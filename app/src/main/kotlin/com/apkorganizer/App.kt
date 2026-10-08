package com.apkorganizer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.fillMaxSize
import com.apkorganizer.services.PreferencesService
import com.apkorganizer.ui.AppScreen
import com.apkorganizer.ui.screens.ApkDetailPage
import com.apkorganizer.ui.screens.HomePage
import com.apkorganizer.ui.screens.InstalledAppDetailPage
import com.apkorganizer.ui.screens.InstalledAppsPage
import com.apkorganizer.ui.screens.PrivacyPolicyPage
import com.apkorganizer.ui.theme.AppTheme

/**
 * Root composable of the app — the port of `main.dart` / `entry_point.dart`:
 * theming from [PreferencesService] plus a small overlay-stack navigator that
 * mirrors the Flutter Navigator routes.
 */
@Composable
fun ApkOrganizerApp(
    activity: ComponentActivity,
    appVersion: String,
) {
    val themeMode by PreferencesService.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        PreferencesService.ThemeMode.LIGHT -> false
        PreferencesService.ThemeMode.DARK -> true
        PreferencesService.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // The pushed overlay screens (home sits at the bottom of the stack).
    val screens = remember { mutableStateListOf<AppScreen>() }
    val pop: () -> Unit = { if (screens.isNotEmpty()) screens.removeAt(screens.lastIndex) }

    BackHandler(enabled = screens.isNotEmpty()) { pop() }

    AppTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            when (val top = screens.lastOrNull()) {
                null -> HomePage(
                    appVersion = appVersion,
                    push = { screens.add(it) },
                    exitApp = { activity.finish() },
                )
                is AppScreen.ApkDetail -> ApkDetailPage(
                    filePath = top.filePath,
                    appName = top.appName,
                    onBack = pop,
                )
                is AppScreen.InstalledApps -> InstalledAppsPage(
                    includeSystem = top.includeSystem,
                    title = top.title,
                    onBack = pop,
                    onPush = { screens.add(it) },
                )
                is AppScreen.InstalledAppDetail -> InstalledAppDetailPage(
                    app = top.app,
                    onBack = pop,
                    // The Flutter page pops itself before running the action.
                    onBackup = {
                        pop()
                        top.onBackup()
                    },
                    onUninstall = {
                        pop()
                        top.onUninstall()
                    },
                )
                is AppScreen.PrivacyPolicy -> PrivacyPolicyPage(
                    appVersion = top.appVersion,
                    onBack = pop,
                )
            }
        }
    }
}
