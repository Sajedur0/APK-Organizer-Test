package com.apkorganizer.services

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Google Play In-App Update service with **Flexible → Immediate escalation**
 * based on client staleness.
 *
 * ## Business logic (must match the original spec exactly)
 *
 * * Check via Play Core every app start (and on resume, throttled).
 * * Inspect `AppUpdateInfo.clientVersionStalenessDays` — nullable, only
 *   populated by Play Store when the update is served through Play.
 * * If `updateAvailable` AND staleness < 3 days AND `flexibleUpdateAllowed`:
 *   register the [InstallStateUpdatedListener] *first*, then start a flexible
 *   (dismissible native Play UI) update.
 * * If `updateAvailable` AND staleness >= 3 days: do NOT start a flexible
 *   update; perform an immediate (blocking full-screen) update instead. Also
 *   fall back to this branch when an immediate update is allowed or already
 *   in progress (Play can mark an update as immediate-only via priority).
 * * After a flexible download completes (`installStatus == downloaded`),
 *   automatically call `completeUpdate()` so the app installs & restarts into
 *   the new version without requiring a manual "Restart" tap.
 *
 * `clientVersionStalenessDays` is only populated by **Google Play** — local /
 * sideloaded builds report null and never show the Play update UI.
 */
object AppUpdateService {

    private const val TAG = "AppUpdateService"
    const val UPDATE_REQUEST_CODE = 51001

    /** Throttle resume-triggered checks to avoid hammering Play. */
    private const val MIN_RESUME_INTERVAL_MS = 5L * 60 * 1000
    private const val CHECK_TIMEOUT_MS = 12_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var appUpdateManager: AppUpdateManager? = null
    private var installStateListener: InstallStateUpdatedListener? = null
    private var activity: Activity? = null

    @Volatile private var isChecking = false
    @Volatile private var flexibleInProgress = false
    @Volatile private var currentFlowType: Int? = null
    private var lastCheckTime = 0L

    /** Called from MainActivity.onCreate. */
    fun init(activity: Activity) {
        this.activity = activity
        if (appUpdateManager == null) {
            appUpdateManager = AppUpdateManagerFactory.create(activity.applicationContext)
        }
        ensureInstallListener()
    }

    /** Called from MainActivity.onResume (throttled re-check). */
    fun onAppResumed() {
        val now = System.currentTimeMillis()
        if (lastCheckTime != 0L &&
            now - lastCheckTime < MIN_RESUME_INTERVAL_MS &&
            !flexibleInProgress
        ) {
            return
        }
        // Defer — don't block the resume transition.
        scope.launch { checkAndPromptUpdate(isResume = true) }
    }

    /** Main entry point — checks Play and starts the appropriate flow. */
    suspend fun checkAndPromptUpdate(isResume: Boolean = false) {
        val act = activity ?: return
        if (isChecking) return

        // Throttle resume checks.
        if (isResume &&
            lastCheckTime != 0L &&
            System.currentTimeMillis() - lastCheckTime < MIN_RESUME_INTERVAL_MS &&
            !flexibleInProgress
        ) {
            return
        }

        isChecking = true
        lastCheckTime = System.currentTimeMillis()

        try {
            val manager = appUpdateManager ?: return

            // Try/catch around checkForUpdate with silent failure + debug log.
            val info: AppUpdateInfo = try {
                manager.appUpdateInfo.await(CHECK_TIMEOUT_MS) ?: run {
                    Log.d(TAG, "checkForUpdate timed out")
                    return
                }
            } catch (e: Exception) {
                Log.d(TAG, "checkForUpdate failed: $e")
                return
            }

            Log.d(
                TAG,
                "AppUpdateInfo: updateAvailable=" +
                    "${info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE} " +
                    "stalenessDays=${info.clientVersionStalenessDays()} " +
                    "flexibleAllowed=${info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)} " +
                    "immediateAllowed=${info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)} " +
                    "installStatus=${info.installStatus()} " +
                    "availableVersionCode=${info.availableVersionCode()}",
            )

            // Handle already-downloaded flexible update (e.g., app was killed
            // after download). Complete automatically so the app restarts.
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                Log.d(TAG, "Flexible update already downloaded — completing automatically")
                ensureInstallListener()
                flexibleInProgress = true
                completeFlexibleUpdate()
                return
            }

            // If an immediate update was already triggered
            // (developerTriggeredUpdateInProgress), resume it.
            if (info.updateAvailability() ==
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                Log.d(TAG, "Immediate update in progress — resuming immediate flow")
                try {
                    performImmediateUpdate(info)
                } catch (e: Exception) {
                    Log.d(TAG, "Resume immediate failed: $e")
                }
                return
            }

            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
                Log.d(TAG, "No update available")
                return
            }

            // -----------------------------------------------------------------
            // 3-day staleness branch logic (must match spec exactly)
            // -----------------------------------------------------------------
            val stalenessDays: Int? = info.clientVersionStalenessDays()

            val isStaleThreePlus = stalenessDays != null && stalenessDays >= 3
            val isFlexibleCandidate = stalenessDays == null || stalenessDays < 3

            // Branch 1: Stale >= 3 days => Immediate.
            if (isStaleThreePlus) {
                Log.d(
                    TAG,
                    "Staleness $stalenessDays days >= 3 — escalating to IMMEDIATE update",
                )
                performImmediateUpdate(info)
                return
            }

            // Branch 2: Play-marked immediate-only (priority) => Immediate
            // even when fresh.
            if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                Log.d(TAG, "Play marks update as immediate-allowed — starting IMMEDIATE flow")
                performImmediateUpdate(info)
                return
            }

            // Branch 3: Fresh (<3 days, or null/unknown) + flexible allowed =>
            // Flexible. Subscribe to installStateListener first, then start.
            if (isFlexibleCandidate && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                if (flexibleInProgress) {
                    Log.d(TAG, "Flexible already in progress — skip start")
                    return
                }
                Log.d(
                    TAG,
                    "Staleness ${stalenessDays ?: "unknown"} days < 3 — starting FLEXIBLE update",
                )
                startFlexibleUpdate(act, info)
                return
            }

            Log.d(
                TAG,
                "No suitable update flow: stalenessDays=$stalenessDays " +
                    "flexibleAllowed=${info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)} " +
                    "immediateAllowed=${info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)}",
            )
        } finally {
            isChecking = false
        }
    }

    /** Starts the flexible flow. Registers the install listener **before** the update starts. */
    private fun startFlexibleUpdate(act: Activity, info: AppUpdateInfo) {
        if (flexibleInProgress) return
        flexibleInProgress = true
        ensureInstallListener()

        try {
            Log.d(TAG, "startFlexibleUpdate()")
            currentFlowType = AppUpdateType.FLEXIBLE
            appUpdateManager?.startUpdateFlowForResult(
                info,
                AppUpdateType.FLEXIBLE,
                act,
                UPDATE_REQUEST_CODE,
            )
        } catch (e: Exception) {
            Log.d(TAG, "startFlexibleUpdate failed: $e")
            flexibleInProgress = false
        }
    }

    /**
     * Performs the immediate (blocking) flow. Play Core handles install and
     * app restart automatically — this method does not suppress that flow.
     */
    private fun performImmediateUpdate(info: AppUpdateInfo) {
        val act = activity ?: return
        try {
            Log.d(TAG, "performImmediateUpdate()")
            currentFlowType = AppUpdateType.IMMEDIATE
            appUpdateManager?.startUpdateFlowForResult(
                info,
                AppUpdateType.IMMEDIATE,
                act,
                UPDATE_REQUEST_CODE,
            )
        } catch (e: Exception) {
            Log.d(TAG, "performImmediateUpdate failed: $e")
        }
    }

    /** InstallState listener — auto-completes flexible downloads. */
    private fun ensureInstallListener() {
        if (installStateListener != null) return
        val listener = InstallStateUpdatedListener { state ->
            Log.d(
                TAG,
                "InstallState status=${state.installStatus()} " +
                    "bytes=${state.bytesDownloaded()}/${state.totalBytesToDownload()}",
            )
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> {
                    // Once installStatus == downloaded, call completeUpdate() so
                    // the app installs and automatically restarts.
                    Log.d(TAG, "Flexible download complete — auto-completing")
                    completeFlexibleUpdate()
                }
                InstallStatus.INSTALLED -> {
                    Log.d(TAG, "Update installed — cleaning up")
                    flexibleInProgress = false
                }
                InstallStatus.FAILED, InstallStatus.CANCELED -> {
                    Log.d(TAG, "Flexible failed/canceled code=${state.installErrorCode()}")
                    flexibleInProgress = false
                }
                else -> {
                    // PENDING / DOWNLOADING / INSTALLING — progress available
                    // via state.installStatus()/bytesDownloaded().
                }
            }
        }
        installStateListener = listener
        try {
            appUpdateManager?.registerListener(listener)
        } catch (e: Exception) {
            Log.d(TAG, "registerListener failed: $e")
            installStateListener = null
        }
    }

    private fun completeFlexibleUpdate() {
        try {
            Log.d(TAG, "completeUpdate()")
            appUpdateManager?.completeUpdate()
                ?.addOnSuccessListener {
                    Log.d(TAG, "completeUpdate succeeded — app will restart")
                }
                ?.addOnFailureListener { e ->
                    Log.d(TAG, "completeUpdate failed: $e")
                    flexibleInProgress = false
                }
        } catch (e: Exception) {
            Log.d(TAG, "completeUpdate failed: $e")
            flexibleInProgress = false
        }
    }

    /** Routes the update flow's activity result (from MainActivity). */
    fun onUpdateFlowResult(resultCode: Int) {
        when (currentFlowType) {
            AppUpdateType.FLEXIBLE -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "Flexible update started — downloading")
                } else {
                    Log.d(TAG, "User denied flexible update")
                    flexibleInProgress = false
                }
            }
            AppUpdateType.IMMEDIATE -> {
                if (resultCode != Activity.RESULT_OK) {
                    Log.d(TAG, "User denied immediate update")
                }
            }
        }
        currentFlowType = null
    }

    /** Cancels the install-state subscription and scope. */
    fun dispose() {
        installStateListener?.let { listener ->
            try {
                appUpdateManager?.unregisterListener(listener)
            } catch (_: Exception) {
            }
        }
        installStateListener = null
        scope.cancel()
    }

    /** Resets session state — useful in tests. */
    fun resetForTest() {
        lastCheckTime = 0L
        isChecking = false
        flexibleInProgress = false
    }

    /**
     * Awaits a Play Core [Task] with a timeout; null on timeout.
     * Failures rethrow to the caller.
     */
    private suspend fun <T : Any> Task<T>.await(timeoutMs: Long): T? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                addOnSuccessListener { if (cont.isActive) cont.resumeWith(Result.success(it)) }
                addOnFailureListener {
                    if (cont.isActive) cont.resumeWith(Result.failure(it))
                }
                addOnCanceledListener { if (cont.isActive) cont.cancel() }
            }
        }
}
