import '../models/apk_file.dart';
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
  final List<RenameResult> results;

  const BatchRenameSummary({
    required this.total,
    required this.succeeded,
    required this.failed,
    required this.results,
  });
}

/// Handles renaming APK files with auto-rename and batch support.
class RenamerService {
  final _logger = LoggerService.instance;

  /// Auto-renames a single APK to the format `AppName_VersionName.apk`.
  Future<RenameResult> autoRename(ApkFile apk) async {
    final newName = apk.suggestedRename;
    return rename(apk.path, newName);
  }

  /// Renames an APK file to the given [newName].
  Future<RenameResult> rename(String path, String newName) async {
    _logger.info('Rename', 'Renaming to $newName', filePath: path);
    try {
      final result = await ApkManagerService.renameApk(path, newName);
      final success = result['success'] as bool? ?? false;
      final newPath = result['newPath'] as String?;
      final finalName = result['newName'] as String?;
      
      if (!success) {
        _logger.error('Rename', 'Rename failed: no success flag', filePath: path);
        return RenameResult(
          originalPath: path,
          success: false,
          error: 'Rename operation returned failure',
        );
      }
      
      _logger.info('Rename', 'Renamed successfully to $finalName',
          filePath: newPath);
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
        error: e.toString(),
      );
    }
  }

  /// Batch auto-renames a list of APK files using a bounded worker pool.
  ///
  /// Renames run with at most [concurrency] in flight (default 8), so one slow
  /// file can't block otherwise-idle workers (unlike naive chunking). Files
  /// that are already correctly named are skipped without a native call.
  ///
  /// [onProgress] reports (done, total) after each file. [isCancelled] lets the
  /// caller stop *starting* new renames mid-flight; in-flight native writes are
  /// always allowed to finish so partial files can't be corrupted. A failure on
  /// a single file is recorded in its [RenameResult] and does not abort the
  /// batch.
  ///
  /// Returns a [BatchRenameSummary] with per-file results.
  Future<BatchRenameSummary> autoRenameAll(
    List<ApkFile> apks, {
    int concurrency = 8,
    void Function(int done, int total)? onProgress,
    bool Function()? isCancelled,
  }) async {
    _logger.info('Rename',
        'Starting parallel auto-rename of ${apks.length} file(s) with concurrency $concurrency');

    // Optimization: pre-filter the work queue so files already in the target
    // format never enter a worker. This avoids a per-file name compare on the
    // worker hot path and keeps already-named files out of the async rename
    // pool entirely, so they never occupy a worker slot or trigger a native
    // call. `skipped` are still counted toward progress for accurate UX.
    final pending = <ApkFile>[];
    var skipped = 0;
    for (final apk in apks) {
      if (apk.suggestedRename.toLowerCase() == apk.fileName.toLowerCase()) {
        skipped++;
      } else {
        pending.add(apk);
      }
    }

    final results = <RenameResult>[];
    final queue = List<ApkFile>.from(pending);
    final total = apks.length;
    int done = skipped;

    // Synchronous queue mutation + counters are safe: Dart runs sync code
    // atomically between awaits, so there is no race on `queue`/`done`/`results`.
    Future<void> worker() async {
      while (queue.isNotEmpty) {
        if (isCancelled?.call() ?? false) return;
        final apk = queue.removeAt(0);

        RenameResult result;
        try {
          result = await autoRename(apk);
        } catch (e) {
          _logger.error('Rename', 'Failed to rename: $e', filePath: apk.path);
          result = RenameResult(
            originalPath: apk.path,
            success: false,
            error: e.toString(),
          );
        }
        results.add(result);
        done++;
        onProgress?.call(done, total);
      }
    }

    await Future.wait(List.generate(concurrency, (_) => worker()));

    final succeeded = results.where((r) => r.success).length;
    final failed = results.where((r) => !r.success).length;

    _logger.info('Rename',
        'Batch rename complete: $succeeded succeeded, $failed failed, $skipped already named');

    return BatchRenameSummary(
      total: total,
      succeeded: succeeded,
      failed: failed,
      results: results,
    );
  }
}
