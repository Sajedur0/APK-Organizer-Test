import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Central store for persisted user preferences (theme + sorting).
///
/// Loads once at startup and writes changes back to [SharedPreferences] so the
/// user's choices survive app restarts. Every accessor is defensive: if the
/// platform store is unavailable (very old devices, corrupted prefs) the app
/// keeps running with sensible defaults instead of failing to start.
class PreferencesService {
  PreferencesService._();

  static final PreferencesService instance = PreferencesService._();

  static const _kThemeMode = 'pref_theme_mode';
  static const _kSortMode = 'pref_sort_mode';
  static const _kSortAscending = 'pref_sort_ascending';

  SharedPreferences? _prefs;
  bool _initialized = false;

  /// Notifies listeners (the [MaterialApp]) when the theme mode changes.
  final ValueNotifier<ThemeMode> themeMode = ValueNotifier(ThemeMode.light);

  bool get isInitialized => _initialized;

  Future<void> init() async {
    if (_initialized) return;
    try {
      _prefs = await SharedPreferences.getInstance();
      themeMode.value = _readThemeMode();
    } catch (error, stackTrace) {
      debugPrint('[PreferencesService] init failed: $error\n$stackTrace');
    } finally {
      _initialized = true;
    }
  }

  ThemeMode _readThemeMode() {
    switch (_prefs?.getString(_kThemeMode)) {
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
    try {
      await _prefs?.setString(_kThemeMode, raw);
    } catch (error) {
      debugPrint('[PreferencesService] setThemeMode failed: $error');
    }
  }

  // --- Sorting ---------------------------------------------------------------

  /// Persisted sort mode index (`ApkSortMode.index`), or null when unset.
  int? get sortModeIndex => _prefs?.getInt(_kSortMode);

  /// Persisted sort direction; ascending by default.
  bool get sortAscending => _prefs?.getBool(_kSortAscending) ?? true;

  Future<void> setSort(int modeIndex, bool ascending) async {
    try {
      await _prefs?.setInt(_kSortMode, modeIndex);
      await _prefs?.setBool(_kSortAscending, ascending);
    } catch (error) {
      debugPrint('[PreferencesService] setSort failed: $error');
    }
  }

  /// Clears every stored preference (used by tests / "reset" flows).
  Future<void> clear() async {
    try {
      await _prefs?.clear();
    } catch (error) {
      debugPrint('[PreferencesService] clear failed: $error');
    }
  }
}
