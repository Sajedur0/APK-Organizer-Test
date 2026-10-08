package com.apkorganizer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for corner radii.
 *
 * Everything rounded comes from here: cards 14, controls 12, dialogs 20,
 * sheets 28, chips 8 — the exact values the Flutter theme used.
 */
object AppRadius {
    val card = 14.dp
    val control = 12.dp
    val dialog = 20.dp
    val sheet = 28.dp
    val chip = 8.dp

    val cardShape: RoundedCornerShape get() = RoundedCornerShape(card)
    val controlShape: RoundedCornerShape get() = RoundedCornerShape(control)
    val dialogShape: RoundedCornerShape get() = RoundedCornerShape(dialog)
    val sheetShape: RoundedCornerShape get() = RoundedCornerShape(topStart = sheet, topEnd = sheet)
}

/** Flutter `Colors.deepOrange` — used by the Move actions. */
val DeepOrange = Color(0xFFFF5722)

/** Divider tint (theme divider color: outlineVariant at 120/160 alpha). */
val LocalAppDividerColor = staticCompositionLocalOf { Color.Unspecified }

/** Whether the dark theme is currently active (exposed to widgets). */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF006C5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF2D9),
    onPrimaryContainer = Color(0xFF002019),
    inversePrimary = Color(0xFF82D5BE),
    secondary = Color(0xFF5B5D00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E878),
    onSecondaryContainer = Color(0xFF1B1C00),
    tertiary = Color(0xFF8A4A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB9),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFAFCF7),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFAFCF7),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFE7E9E4),
    onSurfaceVariant = Color(0xFF414944),
    surfaceTint = Color(0xFF006C5B),
    inverseSurface = Color(0xFF2E312F),
    inverseOnSurface = Color(0xFFF0F1ED),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9C0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFCF7),
    surfaceDim = Color(0xFFDADBD6),
    surfaceContainer = Color(0xFFEDEFEA),
    surfaceContainerHigh = Color(0xFFE7E9E4),
    surfaceContainerHighest = Color(0xFFE1E3DF),
    surfaceContainerLow = Color(0xFFF3F6F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF82D5BE),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005144),
    onPrimaryContainer = Color(0xFF9EF2D9),
    inversePrimary = Color(0xFF006C5B),
    secondary = Color(0xFFC8CB60),
    onSecondary = Color(0xFF2F3100),
    secondaryContainer = Color(0xFF444600),
    onSecondaryContainer = Color(0xFFE4E878),
    tertiary = Color(0xFFFFB866),
    onTertiary = Color(0xFF492900),
    tertiaryContainer = Color(0xFF683C00),
    onTertiaryContainer = Color(0xFFFFDDB9),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE0E4DE),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE0E4DE),
    surfaceVariant = Color(0xFF282B29),
    onSurfaceVariant = Color(0xFFC1C9C0),
    surfaceTint = Color(0xFF82D5BE),
    inverseSurface = Color(0xFFE0E4DE),
    inverseOnSurface = Color(0xFF2E312F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414944),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363A38),
    surfaceDim = Color(0xFF101412),
    surfaceContainer = Color(0xFF1D211F),
    surfaceContainerHigh = Color(0xFF282B29),
    surfaceContainerHighest = Color(0xFF333634),
    surfaceContainerLow = Color(0xFF191C1A),
    surfaceContainerLowest = Color(0xFF0B0F0D),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppRadius.chip),
    small = RoundedCornerShape(AppRadius.control),
    medium = RoundedCornerShape(AppRadius.card),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(AppRadius.dialog),
)

@Composable
fun AppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val dividerColor = colorScheme.outlineVariant.copy(
        alpha = if (darkTheme) 120f / 255f else 160f / 255f,
    )

    CompositionLocalProvider(
        LocalAppDividerColor provides dividerColor,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Convenience accessor for the themed divider color. */
object AppThemeDefaults {
    val dividerColor: Color
        @Composable get() = LocalAppDividerColor.current
}
