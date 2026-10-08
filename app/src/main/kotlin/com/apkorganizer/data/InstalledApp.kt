package com.apkorganizer.data

import com.apkorganizer.utils.FormatUtil

/**
 * One installed application on the device.
 *
 * Derived strings (display name, search text, size label) are cached on the
 * instance so list builds and searches never re-compute them.
 */
class InstalledApp(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val iconPath: String?,
    val sourceDir: String,
    val size: Long,
    val isSystemApp: Boolean,
) {

    val displayName: String by lazy {
        if (appName.trim().isEmpty()) packageName else appName
    }

    /** Lower-cased [displayName] — primary sort key. */
    val sortName: String by lazy { displayName.lowercase() }

    /** Lower-cased haystack used by the search box. */
    val searchLower: String by lazy {
        listOf(
            displayName,
            packageName,
            versionName,
            versionCode.toString(),
        ).joinToString(" ").lowercase()
    }

    val formattedSize: String by lazy {
        if (size <= 0) "Unknown size" else FormatUtil.formatBytes(size)
    }

    val versionLabel: String
        get() = "v$versionName ($versionCode)"

    companion object {
        fun fromMap(map: Map<String, Any?>): InstalledApp = InstalledApp(
            appName = map["appName"] as? String ?: "",
            packageName = map["packageName"] as? String ?: "",
            versionName = map["versionName"] as? String ?: "Unknown",
            versionCode = (map["versionCode"] as? Number)?.toLong() ?: 0L,
            iconPath = map["iconPath"] as? String,
            sourceDir = map["sourceDir"] as? String ?: "",
            size = (map["size"] as? Number)?.toLong() ?: 0L,
            isSystemApp = map["isSystemApp"] as? Boolean ?: false,
        )

        val compareByName: Comparator<InstalledApp> = Comparator { a, b ->
            val name = a.sortName.compareTo(b.sortName)
            if (name != 0) return@Comparator name
            a.packageName.compareTo(b.packageName)
        }

        val compareBySize: Comparator<InstalledApp> = Comparator { a, b ->
            val sizeCompare = a.size.compareTo(b.size)
            if (sizeCompare != 0) return@Comparator sizeCompare
            compareByName.compare(a, b)
        }
    }
}
