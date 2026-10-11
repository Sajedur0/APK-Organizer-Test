package com.apkorganizer

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import com.apkorganizer.services.PreferencesService
import com.apkorganizer.ui.AppScreen
import com.apkorganizer.ui.screens.ApkDetailPage
import com.apkorganizer.ui.screens.HomePage
import com.apkorganizer.ui.screens.InstalledAppDetailPage
import com.apkorganizer.ui.screens.InstalledAppsPage
import com.apkorganizer.ui.screens.PrivacyPolicyPage
import com.apkorganizer.ui.theme.AppTheme
import com.apkorganizer.ui.theme.GlassBlurState
import com.apkorganizer.ui.theme.LocalGlassBlurState
import com.apkorganizer.ui.theme.glassCanvas
import com.apkorganizer.ui.theme.rememberGlassBlurState

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

    val glassBlur = rememberGlassBlurState()

    AppTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalGlassBlurState provides glassBlur) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            GlassBackdrop(glassBlur) {
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
}

/**
 * The frosted canvas of the glassmorphism design: a vertical navy gradient
 * plus two soft azure/teal glows (see [glassCanvas]) for the translucent
 * "glass" surfaces to float on. Screens render their scaffolds transparently
 * on top of it. The canvas size is tracked so frosted panels can repaint the
 * backdrop seamlessly behind themselves.
 *
 * The animated canvas lives in its own leaf composable ([AuroraCanvas]) so
 * the per-frame aurora phase only re-draws the backdrop layer and never
 * recomposes the screens on top of it.
 */
@Composable
private fun GlassBackdrop(
    glassBlur: GlassBlurState,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AuroraCanvas(glassBlur)
        content()
    }
}

/**
 * The living part of the v1.3.2 "aurora glass" look: the two background
 * glows drift slowly along small orbits and breathe in size/alpha on a
 * [AURORA_CYCLE_MS] loop, so the frosted canvas feels lit from within.
 */
@Composable
private fun AuroraCanvas(glassBlur: GlassBlurState) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AURORA_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "auroraPhase",
    )
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { glassBlur.canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .glassCanvas(phase),
    )
}

/** One full drift/breathe cycle of the background aurora glows. */
private const val AURORA_CYCLE_MS = 12_000
