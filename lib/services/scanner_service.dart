import 'dart:async';

import '../models/apk_file.dart';
import 'apk_manager_service.dart';
import 'logger_service.dart';

/// One progress tick from the native scanner.
///
/// Progress arrives in **batches** (the native layer throttles events), so the
/// list UI can be updated a handful of times per second instead of once per
/// file — that is what keeps the scan smooth on large storages.
class ScanProgress {
  /// Total number of APK files found so far.
  final int filesFound;

  /// Directory currently being walked (may be empty while parsing).
  final String currentDirectory;

  /// Newly parsed APKs contained in this batch (may be empty).
  final List<ApkFile> apks;

  /// True while the scanner is walking the tree, false while parsing.
  final bool isDiscovering;

  /// True for the final event of a scan.
  final bool isComplete;

  const ScanProgress({
    required this.filesFound,
    required this.currentDirectory,
    this.apks = const [],
    this.isDiscovering = false,
    this.isComplete = false,
  });
}

class ScanResult {
  final List<ApkFile> allFiles;
  final int totalScanned;
  final Duration duration;

  /// True when the user stopped the scan before it finished.
  final bool cancelled;

  const ScanResult({
    required this.allFiles,
    required this.totalScanned,
    required this.duration,
    this.cancelled = false,
  });
}

class ScannerService {
  final _logger = LoggerService.instance;

  bool _isScanning = false;

  bool get isScanning => _isScanning;

  /// Runs a full storage scan and reports batched progress to [onProgress].
  ///
  /// A second call while a scan is running is ignored (returns the previous
  /// state instead of starting competing scans). [isCancelled] is polled as
  /// events arrive; when it flips to true the native scan is asked to stop and
  /// the files found so far are returned.
  Future<ScanResult> scanAllStorage({
    void Function(ScanProgress progress)? onProgress,
    bool Function()? isCancelled,
  }) async {
    if (_isScanning) {
      _logger.warning('Scan', 'Scan already running — ignoring duplicate start');
      return const ScanResult(
        allFiles: [],
        totalScanned: 0,
        duration: Duration.zero,
      );
    }

    _isScanning = true;
    final stopwatch = Stopwatch()..start();
    _logger.info('Scan', 'Starting full storage scan');

    StreamSubscription<Map<String, dynamic>>? subscription;
    var cancelRequested = false;
    var wasCancelled = false;

    void requestCancel() {
      if (cancelRequested) return;
      cancelRequested = true;
      wasCancelled = true;
      unawaited(ApkManagerService.cancelScan());
      _logger.info('Scan', 'Cancellation requested');
    }

    try {
      subscription = ApkManagerService.scanProgressStream.listen(
        (event) {
          if (isCancelled?.call() ?? false) requestCancel();

          final type = event['type'] as String? ?? 'progress';
          final files = (event['filesFound'] as num?)?.toInt() ?? 0;
          final dir = event['currentDir'] as String? ?? '';

          final batch = <ApkFile>[];
          final rawBatch = event['apks'];
          if (rawBatch is List) {
            for (final item in rawBatch) {
              if (item is Map) {
                batch.add(ApkFile.fromMap(Map<String, dynamic>.from(item)));
              }
            }
          }
          // Compatibility with the older one-APK-per-event payload.
          final single = event['apk'];
          if (single is Map) {
            batch.add(ApkFile.fromMap(Map<String, dynamic>.from(single)));
          }

          onProgress?.call(
            ScanProgress(
              filesFound: files,
              currentDirectory: dir,
              apks: batch,
              isDiscovering: batch.isEmpty || type == 'start',
              isComplete: type == 'complete',
            ),
          );

          if (type == 'error') {
            _logger.error('Scan', 'Native scan reported an error: $dir');
          }
        },
        onError: (Object error, StackTrace stackTrace) {
          _logger.warning('Scan', 'Progress stream error: $error');
        },
        cancelOnError: false,
      );

      final files = await ApkManagerService.scanApkFilesWithProgress();
      files.sort(ApkFile.compareByDisplayName);

      stopwatch.stop();
      _logger.info(
        'Scan',
        'Scan ${wasCancelled ? 'stopped' : 'complete'}: '
        '${files.length} APK file(s) found in '
        '${stopwatch.elapsed.inMilliseconds}ms',
      );

      return ScanResult(
        allFiles: files,
        totalScanned: files.length,
        duration: stopwatch.elapsed,
        cancelled: wasCancelled,
      );
    } catch (e) {
      stopwatch.stop();
      _logger.error('Scan', 'Scan failed: $e');
      rethrow;
    } finally {
      await subscription?.cancel();
      _isScanning = false;
    }
  }

  Future<List<ApkFile>> scanDirectory(String dirPath) async {
    _logger.info('Scan', 'Scanning directory: $dirPath');
    try {
      final files = await ApkManagerService.scanDirectoryForApks(dirPath);
      files.sort(ApkFile.compareByDisplayName);
      _logger.info('Scan', 'Found ${files.length} APK(s) in $dirPath');
      return files;
    } catch (e) {
      _logger.error('Scan', 'Failed to scan directory $dirPath: $e');
      rethrow;
    }
  }
}
