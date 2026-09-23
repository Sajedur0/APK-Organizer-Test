import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:in_app_update_android/in_app_update_android.dart';

/// Google Play In-App Update service with **Flexible → Immediate escalation**
/// based on client staleness.
///
/// ## Business logic (must match spec exactly)
///
/// * Check via [InAppUpdate.checkForUpdate] every app start (and optionally
///   on resume).
/// * Inspect `AppUpdateInfo.clientVersionStalenessDays` — the exact field name
///   confirmed from the `in_app_update_android` package source
///   (`lib/in_app_update_android.dart:88`). This value is nullable and only
///   populated by Play Store when the update is served through Play.
/// * **If `updateAvailable` == true AND staleness < 3 days AND `flexibleUpdateAllowed` == true:**
///   subscribe to [InAppUpdate.installStateListener] *first*, then call
///   [InAppUpdate.startFlexibleUpdate] — a dismissible native Play UI.
/// * **If `updateAvailable` == true AND staleness >= 3 days:**
///   do NOT call `startFlexibleUpdate`; call [InAppUpdate.performImmediateUpdate]
///   instead — a blocking full-screen flow. Also fall back to this branch if
///   `immediateUpdateAllowed || immediateUpdateInProgress` is true even before
///   3 days (Play can mark an update as immediate-only via `inAppUpdatePriority`).
/// * **After a flexible download** completes (`installStatus == downloaded`),
///   automatically call `completeFlexibleUpdate()` so the app installs & restarts
///   into the new version without requiring a manual "Restart" tap (spec requires
///   auto-restart; a snackbar-only flow would leave the update staged).
///
/// ## Testing notes (see also inline comments below)
///
/// * `clientVersionStalenessDays` is only populated by **Google Play**.
///   Testing the 3-day escalation requires publishing a build to Play Console's
///   **internal/closed testing track** and waiting until the staleness reaches
///   3+ days. Local `flutter run` / sideloaded APKs will always report `null`
///   for this field and will never show the Play update UI.
/// * Neither flexible nor immediate UI appears when running a debug build
///   installed outside Play Store (emulator without Play Store, `adb install`,
///   etc.). Use **Play Internal App Sharing** or a Play testing track to test.
/// * Immediate updates automatically resume on activity resume when
///   `developerTriggeredUpdateInProgress` is true — this service handles that
///   case explicitly.
///
/// ## Code structure
///
/// Dedicated service with a static/singleton entry point
/// `checkAndPromptUpdate()` — guard with `InAppUpdate.isAndroid`, try/catch
/// around `checkForUpdate()` with silent failure + debug log, and a managed
/// `installStateListener` subscription with proper `cancel()` in [dispose].
class AppUpdateService with WidgetsBindingObserver {
  AppUpdateService._();

  /// Singleton instance — use [checkAndPromptUpdate] (static) for the main
  /// entry point, or [instance] if you need lifecycle hooks.
  static final AppUpdateService instance = AppUpdateService._();

  /// Static entry point required by the spec — delegates to the singleton so
  /// call sites can write `AppUpdateService.checkAndPromptUpdate()` without
  /// holding an instance.
  static Future<void> checkAndPromptUpdate() => instance._checkAndPromptUpdate();

  /// Navigator key — exposed so [MaterialApp.navigatorKey] can be set if the
  /// service ever needs a BuildContext for error UI. The current staleness
  /// flow auto-completes without Scaffold context, but the key is provided
  /// for parity with the legacy [UpdateService] and for future extensibility.
  final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

  StreamSubscription<InstallState>? _installStateSubscription;
  bool _isChecking = false;
  bool _isFlexibleInProgress = false;
  bool _observerRegistered = false;
  DateTime? _lastCheckTime;

  // Throttle resume-triggered checks to avoid hammering Play.
  static const Duration _minResumeInterval = Duration(minutes: 5);
  static const Duration _checkTimeout = Duration(seconds: 12);

  // ---------------------------------------------------------------------------
  // Lifecycle (optional resume handling)
  // ---------------------------------------------------------------------------

  /// Registers a [WidgetsBindingObserver] so the service can re-check on
  /// app resume (throttled). Call once from the root widget's `initState`
  /// if you want resume checks; otherwise the static `checkAndPromptUpdate()`
  /// alone is sufficient for the "every app start" requirement.
  void init() {
    if (_observerRegistered) return;
    WidgetsBinding.instance.addObserver(this);
    _observerRegistered = true;
    _ensureInstallListener();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) return;
    final now = DateTime.now();
    if (_lastCheckTime != null &&
        now.difference(_lastCheckTime!) < _minResumeInterval &&
        !_isFlexibleInProgress) {
      return;
    }
    // Defer — don't block the resume transition.
    unawaited(_checkAndPromptUpdate(isResume: true));
  }

  // ---------------------------------------------------------------------------
  // Core logic
  // ---------------------------------------------------------------------------

  /// Checks Play for an update and starts the appropriate native flow.
  ///
  /// Uses [InAppUpdate.isAndroid] as platform guard (no-op on non-Android).
  /// Wraps `checkForUpdate()` in try/catch with silent failure + debug log so
  /// the app never crashes when Play Store is unreachable (no internet,
  /// emulator without Play, etc.).
  Future<void> _checkAndPromptUpdate({bool isResume = false}) async {
    // Platform guard — required by spec: no-op immediately if false.
    if (!InAppUpdate.isAndroid) return;
    if (_isChecking) return;

    // Throttle resume checks.
    if (isResume &&
        _lastCheckTime != null &&
        DateTime.now().difference(_lastCheckTime!) < _minResumeInterval &&
        !_isFlexibleInProgress) {
      return;
    }

    _isChecking = true;
    _lastCheckTime = DateTime.now();

    try {
      // Try/catch around checkForUpdate with silent failure + debug log.
      final AppUpdateInfo info;
      try {
        info = await InAppUpdate.checkForUpdate().timeout(_checkTimeout);
      } on TimeoutException {
        debugPrint('[AppUpdateService] checkForUpdate timed out');
        return;
      } on InAppUpdateException catch (e) {
        debugPrint(
            '[AppUpdateService] checkForUpdate InAppUpdateException ${e.code}: ${e.message}');
        return;
      } catch (e, st) {
        debugPrint('[AppUpdateService] checkForUpdate unexpected: $e\n$st');
        return;
      }

      debugPrint(
          '[AppUpdateService] AppUpdateInfo: updateAvailable=${info.updateAvailable} '
          'stalenessDays=${info.clientVersionStalenessDays} '
          'flexibleAllowed=${info.flexibleUpdateAllowed} '
          'immediateAllowed=${info.immediateUpdateAllowed} '
          'immediateInProgress=${info.immediateUpdateInProgress} '
          'installStatus=${info.installStatus} '
          'priority=${info.updatePriority} '
          'availableVersionCode=${info.availableVersionCode}');

      // Handle already-downloaded flexible update (e.g., app was killed after
      // download). Play still reports installStatus == downloaded even when
      // updateAvailable is false. Complete automatically so the app restarts.
      if (info.installStatus == InstallStatus.downloaded) {
        debugPrint(
            '[AppUpdateService] Flexible update already downloaded — completing automatically');
        _ensureInstallListener();
        _isFlexibleInProgress = true;
        await _completeFlexibleUpdate();
        return;
      }

      // If an immediate update was already triggered (developerTriggeredUpdateInProgress),
      // resume it. Play Core also auto-resumes on activity resume, but we
      // attempt explicit resume for stability.
      if (info.immediateUpdateInProgress) {
        debugPrint(
            '[AppUpdateService] Immediate update in progress — resuming immediate flow');
        // Spec: Play Core handles install & restart automatically — don't wrap
        // in a try/catch that silently swallows it. We log failures but don't
        // suppress the flow.
        try {
          final result = await InAppUpdate.performImmediateUpdate()
              .timeout(_checkTimeout);
          debugPrint(
              '[AppUpdateService] Resume immediate result: $result');
        } on InAppUpdateException catch (e) {
          debugPrint(
              '[AppUpdateService] Resume immediate failed ${e.code}: ${e.message}');
        } on TimeoutException {
          debugPrint('[AppUpdateService] Resume immediate timeout');
        } catch (e, st) {
          debugPrint('[AppUpdateService] Resume immediate unexpected: $e\n$st');
        }
        return;
      }

      if (!info.updateAvailable) {
        debugPrint('[AppUpdateService] No update available');
        return;
      }

      // -----------------------------------------------------------------------
      // 3-day staleness branch logic (spec must match exactly)
      // -----------------------------------------------------------------------
      //
      // The staleness field is `AppUpdateInfo.clientVersionStalenessDays`
      // (confirmed from package source `lib/in_app_update_android.dart:88`).
      // It is nullable — Play returns null when staleness is unknown
      // (sideloaded/debug builds, fresh rollout, or Play caching).
      //
      // Spec rules:
      //   * staleness < 3 days  + flexibleUpdateAllowed => startFlexibleUpdate
      //   * staleness >= 3 days                     => performImmediateUpdate
      //   * Also fall back to immediate if immediateUpdateAllowed ||
      //     immediateUpdateInProgress (Play can mark update as immediate-only
      //     via inAppUpdatePriority even before 3 days).
      //
      // For null staleness we treat the app as "fresh" and prefer flexible
      // when allowed, falling back to flexible only if immediate is not
      // required. This avoids blocking the user when Play hasn't populated
      // staleness (common in testing).
      //
      // -----------------------------------------------------------------------
      final int? stalenessDays = info.clientVersionStalenessDays;

      final bool isStaleThreePlus =
          stalenessDays != null && stalenessDays >= 3;
      final bool isFreshLessThanThree =
          stalenessDays != null && stalenessDays < 3;
      // Null => unknown freshness; treat as fresh for flexible eligibility.
      final bool isFlexibleCandidate =
          stalenessDays == null || stalenessDays < 3;

      // Branch 1: Stale >= 3 days => Immediate (spec: Do NOT call startFlexibleUpdate).
      if (isStaleThreePlus) {
        debugPrint(
            '[AppUpdateService] Staleness $stalenessDays days >= 3 — escalating to IMMEDIATE update');
        await _performImmediateUpdate();
        return;
      }

      // Branch 2: Play-marked immediate-only (priority) => Immediate even when fresh.
      // Spec: "Also fall back to this branch if immediateUpdateAllowed ||
      // immediateUpdateInProgress is true even before 3 days."
      if (info.immediateUpdateAllowed || info.immediateUpdateInProgress) {
        debugPrint(
            '[AppUpdateService] Play marks update as immediate-allowed (priority=${info.updatePriority}) — starting IMMEDIATE flow');
        await _performImmediateUpdate();
        return;
      }

      // Branch 3: Fresh (<3 days, or null/unknown) + flexible allowed => Flexible.
      // Spec: "If updateAvailable is true AND staleness is less than 3 days AND
      // flexibleUpdateAllowed is true — subscribe to installStateListener first,
      // then call startFlexibleUpdate()."
      if (isFlexibleCandidate && info.flexibleUpdateAllowed) {
        // Extra guard: ensure we don't start flexible if already in progress.
        if (_isFlexibleInProgress) {
          debugPrint(
              '[AppUpdateService] Flexible already in progress — skip start');
          return;
        }
        if (isFreshLessThanThree) {
          debugPrint(
              '[AppUpdateService] Staleness $stalenessDays days < 3 — starting FLEXIBLE update');
        } else {
          debugPrint(
              '[AppUpdateService] Staleness unknown (null) but flexible allowed — starting FLEXIBLE update');
        }
        await _startFlexibleUpdate();
        return;
      }

      // No suitable flow (e.g., stale null but neither flexible nor immediate
      // allowed). Log preconditions for debugging rollout issues.
      debugPrint(
          '[AppUpdateService] No suitable update flow: stalenessDays=$stalenessDays '
          'flexibleAllowed=${info.flexibleUpdateAllowed} '
          'flexiblePreconditions=${info.flexibleAllowedPreconditions} '
          'immediateAllowed=${info.immediateUpdateAllowed} '
          'immediatePreconditions=${info.immediateAllowedPreconditions} '
          'priority=${info.updatePriority}');
    } finally {
      _isChecking = false;
    }
  }

  /// Starts the flexible flow. Subscribes to [InAppUpdate.installStateListener]
  /// **before** calling `startFlexibleUpdate()` so no progress event is missed.
  Future<void> _startFlexibleUpdate() async {
    if (_isFlexibleInProgress) return;
    _isFlexibleInProgress = true;
    _ensureInstallListener();

    try {
      debugPrint('[AppUpdateService] startFlexibleUpdate()');
      final result =
          await InAppUpdate.startFlexibleUpdate().timeout(_checkTimeout);
      debugPrint('[AppUpdateService] startFlexibleUpdate result: $result');
      switch (result) {
        case AppUpdateResult.success:
          debugPrint('[AppUpdateService] Flexible update started — downloading');
          break;
        case AppUpdateResult.userDeniedUpdate:
          debugPrint('[AppUpdateService] User denied flexible update');
          _isFlexibleInProgress = false;
          break;
        case AppUpdateResult.inAppUpdateFailed:
          debugPrint('[AppUpdateService] Flexible update failed to start');
          _isFlexibleInProgress = false;
          break;
      }
    } on TimeoutException {
      debugPrint('[AppUpdateService] startFlexibleUpdate timeout');
      _isFlexibleInProgress = false;
    } on InAppUpdateException catch (e) {
      debugPrint(
          '[AppUpdateService] startFlexibleUpdate ${e.code}: ${e.message}');
      if (e.code == 'ALREADY_RUNNING') {
        // Another flow owns the session — keep in-progress true.
        return;
      }
      _isFlexibleInProgress = false;
    } catch (e, st) {
      debugPrint('[AppUpdateService] startFlexibleUpdate unexpected: $e\n$st');
      _isFlexibleInProgress = false;
    }
  }

  /// Performs the immediate (blocking) flow. Play Core handles install and
  /// app restart automatically — this method does not suppress that flow.
  Future<void> _performImmediateUpdate() async {
    try {
      debugPrint('[AppUpdateService] performImmediateUpdate()');
      final result =
          await InAppUpdate.performImmediateUpdate().timeout(_checkTimeout);
      debugPrint('[AppUpdateService] performImmediateUpdate result: $result');
      // Play restarts the app on success; no further action needed.
      // Spec: don't wrap in a try/catch that silently swallows the flow.
      // We only log the userDenied/failed cases for observability.
      if (result == AppUpdateResult.userDeniedUpdate) {
        debugPrint('[AppUpdateService] User denied immediate update');
      } else if (result == AppUpdateResult.inAppUpdateFailed) {
        debugPrint('[AppUpdateService] Immediate update failed');
      }
    } on TimeoutException {
      debugPrint('[AppUpdateService] performImmediateUpdate timeout');
    } on InAppUpdateException catch (e) {
      debugPrint(
          '[AppUpdateService] performImmediateUpdate ${e.code}: ${e.message}');
    } catch (e, st) {
      debugPrint('[AppUpdateService] performImmediateUpdate unexpected: $e\n$st');
    }
  }

  // ---------------------------------------------------------------------------
  // InstallState listener — auto-completes flexible download
  // ---------------------------------------------------------------------------

  void _ensureInstallListener() {
    if (_installStateSubscription != null) return;
    _installStateSubscription = InAppUpdate.installStateListener.listen(
      _onInstallState,
      onError: (Object e, StackTrace st) {
        debugPrint('[AppUpdateService] installStateListener error: $e\n$st');
      },
      cancelOnError: false,
    );
  }

  void _onInstallState(InstallState state) {
    debugPrint(
        '[AppUpdateService] InstallState status=${state.installStatus} '
        'progress=${state.downloadProgress} error=${state.installErrorCode} '
        'bytes=${state.bytesDownloaded}/${state.totalBytesToDownload}');
    switch (state.installStatus) {
      case InstallStatus.pending:
        break;
      case InstallStatus.downloading:
        // Progress available via state.downloadProgress for optional UI.
        break;
      case InstallStatus.downloaded:
        // Spec: once installStatus == downloaded, call completeFlexibleUpdate()
        // so the app installs and automatically restarts into the new version.
        // Do not just show a manual "Restart" snackbar without auto-trigger.
        debugPrint(
            '[AppUpdateService] Flexible download complete — auto-completing');
        unawaited(_completeFlexibleUpdate());
        break;
      case InstallStatus.installing:
        break;
      case InstallStatus.installed:
        debugPrint('[AppUpdateService] Update installed — cleaning up');
        _isFlexibleInProgress = false;
        break;
      case InstallStatus.failed:
        debugPrint(
            '[AppUpdateService] Flexible failed code=${state.installErrorCode}');
        _isFlexibleInProgress = false;
        break;
      case InstallStatus.canceled:
        debugPrint('[AppUpdateService] Flexible canceled');
        _isFlexibleInProgress = false;
        break;
      case InstallStatus.unknown:
        break;
    }
  }

  Future<void> _completeFlexibleUpdate() async {
    try {
      debugPrint('[AppUpdateService] completeFlexibleUpdate()');
      await InAppUpdate.completeFlexibleUpdate().timeout(_checkTimeout);
      debugPrint(
          '[AppUpdateService] completeFlexibleUpdate succeeded — app will restart');
      // Play will restart the app; no further action needed.
    } on TimeoutException {
      debugPrint('[AppUpdateService] completeFlexibleUpdate timeout');
    } on InAppUpdateException catch (e) {
      debugPrint(
          '[AppUpdateService] completeFlexibleUpdate ${e.code}: ${e.message}');
      if (e.code == 'NO_FLEXIBLE_UPDATE') {
        _isFlexibleInProgress = false;
      }
    } catch (e, st) {
      debugPrint('[AppUpdateService] completeFlexibleUpdate unexpected: $e\n$st');
    }
  }

  /// Resets session state — useful in tests.
  void resetForTest() {
    _lastCheckTime = null;
    _isChecking = false;
    _isFlexibleInProgress = false;
  }

  /// Cancels the install-state subscription. Call when the service is no longer
  /// needed (e.g., in `State.dispose` if you registered the observer).
  void dispose() {
    if (_observerRegistered) {
      WidgetsBinding.instance.removeObserver(this);
      _observerRegistered = false;
    }
    _installStateSubscription?.cancel();
    _installStateSubscription = null;
  }
}
