import 'dart:io';

import 'apk_manager_service.dart';
import 'logger_service.dart';

/// Result of a single move operation.
class MoveResult {
  final String sourcePath;
  final String? destPath;
  final bool success;
  final bool conflictResolved;
  final String? error;

  const MoveResult({
    required this.sourcePath,
    this.destPath,
    required this.success,
    this.conflictResolved = false,
    this.error,
  });
}

/// Summary of a batch move operation.
class BatchMoveSummary {
  final int total;
  final int succeeded;
  final int failed;
  final int conflictsResolved;
  final List<MoveResult> results;

  const BatchMoveSummary({
    required this.total,
    required this.succeeded,
    required this.failed,
    required this.conflictsResolved,
    required this.results,
  });
}

/// Handles moving APK files with conflict resolution.
///
/// When a file with the same name exists at the destination, the native layer
/// creates a safe suffixed file name instead of replacing existing data.
class FileOperations {
  final _logger = LoggerService.instance;

  /// Moves an APK file to the target directory.
  ///
  /// If a file with the same name already exists at the destination, the
  /// native plugin renames the moved file (adds a suffix) and reports that
  /// via [MoveResult.conflictResolved].
  Future<MoveResult> moveApk(
    String sourcePath,
    String destDir,
  ) async {
    _logger.info('Move', 'Moving to $destDir', filePath: sourcePath);

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

      final destDirObj = Directory(destDir);
      if (!await destDirObj.exists()) {
        await destDirObj.create(recursive: true);
      }

      final result = await ApkManagerService.moveApk(sourcePath, destDir);
      final success = result['success'] as bool? ?? false;
      final newPath = result['destPath'] as String?;
      // The native layer renames to a unique name when a same-named file
      // already exists at the destination, and reports that here.
      final conflictResolved = result['conflictResolved'] as bool? ?? false;

      if (!success) {
        _logger.error('Move', 'Move failed: no success flag', filePath: sourcePath);
        return MoveResult(
          sourcePath: sourcePath,
          success: false,
          error: 'Move operation returned failure',
        );
      }

      _logger.info('Move', 'Moved successfully', filePath: newPath ?? destDir);

      return MoveResult(
        sourcePath: sourcePath,
        destPath: newPath,
        success: true,
        conflictResolved: conflictResolved,
      );
    } catch (e) {
      _logger.error('Move', 'Failed to move: $e', filePath: sourcePath);
      return MoveResult(
        sourcePath: sourcePath,
        success: false,
        error: e.toString(),
      );
    }
  }

  /// Batch moves APK files to a target directory with conflict handling.
  Future<BatchMoveSummary> batchMove(
    List<String> sourcePaths,
    String destDir,
  ) async {
    _logger.info(
      'Move',
      'Starting batch move of ${sourcePaths.length} file(s) to $destDir',
    );

    final results = <MoveResult>[];

    for (final path in sourcePaths) {
      final result = await moveApk(path, destDir);
      results.add(result);
    }

    final succeeded = results.where((r) => r.success).length;
    final failed = results.where((r) => !r.success).length;
    final conflicts = results.where((r) => r.conflictResolved).length;

    _logger.info(
      'Move',
      'Batch move complete: $succeeded moved, $failed failed, $conflicts conflicts resolved',
    );

    return BatchMoveSummary(
      total: sourcePaths.length,
      succeeded: succeeded,
      failed: failed,
      conflictsResolved: conflicts,
      results: results,
    );
  }

}
