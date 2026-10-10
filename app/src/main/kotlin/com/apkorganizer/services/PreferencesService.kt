package com.apkorganizer.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Central store for persisted user preferences (theme + sorting).
 *
 * Loads once at startup and writes changes back to [SharedPreferences] so the
 * user's choices survive app restarts. Every accessor is defensive: if the
 * platform store is unavailable the app keeps running with sensible defaults
 * instead of failing to start.
 */
object PreferencesService {

    enum class ThemeMode { LIGHT, DARK, SYSTEM }

    private const val PREFS_NAME = "apk_organizer_prefs"
    private const val KEY_THEME_MODE = "pref_theme_mode"
    private const val KEY_SORT_MODE = "pref_sort_mode"
    private const val KEY_SORT_ASCENDING = "pref_sort_ascending"
    private const val KEY_LAST_DIRECTORY = "pref_last_used_directory"

    private var prefs: SharedPreferences? = null
    private var initialized = false

    /** Notifies the app root when the theme mode changes. */
    val themeMode = MutableStateFlow(ThemeMode.LIGHT)

    val isInitialized: Boolean
        get() = initialized

    fun init(context: Context) {
        if (initialized) return
        try {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            themeMode.value = readThemeMode()
        } catch (error: Exception) {
            Log.w("PreferencesService", "init failed: $error")
        } finally {
            initialized = true
        }
    }

    private fun readThemeMode(): ThemeMode = when (prefs?.getString(KEY_THEME_MODE, null)) {
        "dark" -> ThemeMode.DARK
        "system" -> ThemeMode.SYSTEM
        else -> ThemeMode.LIGHT
    }

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        val raw = when (mode) {
            ThemeMode.DARK -> "dark"
            ThemeMode.LIGHT -> "light"
            ThemeMode.SYSTEM -> "system"
        }
        try {
            prefs?.edit()?.putString(KEY_THEME_MODE, raw)?.apply()
        } catch (error: Exception) {
            Log.w("PreferencesService", "setThemeMode failed: $error")
        }
    }

    // --- Sorting ---------------------------------------------------------------

    /** Persisted sort mode index, or null when unset. */
    val sortModeIndex: Int?
        get() = prefs?.getInt(KEY_SORT_MODE, -1)?.takeIf { it >= 0 }

    /** Persisted sort direction; ascending by default. */
    val sortAscending: Boolean
        get() = prefs?.getBoolean(KEY_SORT_ASCENDING, true) ?: true

    fun setSort(modeIndex: Int, ascending: Boolean) {
        try {
            prefs?.edit()
                ?.putInt(KEY_SORT_MODE, modeIndex)
                ?.putBoolean(KEY_SORT_ASCENDING, ascending)
                ?.apply()
        } catch (error: Exception) {
            Log.w("PreferencesService", "setSort failed: $error")
        }
    }

    // --- Last used destination -------------------------------------------------

    /**
     * The folder used most recently for a move/backup. Shown as a shortcut at
     * the top of the directory picker so repeat operations take one tap.
     */
    val lastUsedDirectory: String?
        get() = try {
            prefs?.getString(KEY_LAST_DIRECTORY, null)?.takeIf { it.isNotEmpty() }
        } catch (error: Exception) {
            Log.w("PreferencesService", "lastUsedDirectory read failed: $error")
            null
        }

    fun setLastUsedDirectory(path: String?) {
        try {
            if (path.isNullOrEmpty()) {
                prefs?.edit()?.remove(KEY_LAST_DIRECTORY)?.apply()
            } else {
                prefs?.edit()?.putString(KEY_LAST_DIRECTORY, path)?.apply()
            }
        } catch (error: Exception) {
            Log.w("PreferencesService", "setLastUsedDirectory failed: $error")
        }
    }

    /** Clears every stored preference (used by tests / "reset" flows). */
    fun clear() {
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (error: Exception) {
            Log.w("PreferencesService", "clear failed: $error")
        }
    }
}
