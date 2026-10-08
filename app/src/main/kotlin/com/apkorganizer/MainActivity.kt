package com.apkorganizer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.apkorganizer.data.ApkManager
import com.apkorganizer.services.AppUpdateService
import com.apkorganizer.services.PreferencesService

/**
 * Single-activity host of the Compose UI — the port of the Flutter
 * `MainActivity` (which drove the app through the embedded ApkManagerPlugin).
 *
 * All platform interactions the plugin used to own now happen here directly:
 * permission activity-result launchers (wired into [ApkManager.attach]), the
 * package-removed broadcast receiver and the Play In-App Update flow result.
 */
class MainActivity : ComponentActivity() {

    private lateinit var manageStorageLauncher: ActivityResultLauncher<Intent>
    private lateinit var runtimePermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var installPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var installFromInstallLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ApkManager.init(this)
        PreferencesService.init(this)
        AppUpdateService.init(this)

        manageStorageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                ApkManager.onManageStorageResult()
            }
        runtimePermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                ApkManager.onRuntimePermissionsResult(grants)
            }
        installPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                ApkManager.onInstallPermissionResult()
            }
        installFromInstallLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                ApkManager.onInstallFromInstallResult()
            }

        ApkManager.attach(
            activity = this,
            manageStorage = manageStorageLauncher,
            runtimePermissions = runtimePermissionsLauncher,
            installPermission = installPermissionLauncher,
            installFromInstall = installFromInstallLauncher,
        )
        ApkManager.registerPackageRemovedReceiver(this)

        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

        setContent {
            ApkOrganizerApp(activity = this, appVersion = appVersion)
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdateService.onAppResumed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AppUpdateService.UPDATE_REQUEST_CODE) {
            AppUpdateService.onUpdateFlowResult(resultCode)
        }
    }

    override fun onDestroy() {
        ApkManager.unregisterPackageRemovedReceiver(this)
        ApkManager.detach(this)
        // Matches the Flutter app: the flexible-update listener is kept alive
        // for the app lifetime; only the activity reference is dropped here.
        super.onDestroy()
    }
}
