import 'dart:async';

import '../models/apk_file.dart';
import 'apk_manager_service.dart';
import 'logger_service.dart';

class ScanProgress {
  final int filesFound;
  final String currentDirectory;
  final bool isComplete;

  const ScanProgress({
    required this.filesFound,
    required this.currentDirectory,
    this.isComplete = false,
  });
}

class ScanResult {
  final List<ApkFile> allFiles;
  final int totalScanned;
  final Duration duration;

  const ScanResult({
    required this.allFiles,
    required this.totalScanned,
    required this.duration,
  });
}

class ScannerService {
  final _logger = LoggerService.instance;

  Future<ScanResult> scanAllStorage() async {
    final stopwatch = Stopwatch()..start();
    _logger.info('Scan', 'Starting full storage scan');

    try {
      final files = await ApkManagerService.scanApkFilesWithProgress();
      files.sort(ApkFile.compareByDisplayName);

      stopwatch.stop();
      _logger.info(
        'Scan',
        'Scan complete: ${files.length} APK files found in ${stopwatch.elapsed.inSeconds}s',
      );

      return ScanResult(
        allFiles: files,
        totalScanned: files.length,
        duration: stopwatch.elapsed,
      );
    } catch (e) {
      stopwatch.stop();
      _logger.error('Scan', 'Scan failed: $e');
      rethrow;
    }
  }

  Future<List<ApkFile>> scanDirectory(String dirPath) async {
    _logger.info('Scan', 'Scanning directory: $dirPath');
    try {
      final files = await ApkManagerService.scanDirectoryForApks(dirPath);
      _logger.info('Scan', 'Found ${files.length} APK(s) in $dirPath');
      return files;
    } catch (e) {
      _logger.error('Scan', 'Failed to scan directory $dirPath: $e');
      rethrow;
    }
  }
}
