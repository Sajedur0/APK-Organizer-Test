import 'package:flutter/material.dart';

/// Single source of truth for corner radii.
///
/// Widgets used to hardcode 12/14/16/20/28 while the theme used 8, which made
/// ripples spill outside card corners and gave sheets/dialogs inconsistent
/// shapes. Everything rounded now comes from here.
class AppRadius {
  const AppRadius._();

  /// Cards and list tiles.
  static const double card = 14;

  /// Buttons, text fields, popups, menus.
  static const double control = 12;

  /// Dialogs.
  static const double dialog = 20;

  /// Modal bottom sheets.
  static const double sheet = 28;

  /// Small badges / chips.
  static const double chip = 8;

  static BorderRadius get cardBorder => BorderRadius.circular(card);
  static BorderRadius get controlBorder => BorderRadius.circular(control);
  static BorderRadius get dialogBorder => BorderRadius.circular(dialog);
  static BorderRadius get sheetBorder =>
      const BorderRadius.vertical(top: Radius.circular(sheet));
}

class AppTheme {
  const AppTheme._();

  static const _lightScheme = ColorScheme(
    brightness: Brightness.light,
    primary: Color(0xFF006C5B),
    onPrimary: Color(0xFFFFFFFF),
    primaryContainer: Color(0xFF9EF2D9),
    onPrimaryContainer: Color(0xFF002019),
    secondary: Color(0xFF5B5D00),
    onSecondary: Color(0xFFFFFFFF),
    secondaryContainer: Color(0xFFE4E878),
    onSecondaryContainer: Color(0xFF1B1C00),
    tertiary: Color(0xFF8A4A00),
    onTertiary: Color(0xFFFFFFFF),
    tertiaryContainer: Color(0xFFFFDDB9),
    onTertiaryContainer: Color(0xFF2C1600),
    error: Color(0xFFBA1A1A),
    onError: Color(0xFFFFFFFF),
    errorContainer: Color(0xFFFFDAD6),
    onErrorContainer: Color(0xFF410002),
    surface: Color(0xFFFAFCF7),
    onSurface: Color(0xFF191C1A),
    surfaceContainerLowest: Color(0xFFFFFFFF),
    surfaceContainerLow: Color(0xFFF3F6F1),
    surfaceContainer: Color(0xFFEDEFEA),
    surfaceContainerHigh: Color(0xFFE7E9E4),
    surfaceContainerHighest: Color(0xFFE1E3DF),
    onSurfaceVariant: Color(0xFF414944),
    outline: Color(0xFF717971),
    outlineVariant: Color(0xFFC1C9C0),
    shadow: Color(0xFF000000),
    scrim: Color(0xFF000000),
    inverseSurface: Color(0xFF2E312F),
    onInverseSurface: Color(0xFFF0F1ED),
    inversePrimary: Color(0xFF82D5BE),
  );

  static const _darkScheme = ColorScheme(
    brightness: Brightness.dark,
    primary: Color(0xFF82D5BE),
    onPrimary: Color(0xFF00382F),
    primaryContainer: Color(0xFF005144),
    onPrimaryContainer: Color(0xFF9EF2D9),
    secondary: Color(0xFFC8CB60),
    onSecondary: Color(0xFF2F3100),
    secondaryContainer: Color(0xFF444600),
    onSecondaryContainer: Color(0xFFE4E878),
    tertiary: Color(0xFFFFB866),
    onTertiary: Color(0xFF492900),
    tertiaryContainer: Color(0xFF683C00),
    onTertiaryContainer: Color(0xFFFFDDB9),
    error: Color(0xFFFFB4AB),
    onError: Color(0xFF690005),
    errorContainer: Color(0xFF93000A),
    onErrorContainer: Color(0xFFFFDAD6),
    surface: Color(0xFF101412),
    onSurface: Color(0xFFE0E4DE),
    surfaceContainerLowest: Color(0xFF0B0F0D),
    surfaceContainerLow: Color(0xFF191C1A),
    surfaceContainer: Color(0xFF1D211F),
    surfaceContainerHigh: Color(0xFF282B29),
    surfaceContainerHighest: Color(0xFF333634),
    onSurfaceVariant: Color(0xFFC1C9C0),
    outline: Color(0xFF8B938B),
    outlineVariant: Color(0xFF414944),
    shadow: Color(0xFF000000),
    scrim: Color(0xFF000000),
    inverseSurface: Color(0xFFE0E4DE),
    onInverseSurface: Color(0xFF2E312F),
    inversePrimary: Color(0xFF006C5B),
  );

  /// Themes are built once and cached: [ThemeData] construction is not free and
  /// the app asks for these on every rebuild of the root widget.
  static final ThemeData light = _theme(_lightScheme);
  static final ThemeData dark = _theme(_darkScheme);

  static ThemeData _theme(ColorScheme scheme) {
    final isDark = scheme.brightness == Brightness.dark;
    return ThemeData(
      colorScheme: scheme,
      useMaterial3: true,
      scaffoldBackgroundColor: scheme.surface,
      appBarTheme: AppBarTheme(
        centerTitle: false,
        elevation: 0,
        scrolledUnderElevation: 0,
        backgroundColor: scheme.surface,
        foregroundColor: scheme.onSurface,
        surfaceTintColor: Colors.transparent,
        titleTextStyle: TextStyle(
          color: scheme.onSurface,
          fontSize: 20,
          fontWeight: FontWeight.w700,
        ),
      ),
      cardTheme: CardThemeData(
        color: scheme.surfaceContainerLow,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: AppRadius.cardBorder,
          side: BorderSide(color: scheme.outlineVariant.withAlpha(120)),
        ),
      ),
      drawerTheme: DrawerThemeData(
        backgroundColor: scheme.surface,
        surfaceTintColor: Colors.transparent,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.horizontal(
            right: Radius.circular(AppRadius.dialog),
          ),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.dialogBorder),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(AppRadius.sheet),
          ),
        ),
      ),
      popupMenuTheme: PopupMenuThemeData(
        color: scheme.surfaceContainerHigh,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.controlBorder),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainerLow,
        border: OutlineInputBorder(
          borderRadius: AppRadius.controlBorder,
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: AppRadius.controlBorder,
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: AppRadius.controlBorder,
          borderSide: BorderSide(color: scheme.primary, width: 1.6),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          shape: RoundedRectangleBorder(borderRadius: AppRadius.controlBorder),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          shape: RoundedRectangleBorder(borderRadius: AppRadius.controlBorder),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          shape: RoundedRectangleBorder(borderRadius: AppRadius.controlBorder),
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          shape: RoundedRectangleBorder(borderRadius: AppRadius.controlBorder),
        ),
      ),
      listTileTheme: const ListTileThemeData(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(AppRadius.control)),
        ),
      ),
      chipTheme: ChipThemeData(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.chip),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: isDark
            ? scheme.surfaceContainerHighest
            : scheme.inverseSurface,
        contentTextStyle: TextStyle(
          color: isDark ? scheme.onSurface : scheme.onInverseSurface,
          fontWeight: FontWeight.w600,
        ),
        shape: RoundedRectangleBorder(borderRadius: AppRadius.controlBorder),
      ),
      dividerTheme: DividerThemeData(
        color: scheme.outlineVariant.withAlpha(isDark ? 120 : 160),
      ),
    );
  }
}
