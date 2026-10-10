package com.apkorganizer

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apkorganizer.services.PreferencesService
import com.apkorganizer.ui.AppScreen
import com.apkorganizer.ui.screens.ApkDetailPage
import com.apkorganizer.ui.screens.HomePage
import com.apkorganizer.ui.screens.InstalledAppDetailPage
import com.apkorganizer.ui.screens.InstalledAppsPage
import com.apkorganizer.ui.screens.PrivacyPolicyPage
import com.apkorganizer.ui.theme.AppGradients
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
            GlassBackdrop {
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
}

/**
 * The frosted canvas of the glassmorphism design: a vertical green gradient
 * plus two soft sage/sand glows for the translucent "glass" surfaces to
 * float on. Screens render their scaffolds transparently on top of it.
 */
@Composable
private fun GlassBackdrop(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(AppGradients.backdrop)),
    ) {
        Box(
            Modifier
                .size(360.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        listOf(scheme.primary.copy(alpha = 0.22f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .size(320.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-80).dp, y = 90.dp)
                .background(
                    Brush.radialGradient(
                        listOf(scheme.tertiary.copy(alpha = 0.16f), Color.Transparent),
                    ),
                ),
        )
        content()
    }
}
