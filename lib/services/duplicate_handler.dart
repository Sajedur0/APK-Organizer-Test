import '../models/apk_file.dart';
import '../utils/parallel_work_queue.dart';
import 'apk_manager_service.dart';
import 'logger_service.dart';

class DuplicateGroup {
  final String appName;
  final String versionName;
  final List<ApkFile> files;

  const DuplicateGroup({
    required this.appName,
    required this.versionName,
    required this.files,
  });

  /// The file to keep: newest first, then largest, then stable path order.
  ///
  /// The final tiebreaker uses the full path in a deterministic order so the
  /// same file is always chosen across separate `findDuplicates` /
  /// `removeDuplicates` calls (avoiding deleting the wrong duplicate).
  ApkFile get fileToKeep {
    ApkFile? best;
    for (final file in files) {
      if (best == null) {
        best = file;
        continue;
      }
      final modified = file.lastModified.compareTo(best.lastModified);
      if (modified > 0) {
        best = file;
        continue;
      }
      if (modified == 0) {
        final size = file.size.compareTo(best.size);
        if (size > 0) {
          best = file;
          continue;
        }
        if (size == 0 && file.path.compareTo(best.path) < 0) {
          best = file;
        }
      }
    }
    return best ?? files.first;
  }

  /// Files to delete: all except the selected keep file.
  List<ApkFile> get filesToDelete {
    final keep = fileToKeep;
    return files.where((f) => f.path != keep.path).toList();
  }
}

/// Summary of a duplicate removal operation.
class DuplicateRemovalSummary {
  final int duplicateGroups;
  final int filesDeleted;
  final int filesKept;
  final List<String> deletedPaths;
  final List<String> errors;

  const DuplicateRemovalSummary({
    required this.duplicateGroups,
    required this.filesDeleted,
    required this.filesKept,
    required this.deletedPaths,
    required this.errors,
  });
}

/// Detects and removes duplicate APK files.
///
/// Package name + version code is the safest duplicate identity. If APK parsing
/// fails, the fallback is display name + version name + size. Different
/// versions of the same package are never treated as duplicates.
class DuplicateHandler {
  final _logger = LoggerService.instance;

  /// Single pass over [files] building both groupings needed for duplicate
  /// analysis:
  ///  - [duplicates]: keyed by [ApkFile.duplicateIdentity] (same package + same
  ///    version) — these are true duplicates.
  ///  - [multiVersion]: keyed by package name / display name — used to log apps
  ///    that appear in several *different* versions (never deleted).
  ///
  /// Returns both maps so callers avoid a second full-list iteration.
  ({Map<String, List<ApkFile>> duplicates, Map<String, List<ApkFile>> multiVersion})
      analyzeApks(List<ApkFile> files) {
    final byIdentity = <String, List<ApkFile>>{};
    final byApp = <String, List<ApkFile>>{};

    for (final file in files) {
      byIdentity.putIfAbsent(file.duplicateIdentity, () => []).add(file);
      final key = file.isUnparsed
          ? file.sortName
          : file.packageName.trim().toLowerCase();
      byApp.putIfAbsent(key, () => []).add(file);
    }

    return (duplicates: byIdentity, multiVersion: byApp);
  }

  List<DuplicateGroup> findDuplicates(List<ApkFile> files) {
    _logger.info(
      'Duplicates',
      'Scanning ${files.length} file(s) for duplicates',
    );

    final analysis = analyzeApks(files);
    _logAppsWithMultipleVersions(analysis.multiVersion);

    final duplicates = <DuplicateGroup>[];
    for (final group in analysis.duplicates.values) {
      if (group.length > 1) {
        duplicates.add(
          DuplicateGroup(
            appName: group.first.displayName,
            versionName: group.first.versionName,
            files: group,
          ),
        );
      }
    }

    _logger.info(
      'Duplicates',
      'Found ${duplicates.length} duplicate group(s) (same package + same version)',
    );

    return duplicates;
  }

  Future<DuplicateRemovalSummary> removeDuplicates(
    List<ApkFile> files, {
    int concurrency = 4,
    void Function(int done, int total)? onProgress,
    bool Function()? isCancelled,
  }) async {
    // Fast path: if every file has a unique duplicate identity, no work is
    // possible. Skipping `findDuplicates` (which builds grouping maps and
    // logs multi-version apps) saves a full-list scan in the common case of
    // zero duplicates.
    final identitySeen = <String>{};
    var hasDuplicates = false;
    for (final file in files) {
      if (!identitySeen.add(file.duplicateIdentity)) {
        hasDuplicates = true;
        break;
      }
    }
    if (!hasDuplicates) {
      _logger.info('Duplicates',
          'No duplicate identities among ${files.length} file(s); skipping removal');
      return const DuplicateRemovalSummary(
        duplicateGroups: 0,
        filesDeleted: 0,
        filesKept: 0,
        deletedPaths: [],
        errors: [],
      );
    }

    final groups = findDuplicates(files);
    final filesKept = groups.length;
    final deletedPaths = <String>[];
    final errors = <String>[];

    // Flatten every file we intend to delete across all groups. The keep file
    // per group is decided up-front (deterministically) so deleting files in
    // any order is safe.
    final toDelete = <ApkFile>[];
    for (final group in groups) {
      toDelete.addAll(group.filesToDelete);
    }
    final totalDeletes = toDelete.length;

    if (totalDeletes == 0) {
      return DuplicateRemovalSummary(
        duplicateGroups: groups.length,
        filesDeleted: 0,
        filesKept: filesKept,
        deletedPaths: const [],
        errors: const [],
      );
    }

    // Bounded worker pool over a shared cursor: no `removeAt(0)` shifting (that
    // was O(n²) on large duplicate sets) and no idle workers while one slow
    // delete is in flight.
    var done = 0;
    await runParallel(
      total: totalDeletes,
      concurrency: concurrency,
      isCancelled: isCancelled,
      task: (index) async {
        final file = toDelete[index];
        try {
          await ApkManagerService.deleteApk(file.path);
          deletedPaths.add(file.path);
        } catch (e) {
          errors.add('Failed to delete ${file.fileName}: $e');
          _logger.error(
            'Duplicates',
            'Failed to delete duplicate: $e',
            filePath: file.path,
          );
        }
        done++;
        onProgress?.call(done, totalDeletes);
      },
    );

    final summary = DuplicateRemovalSummary(
      duplicateGroups: groups.length,
      filesDeleted: deletedPaths.length,
      filesKept: filesKept,
      deletedPaths: deletedPaths,
      errors: errors,
    );

    _logger.info(
      'Duplicates',
      'Removal complete: ${summary.duplicateGroups} groups, '
      '${summary.filesDeleted} deleted, ${summary.filesKept} kept, '
      '${summary.errors.length} error(s)',
    );

    return summary;
  }

  void _logAppsWithMultipleVersions(Map<String, List<ApkFile>> appGroups) {
    for (final entry in appGroups.entries) {
      final versions = entry.value.map((f) => f.versionName).toSet();
      if (entry.value.length > 1 && versions.length > 1) {
        _logger.info(
          'Duplicates',
          'App "${entry.key}" has ${entry.value.length} different versions: ${versions.join(", ")} - these will NOT be deleted',
        );
      }
    }
  }
}

