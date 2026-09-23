import '../models/apk_file.dart';
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
    final sorted = List<ApkFile>.from(files)
      ..sort((a, b) {
        final modified = b.lastModified.compareTo(a.lastModified);
        if (modified != 0) return modified;
        final size = b.size.compareTo(a.size);
        if (size != 0) return size;
        return a.path.compareTo(b.path);
      });
    return sorted.first;
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
/// fails, the fallback is display name + version name. Different versions of the
/// same package are never treated as duplicates.
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
      final key = file.packageName != 'unknown'
          ? file.packageName.toLowerCase()
          : file.displayName.toLowerCase();
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

    final duplicates = analysis.duplicates.values
        .where((group) => group.length > 1)
        .map(
          (group) => DuplicateGroup(
            appName: group.first.displayName,
            versionName: group.first.versionName,
            files: group,
          ),
        )
        .toList();

    _logger.info(
      'Duplicates',
      'Found ${duplicates.length} duplicate group(s) (same package + same version)',
    );

    return duplicates;
  }

  Future<DuplicateRemovalSummary> removeDuplicates(
    List<ApkFile> files, {
    int concurrency = 8,
    void Function(int done, int total)? onProgress,
    bool Function()? isCancelled,
  }) async {
    // Fast path: if every file has a unique duplicate identity, no work is
    // possible. Skipping `findDuplicates` (which builds grouping maps and
    // logs multi-version apps) saves a full-list scan in the common case of
    // zero duplicates.
    final identitySeen = <String>{};
    final hasDuplicates =
        files.any((f) => !identitySeen.add(f.duplicateIdentity));
    if (!hasDuplicates) {
      _logger.info('Duplicates',
          'No duplicate identities among ${files.length} file(s); skipping removal');
      return DuplicateRemovalSummary(
        duplicateGroups: 0,
        filesDeleted: 0,
        filesKept: 0,
        deletedPaths: const [],
        errors: const [],
      );
    }

    final groups = findDuplicates(files);
    int filesKept = groups.length;
    final deletedPaths = <String>[];
    final errors = <String>[];

    // Flatten every file we intend to delete across all groups. The keep file
    // per group is decided up-front (deterministically) so deleting files in
    // any order is safe.
    final toDelete = groups.expand((g) => g.filesToDelete).toList();
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

    // Bounded worker pool: up to [concurrency] deletes run in flight so one
    // slow storage write can't block idle workers. Files are deleted from a
    // shared queue; progress/deleted-path updates are sync between awaits, so
    // no race exists on these counters (Dart runs sync code atomically).
    int done = 0;
    final queue = List<ApkFile>.from(toDelete);
    Future<void> worker() async {
      while (queue.isNotEmpty) {
        if (isCancelled?.call() ?? false) return;
        final file = queue.removeAt(0);
        try {
          await ApkManagerService.deleteApk(file.path);
          deletedPaths.add(file.path);
          done++;
          onProgress?.call(done, totalDeletes);
          _logger.info(
            'Duplicates',
            'Deleted duplicate: ${file.fileName}',
            filePath: file.path,
          );
        } catch (e) {
          errors.add('Failed to delete ${file.path}: $e');
          _logger.error(
            'Duplicates',
            'Failed to delete duplicate: $e',
            filePath: file.path,
          );
        }
      }
    }

    await Future.wait(List.generate(concurrency, (_) => worker()));

    final summary = DuplicateRemovalSummary(
      duplicateGroups: groups.length,
      filesDeleted: deletedPaths.length,
      filesKept: filesKept,
      deletedPaths: deletedPaths,
      errors: errors,
    );

    _logger.info(
      'Duplicates',
      'Removal complete: ${summary.duplicateGroups} groups, ${summary.filesDeleted} deleted, ${summary.filesKept} kept',
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
