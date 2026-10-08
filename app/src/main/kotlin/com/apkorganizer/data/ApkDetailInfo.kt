package com.apkorganizer.data

import com.apkorganizer.utils.FormatUtil

/**
 * A permission requested by an APK, as declared in its manifest.
 *
 * Note that for an APK file (not yet installed) [granted] only reflects the
 * manifest flags, not a runtime grant.
 */
class ApkPermission(
    val name: String,
    val granted: Boolean,
) {

    /** Short, readable name: `INTERNET` instead of `android.permission.INTERNET`. */
    val shortName: String by lazy {
        if (name.startsWith(ANDROID_PREFIX)) {
            return@lazy name.substring(ANDROID_PREFIX.length)
        }
        if (name.startsWith("com.") || name.startsWith("android.")) {
            val parts = name.split(".")
            if (parts.isEmpty()) name else parts.last()
        } else {
            name
        }
    }

    /** Best-effort grouping used for the "risky" hint in the detail page. */
    val isSensitive: Boolean by lazy {
        name.startsWith(ANDROID_PREFIX) && SENSITIVE_PERMISSIONS.contains(shortName)
    }

    override fun toString(): String = name

    companion object {
        const val ANDROID_PREFIX = "android.permission."

        private val SENSITIVE_PERMISSIONS = setOf(
            "SEND_SMS",
            "RECEIVE_SMS",
            "READ_SMS",
            "CALL_PHONE",
            "READ_CONTACTS",
            "WRITE_CONTACTS",
            "RECORD_AUDIO",
            "CAMERA",
            "ACCESS_FINE_LOCATION",
            "ACCESS_BACKGROUND_LOCATION",
            "READ_CALL_LOG",
            "WRITE_CALL_LOG",
            "READ_PHONE_STATE",
            "SYSTEM_ALERT_WINDOW",
            "REQUEST_INSTALL_PACKAGES",
        )
    }
}

/** Full details of a single APK file (parsed natively). */
class ApkDetailInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val appName: String,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val supportedAbis: List<String>,
    val signatureHash: String?,
    val iconPath: String?,
    val fileSize: Long,
    val fileName: String,
    val filePath: String,
    val permissions: List<ApkPermission>,
) {

    val formattedSize: String by lazy { FormatUtil.formatBytes(fileSize) }

    val signatureDisplay: String by lazy {
        val hash = signatureHash
        when {
            hash == null || hash.isEmpty() -> "Unknown"
            hash.length <= 16 -> hash
            else -> "${hash.substring(0, 8)}...${hash.substring(hash.length - 8)}"
        }
    }

    val sdkDisplay: String
        get() = "API $minSdkVersion → API $targetSdkVersion"

    val abisDisplay: String
        get() = if (supportedAbis.isEmpty()) "Unknown" else supportedAbis.joinToString(", ")

    /** Permissions that are usually worth a second look, shown first. */
    val sensitivePermissions: List<ApkPermission> by lazy {
        permissions.filter { it.isSensitive }
    }

    val sensitivePermissionCount: Int by lazy { sensitivePermissions.size }
}
