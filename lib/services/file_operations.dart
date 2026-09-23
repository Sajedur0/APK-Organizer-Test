import 'dart:io';

import '../utils/format_util.dart';
import '../utils/parallel_work_queue.dart';
import 'apk_manager_service.dart';
import 'logger_service.dart';

/// Result of a single move operation.
class MoveResult {
  final String sourcePath;
  final String? destPath;
  final bool success;
  final bool conflictResolved;

  /// True when the file was already inside the destination folder.
  final bool skipped;
  final String? error;

  const MoveResult({
    required this.sourcePath,
    this.destPath,
    required this.success,
    this.conflictResolved = false,
    this.skipped = false,
    this.error,
  });
}

/// Summary of a batch move operation.
class BatchMoveSummary {
  final int total;
  final int succeeded;
  final int failed;
  final int skipped;
  final int conflictsResolved;
  final List<MoveResult> results;

  const BatchMoveSummary({
    required this.total,
    required this.succeeded,
    required this.failed,
    required this.conflictsResolved,
    required this.results,
    this.skipped = 0,
  });

  bool get hasUndoableChanges =>
      results.any((r) => r.success && !r.skipped && r.destPath != null);

  /// Human readable one-liner used by the snackbar.
  String describe() {
    final buffer = StringBuffer('$succeeded file(s) moved');
    if (skipped > 0) buffer.write(', $skipped already there');
    if (failed > 0) buffer.write(', $failed failed');
    if (conflictsResolved > 0) {
      buffer.write(', $conflictsResolved renamed to avoid conflicts');
    }
    return buffer.toString();
  }
}

/// Handles moving APK files with conflict resolution.
///
/// When a file with the same name exists at the destination, the native layer
/// creates a safe suffixed file name instead of replacing existing data.
class FileOperations {
  final _logger = LoggerService.instance;

  /// Moves an APK file to the target directory.
  ///
  /// Returns [MoveResult.skipped] when the file already lives in [destDir];
  /// a same-folder "move" never touches the disk.
  Future<MoveResult> moveApk(
    String sourcePath,
    String destDir,
  ) async {
    try {
      final sourceFile = File(sourcePath);
      if (!await sourceFile.exists()) {
        _logger.error('Move', 'Source file not found', filePath: sourcePath);
        return MoveResult(
          sourcePath: sourcePath,
          success: false,
          error: 'Source file not found',
        );
      }

      final normalizedDest = _normalize(destDir);
      final normalizedParent = _normalize(FormatUtil.parentPath(sourcePath));
      if (normalizedDest == normalizedParent) {
        return MoveResult(
          sourcePath: sourcePath,
          destPath: sourcePath,
          success: true,
          skipped: true,
        );
      }

      final destDirObj = Directory(destDir);
      if (!await destDirObj.exists()) {
        try {
          await destDirObj.create(recursive: true);
        } catch (e) {
          return MoveResult(
            sourcePath: sourcePath,
            success: false,
            error: 'Could not create destination folder',
          );
        }
      }

      _logger.info('Move', 'Moving to $destDir', filePath: sourcePath);
      final result = await ApkManagerService.moveApk(sourcePath, destDir);
      final success = result['success'] as bool? ?? false;
      final newPath = result['destPath'] as String?;
      final conflictResolved = result['conflictResolved'] as bool? ?? false;
      final skipped = result['skipped'] as bool? ?? false;

      if (!success || newPath == null) {
        return MoveResult(
          sourcePath: sourcePath,
          success: false,
          error: 'Move operation returned failure',
        );
      }

      return MoveResult(
        sourcePath: sourcePath,
        destPath: newPath,
        success: true,
        conflictResolved: conflictResolved,
        skipped: skipped,
      );
    } catch (e) {
      _logger.error('Move', 'Failed to move: $e', filePath: sourcePath);
      return MoveResult(
        sourcePath: sourcePath,
        success: false,
        error: e is ApkManagerException ? e.message : e.toString(),
      );
    }
  }

  /// Batch moves APK files to a target directory with conflict handling.
  ///
  /// Moves run with a bounded worker pool and can report progress and be
  /// cancelled; results are returned in the same order as [sourcePaths] so the
  /// UI can map old paths to new ones reliably.
  Future<BatchMoveSummary> batchMove(
    List<String> sourcePaths,
    String destDir, {
    int concurrency = 3,
    void Function(int done, int total)? onProgress,
    bool Function()? isCancelled,
  }) async {
    final total = sourcePaths.length;
    if (total == 0) {
      return const BatchMoveSummary(
        total: 0,
        succeeded: 0,
        failed: 0,
        conflictsResolved: 0,
        skipped: 0,
        results: [],
      );
    }

    _logger.info(
      'Move',
      'Starting batch move of $total file(s) to $destDir',
    );

    final results = List<MoveResult?>.filled(total, null);
    var done = 0;

    await runParallel(
      total: total,
      concurrency: concurrency,
      isCancelled: isCancelled,
      task: (index) async {
        results[index] = await moveApk(sourcePaths[index], destDir);
        done++;
        onProgress?.call(done, total);
      },
    );

    final completed = <MoveResult>[];
    for (var i = 0; i < total; i++) {
      completed.add(
        results[i] ??
            MoveResult(
              sourcePath: sourcePaths[i],
              success: false,
              error: 'Cancelled',
            ),
      );
    }

    var succeeded = 0;
    var failed = 0;
    var skipped = 0;
    var conflicts = 0;
    for (final result in completed) {
      if (!result.success) {
        failed++;
        continue;
      }
      if (result.skipped) {
        skipped++;
      } else {
        succeeded++;
      }
      if (result.conflictResolved) conflicts++;
    }

    _logger.info(
      'Move',
      'Batch move complete: $succeeded moved, $skipped skipped, '
      '$failed failed, $conflicts conflicts resolved',
    );

    return BatchMoveSummary(
      total: total,
      succeeded: succeeded,
      failed: failed,
      skipped: skipped,
      conflictsResolved: conflicts,
      results: completed,
    );
  }

  /// Compares folder paths without a trailing separator.
  String _normalize(String path) {
    if (path.length > 1 && path.endsWith('/')) {
      return path.substring(0, path.length - 1);
    }
    return path;
  }
}
