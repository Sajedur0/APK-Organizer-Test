import '../models/apk_file.dart';
import '../utils/parallel_work_queue.dart';
import 'apk_manager_service.dart';
import 'logger_service.dart';

/// Result of a single rename operation.
class RenameResult {
  final String originalPath;
  final String? newPath;
  final String? newName;
  final bool success;
  final String? error;

  const RenameResult({
    required this.originalPath,
    this.newPath,
    this.newName,
    required this.success,
    this.error,
  });
}

/// Summary of a batch rename operation.
class BatchRenameSummary {
  final int total;
  final int succeeded;
  final int failed;

  /// Files that already matched the target naming scheme and were left alone.
  final int skipped;

  final List<RenameResult> results;

  const BatchRenameSummary({
    required this.total,
    required this.succeeded,
    required this.failed,
    required this.results,
    this.skipped = 0,
  });

  bool get hasUndoableChanges =>
      results.any((r) => r.success && r.newPath != null && r.newName != null);
}

/// Handles renaming APK files with auto-rename and batch support.
class RenamerService {
  final _logger = LoggerService.instance;

  /// Auto-renames a single APK to the format `AppName_VersionName.apk`.
  Future<RenameResult> autoRename(ApkFile apk) async {
    if (!apk.needsRename) {
      return RenameResult(
        originalPath: apk.path,
        newPath: apk.path,
        newName: apk.fileName,
        success: false,
        error: 'Already named correctly',
      );
    }
    return rename(apk.path, apk.suggestedRename);
  }

  /// Renames an APK file to the given [newName].
  Future<RenameResult> rename(String path, String newName) async {
    try {
      final result = await ApkManagerService.renameApk(path, newName);
      final success = result['success'] as bool? ?? false;
      final newPath = result['newPath'] as String?;
      final finalName = result['newName'] as String?;

      if (!success || newPath == null || finalName == null) {
        _logger.warning('Rename', 'Rename returned no path', filePath: path);
        return RenameResult(
          originalPath: path,
          success: false,
          error: 'Rename operation did not complete',
        );
      }

      _logger.info('Rename', 'Renamed to $finalName', filePath: newPath);
      return RenameResult(
        originalPath: path,
        newPath: newPath,
        newName: finalName,
        success: true,
      );
    } catch (e) {
      _logger.error('Rename', 'Failed to rename: $e', filePath: path);
      return RenameResult(
        originalPath: path,
        success: false,
        error: e is ApkManagerException ? e.message : e.toString(),
      );
    }
  }

  /// Batch auto-renames a list of APK files using a bounded worker pool.
  ///
  /// Renames run with at most [concurrency] in flight, so one slow file can't
  /// block otherwise-idle workers. Files that already follow the target naming
  /// scheme (including ones the native layer suffixed with `_1`) are skipped
  /// without a native call, which makes repeated "Smart Organize" runs cheap
  /// and idempotent.
  ///
  /// [onProgress] reports (done, total) after each file, counting skipped files
  /// as done so the progress dialog matches what the user sees. [isCancelled]
  /// stops *starting* new renames mid-flight; in-flight native writes are
  /// always allowed to finish so partial files can't be corrupted. A failure on
  /// a single file is recorded in its [RenameResult] and does not abort the
  /// batch.
  Future<BatchRenameSummary> autoRenameAll(
    List<ApkFile> apks, {
    int concurrency = 4,
    void Function(int done, int total)? onProgress,
    bool Function()? isCancelled,
  }) async {
    final pending = <ApkFile>[];
    var skipped = 0;
    for (final apk in apks) {
      if (apk.needsRename) {
        pending.add(apk);
      } else {
        skipped++;
      }
    }

    final total = apks.length;
    var done = skipped;
    final results = <RenameResult>[];

    if (pending.isNotEmpty) {
      _logger.info(
        'Rename',
        'Auto-renaming ${pending.length} of $total file(s) '
        '($skipped already named) with concurrency $concurrency',
      );
    }

    await runParallel(
      total: pending.length,
      concurrency: concurrency,
      isCancelled: isCancelled,
      task: (index) async {
        final apk = pending[index];
        RenameResult result;
        try {
          result = await rename(apk.path, apk.suggestedRename);
        } catch (e) {
          _logger.error('Rename', 'Failed to rename: $e', filePath: apk.path);
          result = RenameResult(
            originalPath: apk.path,
            success: false,
            error: e is ApkManagerException ? e.message : e.toString(),
          );
        }
        results.add(result);
        done++;
        onProgress?.call(done > total ? total : done, total);
      },
    );

    // Progress must not appear stuck for the files that were skipped.
    onProgress?.call(done > total ? total : done, total);

    final succeeded = results.where((r) => r.success).length;
    final failed = results.length - succeeded;

    _logger.info(
      'Rename',
      'Batch rename complete: $succeeded succeeded, $failed failed, '
      '$skipped already named',
    );

    return BatchRenameSummary(
      total: total,
      succeeded: succeeded,
      failed: failed,
      skipped: skipped,
      results: results,
    );
  }
}
