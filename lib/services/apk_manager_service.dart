import 'package:flutter/services.dart';

import '../models/apk_detail.dart';
import '../models/apk_file.dart';
import '../models/directory_entry.dart';
import '../models/installed_app.dart';

class ApkManagerService {
  static const MethodChannel _channel = MethodChannel(
    'com.apkorganizer/apk_manager',
  );

  static const EventChannel _progressChannel = EventChannel(
    'com.apkorganizer/scan_progress',
  );

  static Stream<Map<String, dynamic>>? _cachedScanProgressStream;

  static Stream<Map<String, dynamic>> get scanProgressStream {
    _cachedScanProgressStream ??= _progressChannel.receiveBroadcastStream().map(
      (event) => Map<String, dynamic>.from(event as Map),
    );
    return _cachedScanProgressStream!;
  }

  static Future<String> getJavaVersion() async {
    try {
      return await _channel.invokeMethod('getJavaVersion') ?? 'Unknown';
    } on PlatformException {
      return 'Unknown';
    }
  }

  static Future<List<ApkFile>> scanApkFiles() async {
    try {
      final List<dynamic> result = await _channel.invokeMethod('scanApkFiles');
      final List<ApkFile> files = [];
      for (final item in result) {
        files.add(ApkFile.fromMap(Map<String, dynamic>.from(item as Map)));
      }
      return files;
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to scan APK files: ${e.message}');
    }
  }

  static Future<List<InstalledApp>> getInstalledApps({
    bool includeSystem = true,
  }) async {
    try {
      final List<dynamic> result = await _channel.invokeMethod(
        'getInstalledApps',
        {'includeSystem': includeSystem},
      );
      return result
          .map(
            (item) =>
                InstalledApp.fromMap(Map<String, dynamic>.from(item as Map)),
          )
          .toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to load installed apps: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> uninstallPackage(
    String packageName,
  ) async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'uninstallPackage',
        {'packageName': packageName},
      );
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to uninstall app: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> requestStoragePermission() async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'requestStoragePermission',
      );
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to request permission: ${e.message}');
    }
  }

  static Future<bool> checkStoragePermission() async {
    try {
      final bool result = await _channel.invokeMethod('checkStoragePermission');
      return result;
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to check permission: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> requestInstallPermission() async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'requestInstallPermission',
      );
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to request install permission: ${e.message}');
    }
  }

  static Future<bool> canInstallPackages() async {
    try {
      final bool result = await _channel.invokeMethod('canInstallPackages');
      return result;
    } on PlatformException {
      return false;
    }
  }

  static Future<List<DirectoryEntry>> getDirectories() async {
    try {
      final List<dynamic> result = await _channel.invokeMethod(
        'getDirectories',
      );
      return result
          .map(
            (item) =>
                DirectoryEntry.fromMap(Map<String, dynamic>.from(item as Map)),
          )
          .toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to load directories: ${e.message}');
    }
  }

  static Future<DirectoryEntry> createDirectory(
    String parentPath,
    String name,
  ) async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'createDirectory',
        {'parentPath': parentPath, 'folderName': name},
      );
      return DirectoryEntry.fromMap(Map<String, dynamic>.from(result));
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to create directory: ${e.message}');
    }
  }

  static Future<List<DirectoryEntry>> getSubdirectories(
    String directoryPath,
  ) async {
    try {
      final List<dynamic> result = await _channel.invokeMethod(
        'getSubdirectories',
        {'parentPath': directoryPath},
      );
      return result
          .map(
            (item) =>
                DirectoryEntry.fromMap(Map<String, dynamic>.from(item as Map)),
          )
          .toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to load subdirectories: ${e.message}');
    }
  }

  static Future<List<ApkFile>> scanDirectoryForApks(String dirPath) async {
    try {
      final List<dynamic> result = await _channel.invokeMethod(
        'scanDirectoryForApks',
        {'dirPath': dirPath},
      );
      return result
          .map(
            (item) => ApkFile.fromMap(Map<String, dynamic>.from(item as Map)),
          )
          .toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to scan directory: ${e.message}');
    }
  }

  static Future<List<ApkFile>> scanApkFilesWithProgress() async {
    try {
      final List<dynamic> result = await _channel.invokeMethod(
        'scanApkFilesWithProgress',
      );
      return result
          .map(
            (item) => ApkFile.fromMap(Map<String, dynamic>.from(item as Map)),
          )
          .toList();
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to scan APK files: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> moveApk(
    String sourcePath,
    String destDir,
  ) async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'moveApk',
        {'sourcePath': sourcePath, 'destDir': destDir},
      );
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to move APK: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> renameApk(
    String apkPath,
    String newName,
  ) async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'renameApk',
        {'path': apkPath, 'newName': newName},
      );
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to rename APK: ${e.message}');
    }
  }

  static Future<ApkDetailInfo> getApkDetail(String path) async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'getApkDetail',
        {'path': path},
      );
      return ApkDetailInfo.fromMap(Map<String, dynamic>.from(result));
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to get APK details: ${e.message}');
    }
  }

  static Future<void> deleteApk(String apkPath) async {
    try {
      await _channel.invokeMethod('deleteApk', {'path': apkPath});
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to delete APK: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> installApk(String apkPath) async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'installApk',
        {'path': apkPath},
      );
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to install APK: ${e.message}');
    }
  }

  static Future<Map<String, dynamic>> backupInstalledApp(
    String packageName,
    String destDir,
  ) async {
    try {
      final Map<dynamic, dynamic> result = await _channel.invokeMethod(
        'backupInstalledApp',
        {'packageName': packageName, 'destDir': destDir},
      );
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw ApkManagerException('Failed to backup app: ${e.message}');
    }
  }

  static final List<Function(String)> _onPackageRemovedListeners = [];

  static void addOnPackageRemovedListener(Function(String) callback) {
    _onPackageRemovedListeners.add(callback);
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  static void removeOnPackageRemovedListener(Function(String) callback) {
    _onPackageRemovedListeners.remove(callback);
    if (_onPackageRemovedListeners.isEmpty) {
      _channel.setMethodCallHandler(null);
    }
  }

  static Future<void> _handleMethodCall(MethodCall call) async {
    if (call.method == 'onPackageRemoved') {
      final packageName = call.arguments?['packageName'] as String?;
      if (packageName != null) {
        for (final callback in List<Function(String)>.from(_onPackageRemovedListeners)) {
          callback(packageName);
        }
      }
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
