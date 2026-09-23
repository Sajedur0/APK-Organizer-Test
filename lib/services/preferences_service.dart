import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Central store for persisted user preferences (theme + sorting).
///
/// Loads once at startup and writes changes back to [SharedPreferences] so the
/// user's choices survive app restarts.
class PreferencesService {
  PreferencesService._();

  static final PreferencesService instance = PreferencesService._();

  static const _kThemeMode = 'pref_theme_mode';
  static const _kSortMode = 'pref_sort_mode';
  static const _kSortAscending = 'pref_sort_ascending';

  SharedPreferences? _prefs;

  /// Notifies listeners (the [MaterialApp]) when the theme mode changes.
  final ValueNotifier<ThemeMode> themeMode = ValueNotifier(ThemeMode.light);

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    themeMode.value = _readThemeMode();
  }

  ThemeMode _readThemeMode() {
    final raw = _prefs?.getString(_kThemeMode);
    switch (raw) {
      case 'dark':
        return ThemeMode.dark;
      case 'system':
        return ThemeMode.system;
      default:
        return ThemeMode.light;
    }
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    themeMode.value = mode;
    final raw = switch (mode) {
      ThemeMode.dark => 'dark',
      ThemeMode.light => 'light',
      ThemeMode.system => 'system',
    };
    await _prefs?.setString(_kThemeMode, raw);
  }

  // --- Sorting ---------------------------------------------------------------

  /// Returns the persisted sort mode index, or null if none saved.
  int? get sortModeIndex => _prefs?.getInt(_kSortMode);

  bool get sortAscending => _prefs?.getBool(_kSortAscending) ?? true;

  Future<void> setSort(int modeIndex, bool ascending) async {
    await _prefs?.setInt(_kSortMode, modeIndex);
    await _prefs?.setBool(_kSortAscending, ascending);
  }
}
