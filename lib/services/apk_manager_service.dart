import 'package:flutter/services.dart';

import '../models/apk_detail.dart';
import '../models/apk_file.dart';
import '../models/directory_entry.dart';
import '../models/installed_app.dart';

/// Typed wrapper around the native `ApkManagerPlugin` method/event channels.
///
/// Every method is defensive: the native side may return partial or unexpected
/// shapes (older builds, OEM quirks), so values are validated before use
/// instead of being force-cast.
class ApkManagerService {
  ApkManagerService._();

  static const MethodChannel _channel = MethodChannel(
    'com.apkorganizer/apk_manager',
  );

  static const EventChannel _progressChannel = EventChannel(
    'com.apkorganizer/scan_progress',
  );

  /// Channels used by the native side to push events (e.g. package removed).
  static const String _onPackageRemoved = 'onPackageRemoved';

  static Stream<Map<String, dynamic>>? _cachedScanProgressStream;
  static bool _methodCallHandlerInstalled = false;

  static Stream<Map<String, dynamic>> get scanProgressStream {
    _cachedScanProgressStream ??= _progressChannel
        .receiveBroadcastStream()
        .where((event) => event is Map)
        .map((event) => Map<String, dynamic>.from(event as Map));
    return _cachedScanProgressStream!;
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  static List<Map<String, dynamic>> _asMapList(dynamic raw) {
    if (raw is! List) return const [];
    final List<Map<String, dynamic>> out = [];
    for (final item in raw) {
      if (item is Map) out.add(Map<String, dynamic>.from(item));
    }
    return out;
  }

  static Map<String, dynamic> _asMap(dynamic raw) {
    if (raw is Map) return Map<String, dynamic>.from(raw);
    return <String, dynamic>{};
  }

  // ---------------------------------------------------------------------------
  // Environment / meta
  // ---------------------------------------------------------------------------

  static Future<String> getJavaVersion() async {
    try {
      final version = await _channel.invokeMethod<String>('getJavaVersion');
      if (version == null || version.isEmpty) return 'Unknown';
      return version;
    } on PlatformException {
      return 'Unknown';
    } on MissingPluginException {
      return 'Unknown';
    }
  }

  // ---------------------------------------------------------------------------
  // Scanning
  // ---------------------------------------------------------------------------

  static Future<List<ApkFile>> scanApkFiles() async {
    try {
      final result = await _channel.invokeMethod<dynamic>('scanApkFiles');
      return _asMapList(result).map(ApkFile.fromMap).toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to scan APK files: ${e.message}');
    }
  }

  /// Starts a full-storage scan and streams batched progress on
  /// [scanProgressStream] while it runs.
  ///
  /// The returned list is complete unless the scan was cancelled through
  /// [cancelScan], in which case it contains everything found so far.
  static Future<List<ApkFile>> scanApkFilesWithProgress() async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'scanApkFilesWithProgress',
      );
      return _asMapList(result).map(ApkFile.fromMap).toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to scan APK files: ${e.message}');
    }
  }

  /// Asks the native scanner to stop. The running scan resolves with the APKs
  /// it has already parsed instead of being discarded.
  static Future<void> cancelScan() async {
    try {
      await _channel.invokeMethod<bool>('cancelScan');
    } on PlatformException {
      // Nothing to do — the scan will simply finish on its own.
    } on MissingPluginException {
      // Older native build without cancel support.
    }
  }

  static Future<List<ApkFile>> scanDirectoryForApks(String dirPath) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'scanDirectoryForApks',
        {'dirPath': dirPath},
      );
      return _asMapList(result).map(ApkFile.fromMap).toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to scan directory: ${e.message}');
    }
  }

  // ---------------------------------------------------------------------------
  // Installed apps
  // ---------------------------------------------------------------------------

  static Future<List<InstalledApp>> getInstalledApps({
    bool includeSystem = false,
  }) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'getInstalledApps',
        {'includeSystem': includeSystem},
      );
      return _asMapList(result).map(InstalledApp.fromMap).toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to load installed apps: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> uninstallPackage(
    String packageName,
  ) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'uninstallPackage',
        {'packageName': packageName},
      );
      return _asMap(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to uninstall app: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> backupInstalledApp(
    String packageName,
    String destDir,
  ) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'backupInstalledApp',
        {'packageName': packageName, 'destDir': destDir},
      );
      return _asMap(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to backup app: ${e.message}');
    }
  }

  // ---------------------------------------------------------------------------
  // Permissions
  // ---------------------------------------------------------------------------

  static Future<Map<String, dynamic>> requestStoragePermission() async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'requestStoragePermission',
      );
      return _asMap(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to request permission: ${e.message}');
    }
  }

  static Future<bool> checkStoragePermission() async {
    try {
      final result = await _channel.invokeMethod<bool>(
        'checkStoragePermission',
      );
      return result ?? false;
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to check permission: ${e.message}');
    } on MissingPluginException {
      return false;
    }
  }

  static Future<Map<String, dynamic>> requestInstallPermission() async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'requestInstallPermission',
      );
      return _asMap(result);
    } on PlatformException catch (e) {
      throw ApkManagerException(
        'Failed to request install permission: ${e.message}',
      );
    }
  }

  static Future<bool> canInstallPackages() async {
    try {
      final result = await _channel.invokeMethod<bool>('canInstallPackages');
      return result ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  // ---------------------------------------------------------------------------
  // Directories
  // ---------------------------------------------------------------------------

  static Future<List<DirectoryEntry>> getDirectories() async {
    try {
      final result = await _channel.invokeMethod<dynamic>('getDirectories');
      return _asMapList(result).map(DirectoryEntry.fromMap).toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to load directories: ${e.message}');
    }
  }

  static Future<DirectoryEntry> createDirectory(
    String parentPath,
    String name,
  ) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'createDirectory',
        {'parentPath': parentPath, 'folderName': name},
      );
      return DirectoryEntry.fromMap(_asMap(result));
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to create directory: ${e.message}');
    }
  }

  static Future<List<DirectoryEntry>> getSubdirectories(
    String directoryPath,
  ) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'getSubdirectories',
        {'parentPath': directoryPath},
      );
      return _asMapList(result).map(DirectoryEntry.fromMap).toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to load subdirectories: ${e.message}');
    }
  }

  // ---------------------------------------------------------------------------
  // File operations
  // ---------------------------------------------------------------------------

  static Future<Map<String, dynamic>> moveApk(
    String sourcePath,
    String destDir,
  ) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'moveApk',
        {'sourcePath': sourcePath, 'destDir': destDir},
      );
      return _asMap(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to move APK: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> renameApk(
    String apkPath,
    String newName,
  ) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'renameApk',
        {'path': apkPath, 'newName': newName},
      );
      return _asMap(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to rename APK: ${e.message}');
    }
  }

  static Future<ApkDetailInfo> getApkDetail(String path) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'getApkDetail',
        {'path': path},
      );
      return ApkDetailInfo.fromMap(_asMap(result));
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to get APK details: ${e.message}');
    }
  }

  /// Deletes an APK.
  ///
  /// Throws [ApkManagerException] when the file could not actually be removed,
  /// so callers never drop an entry from the list while it still exists on
  /// disk.
  static Future<void> deleteApk(String apkPath) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'deleteApk',
        {'path': apkPath},
      );
      final map = _asMap(result);
      if (map['success'] == false) {
        throw ApkManagerException(
          'Could not delete ${apkPath.split('/').last}',
        );
      }
    } on PlatformException catch (e) {
      throw ApkManagerException(
        e.message ?? 'Failed to delete APK',
      );
    }
  }

  static Future<Map<String, dynamic>> installApk(String apkPath) async {
    try {
      final result = await _channel.invokeMethod<dynamic>(
        'installApk',
        {'path': apkPath},
      );
      return _asMap(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to install APK: ${e.message}');
    }
  }

  // ---------------------------------------------------------------------------
  // Native -> Dart events
  // ---------------------------------------------------------------------------

  static final List<void Function(String)> _onPackageRemovedListeners = [];

  static void addOnPackageRemovedListener(void Function(String) callback) {
    if (!_onPackageRemovedListeners.contains(callback)) {
      _onPackageRemovedListeners.add(callback);
    }
    _installMethodCallHandler();
  }

  static void removeOnPackageRemovedListener(void Function(String) callback) {
    _onPackageRemovedListeners.remove(callback);
    if (_onPackageRemovedListeners.isEmpty && _methodCallHandlerInstalled) {
      _channel.setMethodCallHandler(null);
      _methodCallHandlerInstalled = false;
    }
  }

  static void _installMethodCallHandler() {
    if (_methodCallHandlerInstalled) return;
    _methodCallHandlerInstalled = true;
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  static Future<void> _handleMethodCall(MethodCall call) async {
    if (call.method != _onPackageRemoved) return;
    final args = call.arguments;
    final packageName = args is Map ? args['packageName'] as String? : null;
    if (packageName == null || packageName.isEmpty) return;
    for (final callback in List<void Function(String)>.from(
      _onPackageRemovedListeners,
    )) {
      callback(packageName);
    }
  }
}

class ApkManagerException implements Exception {
  final String message;
  const ApkManagerException(this.message);

  @override
  String toString() => 'ApkManagerException: $message';
}

class MoveConflictException implements Exception {
  final String message;
  const MoveConflictException(this.message);

  @override
  String toString() => 'MoveConflictException: $message';
}
