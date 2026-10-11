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
 * The v4 glassmorphism design uses generous, soft radii: cards 24, controls
 * 16, dialogs 28, sheets 32, chips 12.
 */
object AppRadius {
    val card = 24.dp
    val control = 16.dp
    val dialog = 28.dp
    val sheet = 32.dp
    val chip = 12.dp

    val cardShape: RoundedCornerShape get() = RoundedCornerShape(card)
    val controlShape: RoundedCornerShape get() = RoundedCornerShape(control)
    val dialogShape: RoundedCornerShape get() = RoundedCornerShape(dialog)
    val sheetShape: RoundedCornerShape get() = RoundedCornerShape(topStart = sheet, topEnd = sheet)
}

/** Flutter `Colors.deepOrange` — used by the Move actions. */
val DeepOrange = Color(0xFFFF5722)

/**
 * Brand palette of the "Midnight Azure" professional redesign.
 *
 * A deep navy canvas with an azure primary, a teal secondary and a soft
 * indigo tertiary — a cool, restrained, enterprise-grade combination that
 * keeps frosted-glass surfaces crisp and legible. Kept in one place so the
 * schemes, gradients and glass fallbacks can never drift apart.
 */
internal object AppPalette {
    // Dark canvas / glass fallbacks
    val darkCanvasTop = Color(0xFF080F1D)
    val darkCanvasBottom = Color(0xFF0F1B33)
    val darkGlassSlab = Color(0xFF16233D)

    // Light canvas / glass fallbacks
    val lightCanvasTop = Color(0xFFF0F4FB)
    val lightCanvasBottom = Color(0xFFDDE7F6)
    val lightGlassSlab = Color(0xFFE6ECF8)

    // Hero card sweep (white text sits on it in both themes)
    val darkHeroStart = Color(0xE61F3F86)
    val darkHeroEnd = Color(0xB33B6FD8)
    val lightHeroStart = Color(0xE62A4FB8)
    val lightHeroEnd = Color(0xCC5B88E6)
}

/**
 * Signature brushes of the glassmorphism redesign.
 *
 * [hero] is a translucent azure sweep (white text sits on it in both
 * themes), while [backdrop] paints the frosted navy canvas the glass
 * surfaces float on.
 */
object AppGradients {
    val hero: List<Color>
        @Composable get() =
            if (LocalIsDarkTheme.current) {
                listOf(AppPalette.darkHeroStart, AppPalette.darkHeroEnd)
            } else {
                listOf(AppPalette.lightHeroStart, AppPalette.lightHeroEnd)
            }

    val backdrop: List<Color>
        @Composable get() =
            if (LocalIsDarkTheme.current) {
                listOf(AppPalette.darkCanvasTop, AppPalette.darkCanvasBottom)
            } else {
                listOf(AppPalette.lightCanvasTop, AppPalette.lightCanvasBottom)
            }
}

/** Divider tint (theme divider color: outlineVariant at 120/160 alpha). */
val LocalAppDividerColor = staticCompositionLocalOf { Color.Unspecified }

/** Whether the dark theme is currently active (exposed to widgets). */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * Midnight Azure dark scheme.
 *
 * The container tokens are translucent white, so every card, dialog, sheet
 * and bar reads as frosted glass floating on the navy backdrop — the core
 * of the glassmorphism look. The alphas are deliberately thick so the frosted
 * panels blur/hide whatever sits behind them.
 */
private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0A2A5C),
    primaryContainer = Color(0xFF1E3F7A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    inversePrimary = Color(0xFF2F5FC4),
    secondary = Color(0xFF7FD4C8),
    onSecondary = Color(0xFF00372F),
    secondaryContainer = Color(0xFF1B4A45),
    onSecondaryContainer = Color(0xFFBDF1EA),
    tertiary = Color(0xFFC4B5FD),
    onTertiary = Color(0xFF2E1A66),
    tertiaryContainer = Color(0xFF45348A),
    onTertiaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFF0A1120),
    onBackground = Color(0xFFE3E9F5),
    surface = Color(0xFF0A1120),
    onSurface = Color(0xFFE3E9F5),
    surfaceVariant = Color(0x59FFFFFF),
    onSurfaceVariant = Color(0xFFB3BFD6),
    surfaceTint = Color(0xFF8AB4FF),
    inverseSurface = Color(0xFFE3E9F5),
    inverseOnSurface = Color(0xFF1B2538),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF7C8AA5),
    outlineVariant = Color(0xFF34425C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF26324A),
    surfaceDim = Color(0xFF0A1120),
    surfaceContainer = Color(0x59FFFFFF),
    surfaceContainerHigh = Color(0x66FFFFFF),
    surfaceContainerHighest = Color(0x7AFFFFFF),
    surfaceContainerLow = Color(0x4DFFFFFF),
    surfaceContainerLowest = Color(0xFF060B16),
)

/** Frosted light scheme — white glass over a pale blue canvas (thick frost, nothing shows through). */
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2B56C8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E5FF),
    onPrimaryContainer = Color(0xFF0A1F4D),
    inversePrimary = Color(0xFFAFC6FF),
    secondary = Color(0xFF2F6B66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDEDE8),
    onSecondaryContainer = Color(0xFF07201D),
    tertiary = Color(0xFF5B49B0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6DEFF),
    onTertiaryContainer = Color(0xFF1B0B52),
    background = Color(0xFFF2F5FB),
    onBackground = Color(0xFF121A2B),
    surface = Color(0xFFF2F5FB),
    onSurface = Color(0xFF121A2B),
    surfaceVariant = Color(0xE6FFFFFF),
    onSurfaceVariant = Color(0xFF3F4A60),
    surfaceTint = Color(0xFF2B56C8),
    inverseSurface = Color(0xFF263044),
    inverseOnSurface = Color(0xFFEEF2FB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7C96),
    outlineVariant = Color(0xFFC5CFE2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD6DEEC),
    surfaceContainer = Color(0xE6FFFFFF),
    surfaceContainerHigh = Color(0xF2FFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xD9FFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppRadius.chip),
    small = RoundedCornerShape(AppRadius.control),
    medium = RoundedCornerShape(AppRadius.card),
    large = RoundedCornerShape(28.dp),
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
