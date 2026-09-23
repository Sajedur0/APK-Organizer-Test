import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../services/apk_manager_service.dart';

/// Utility class for handling permission checks and requests consistently.
class PermissionUtils {
  /// Checks if the app has storage permission.
  static Future<bool> hasStoragePermission() async {
    try {
      return await ApkManagerService.checkStoragePermission();
    } on PlatformException catch (_) {
      return false;
    } on ApkManagerException catch (_) {
      return false;
    }
  }

  /// Requests storage permission with rationale.
  static Future<bool> requestStoragePermissionWithRationale() async {
    try {
      final Map<String, dynamic> result =
          await ApkManagerService.requestStoragePermission();
      final status = result['status'] as String?;
      final bool granted = (result['granted'] as bool?) ?? false;
      // A redirected request means the user still has to flip the switch in
      // Settings — treat it as "not granted yet" so callers stop and explain.
      if (!granted && status == 'redirected_to_settings') return false;
      return granted;
    } on PlatformException catch (_) {
      return false;
    } on ApkManagerException catch (_) {
      return false;
    }
  }

  /// Checks if the app is allowed to install packages from unknown sources.
  static Future<bool> canInstallPackages() async {
    try {
      return await ApkManagerService.canInstallPackages();
    } on PlatformException catch (_) {
      return false;
    } on ApkManagerException catch (_) {
      return false;
    }
  }

  /// Requests permission to install packages (redirects to the "install
  /// unknown apps" settings screen if not already granted).
  static Future<bool> requestInstallPermission() async {
    try {
      final Map<String, dynamic> result =
          await ApkManagerService.requestInstallPermission();
      return (result['granted'] as bool?) ?? false;
    } on PlatformException catch (_) {
      return false;
    } on ApkManagerException catch (_) {
      return false;
    }
  }

  /// Shows a permission rationale dialog and requests permission.
  ///
  /// Returns `false` if the context is no longer valid or the user denies.
  static Future<bool> showPermissionRationaleAndRequest(
    BuildContext context, {
    required String title,
    required String message,
    required Future<bool> Function() requestPermission,
  }) async {
    if (!context.mounted) return false;

    final bool showAgain = (await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(title),
            content: Text(message),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Grant Permission'),
              ),
            ],
          ),
        )) ?? false;

    if (!showAgain) return false;

    return await requestPermission();
  }

  /// Ensures storage permission is granted before proceeding with an operation.
  static Future<bool> ensureStoragePermission(BuildContext context) async {
    final hasPerm = await hasStoragePermission();
    if (hasPerm) return true;
    if (!context.mounted) return false;

    return await showPermissionRationaleAndRequest(
      context,
      title: 'Storage Permission Required',
      message:
          'APK Organizer needs access to your device\'s storage to scan, manage, and organize APK files.',
      requestPermission: requestStoragePermissionWithRationale,
    );
  }

  /// Ensures install permission is granted before proceeding with an operation.
  static Future<bool> ensureInstallPermission(BuildContext context) async {
    final canInstall = await canInstallPackages();
    if (canInstall) return true;
    if (!context.mounted) return false;

    return await showPermissionRationaleAndRequest(
      context,
      title: 'Install Permission Required',
      message:
          'APK Organizer needs permission to install APK files on your device.',
      requestPermission: requestInstallPermission,
    );
  }
}