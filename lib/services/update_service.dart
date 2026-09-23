import 'dart:async';

import 'package:flutter/material.dart';

import 'app_update_service.dart';

/// Legacy wrapper — delegates to [AppUpdateService] which implements the
/// spec-compliant staleness-aware Flexible → Immediate escalation.
///
/// This shim exists for backward compatibility: existing entry points and
/// tests that import `update_service.dart` / `UpdateService.instance` continue
/// to work, but the actual update logic lives in [AppUpdateService].
///
/// New code should import `app_update_service.dart` and call
/// `AppUpdateService.checkAndPromptUpdate()` directly.
class UpdateService with WidgetsBindingObserver {
  UpdateService._();

  static final UpdateService instance = UpdateService._();

  /// Exposes the same navigator key as [AppUpdateService] so a single
  /// [MaterialApp.navigatorKey] can satisfy both services.
  GlobalKey<NavigatorState> get navigatorKey =>
      AppUpdateService.instance.navigatorKey;

  /// Delegates observer registration to [AppUpdateService].
  void init() => AppUpdateService.instance.init();

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Forward to the canonical service.
    AppUpdateService.instance.didChangeAppLifecycleState(state);
  }

  /// Legacy entry point — now forwards to the staleness-aware
  /// [AppUpdateService.checkAndPromptUpdate].
  ///
  /// Parameters [isResume] / [force] are accepted for source compatibility
  /// but are not needed by the new implementation (resume throttling is
  /// handled internally).
  Future<void> checkForUpdate({bool isResume = false, bool force = false}) =>
      AppUpdateService.checkAndPromptUpdate();

  /// Manual trigger — bypasses throttling in the old service; new service
  /// always re-checks when called explicitly.
  Future<void> checkNow() => AppUpdateService.checkAndPromptUpdate();

  @visibleForTesting
  void resetForTest() => AppUpdateService.instance.resetForTest();

  void dispose() => AppUpdateService.instance.dispose();
}
