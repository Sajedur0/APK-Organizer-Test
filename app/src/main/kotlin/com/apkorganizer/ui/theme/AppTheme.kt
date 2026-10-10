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
 * The v3 glassmorphism design uses generous, soft radii: cards 24, controls
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
 * Signature brushes of the glassmorphism redesign.
 *
 * [hero] is a translucent deep-green → sage sweep (white text sits on it in
 * both themes), while [backdrop] paints the frosted deep-green canvas the
 * glass surfaces float on.
 */
object AppGradients {
    val hero: List<Color>
        @Composable get() =
            if (LocalIsDarkTheme.current) {
                listOf(Color(0xE61E4430), Color(0xB337684A))
            } else {
                listOf(Color(0xE63E6B4A), Color(0xCC79A57F))
            }

    val backdrop: List<Color>
        @Composable get() =
            if (LocalIsDarkTheme.current) {
                listOf(Color(0xFF0D1F16), Color(0xFF123122))
            } else {
                listOf(Color(0xFFEAF2E6), Color(0xFFDCE9D6))
            }
}

/** Divider tint (theme divider color: outlineVariant at 120/160 alpha). */
val LocalAppDividerColor = staticCompositionLocalOf { Color.Unspecified }

/** Whether the dark theme is currently active (exposed to widgets). */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * Deep-forest "Leafora-style" dark scheme.
 *
 * The container tokens are translucent white, so every card, dialog, sheet
 * and bar reads as frosted glass floating on the green backdrop — the core
 * of the glassmorphism look. The alphas are deliberately thick so the frosted
 * panels blur/hide whatever sits behind them.
 */
private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFABD3A4),
    onPrimary = Color(0xFF1B3524),
    primaryContainer = Color(0xFF2C4E37),
    onPrimaryContainer = Color(0xFFD2E8C9),
    inversePrimary = Color(0xFF477A52),
    secondary = Color(0xFFA9CDBD),
    onSecondary = Color(0xFF14342B),
    secondaryContainer = Color(0xFF2A4A3F),
    onSecondaryContainer = Color(0xFFC9E6DA),
    tertiary = Color(0xFFDCC69C),
    onTertiary = Color(0xFF3C3012),
    tertiaryContainer = Color(0xFF544626),
    onTertiaryContainer = Color(0xFFF2E1BC),
    background = Color(0xFF0B1712),
    onBackground = Color(0xFFDFE8DC),
    surface = Color(0xFF0B1712),
    onSurface = Color(0xFFDFE8DC),
    surfaceVariant = Color(0x59FFFFFF),
    onSurfaceVariant = Color(0xFFB4C6B2),
    surfaceTint = Color(0xFFABD3A4),
    inverseSurface = Color(0xFFDFE8DC),
    inverseOnSurface = Color(0xFF233127),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF7F927E),
    outlineVariant = Color(0xFF3A5243),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF27392C),
    surfaceDim = Color(0xFF0B1712),
    surfaceContainer = Color(0x59FFFFFF),
    surfaceContainerHigh = Color(0x66FFFFFF),
    surfaceContainerHighest = Color(0x7AFFFFFF),
    surfaceContainerLow = Color(0x4DFFFFFF),
    surfaceContainerLowest = Color(0xFF07110C),
)

/** Frosted light scheme — white glass over a pale sage canvas (thick frost, nothing shows through). */
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3C6B4B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE5C4),
    onPrimaryContainer = Color(0xFF0F2A19),
    inversePrimary = Color(0xFFB1C9A6),
    secondary = Color(0xFF526357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8D9),
    onSecondaryContainer = Color(0xFF101F16),
    tertiary = Color(0xFF6B5B3C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1DFB8),
    onTertiaryContainer = Color(0xFF241903),
    background = Color(0xFFEFF4EB),
    onBackground = Color(0xFF1A241C),
    surface = Color(0xFFEFF4EB),
    onSurface = Color(0xFF1A241C),
    surfaceVariant = Color(0xE6FFFFFF),
    onSurfaceVariant = Color(0xFF455245),
    surfaceTint = Color(0xFF3C6B4B),
    inverseSurface = Color(0xFF2B382C),
    inverseOnSurface = Color(0xFFF0F6EC),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF75846F),
    outlineVariant = Color(0xFFC6D5C0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD8E2D2),
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
