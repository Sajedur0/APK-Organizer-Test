package com.apkorganizer.utils

import android.content.Context
import android.content.pm.PackageManager

/** Reads the app's own version (replaces `package_info_plus`). */
object VersionUtil {

    private var cachedVersion = "1.0.0"

    fun getAppVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            cachedVersion = info.versionName ?: cachedVersion
            cachedVersion
        } catch (e: PackageManager.NameNotFoundException) {
            cachedVersion
        } catch (e: Exception) {
            cachedVersion
        }
    }
}
