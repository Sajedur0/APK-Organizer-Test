package com.apkorganizer.ui

import com.apkorganizer.data.InstalledApp
import java.util.concurrent.atomic.AtomicInteger

/**
 * Overlay screens pushed on top of the home screen (the equivalent of the
 * Flutter Navigator routes).
 */
sealed class AppScreen(val id: Int) {
    class ApkDetail(id: Int, val filePath: String, val appName: String) : AppScreen(id)
    class InstalledApps(id: Int, val includeSystem: Boolean, val title: String) : AppScreen(id)
    class InstalledAppDetail(
        id: Int,
        val app: InstalledApp,
        val onBackup: () -> Unit,
        val onUninstall: () -> Unit,
    ) : AppScreen(id)
    class PrivacyPolicy(id: Int, val appVersion: String) : AppScreen(id)
}

/** Monotonic ids so every pushed screen is unique. */
object ScreenIds {
    private val counter = AtomicInteger(0)
    fun next(): Int = counter.incrementAndGet()
}
