package com.apkorganizer.data

import com.apkorganizer.utils.FormatUtil

/**
 * Immutable description of one APK file on disk.
 *
 * ## Performance note
 * Every derived value (`displayName`, search text, sort key, suggested file
 * name, formatted size) is computed **once** and cached on the instance.
 * The caches use `PUBLICATION` mode: every value is a pure function of the
 * immutable fields, so a rare duplicate computation is harmless and avoids a
 * per-property lock object on each of thousands of instances.
 * `copyWith` returns a new instance, so the caches can never go stale.
 */
class ApkFile(
    val fileName: String,
    val path: String,
    val size: Long,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val iconPath: String? = null,
    val lastModified: Long = 0L,
) {

    fun copyWith(
        fileName: String? = null,
        path: String? = null,
        size: Long? = null,
        appName: String? = null,
        packageName: String? = null,
        versionName: String? = null,
        versionCode: Long? = null,
        iconPath: () -> String? = { this.iconPath },
        lastModified: Long? = null,
    ): ApkFile = ApkFile(
        fileName = fileName ?: this.fileName,
        path = path ?: this.path,
        size = size ?: this.size,
        appName = appName ?: this.appName,
        packageName = packageName ?: this.packageName,
        versionName = versionName ?: this.versionName,
        versionCode = versionCode ?: this.versionCode,
        iconPath = iconPath(),
        lastModified = lastModified ?: this.lastModified,
    )

    /** Name shown in the list: app label, else file name, else package name. */
    val displayName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val trimmedAppName = appName.trim()
        if (trimmedAppName.isNotEmpty()) return@lazy trimmedAppName
        val trimmedFileName = fileName.trim()
        if (trimmedFileName.isNotEmpty()) return@lazy trimmedFileName
        if (packageName == "unknown") "Unknown APK" else packageName
    }

    /** Lower-cased [displayName] — used as the primary sort key. */
    val sortName: String by lazy(LazyThreadSafetyMode.PUBLICATION) { displayName.lowercase() }

    /**
     * Lower-cased [fileName] — cached because sort comparators used to call
     * `fileName.lowercase()` on every comparison (thousands of allocations
     * while sorting a large list).
     */
    val fileNameLower: String by lazy(LazyThreadSafetyMode.PUBLICATION) { fileName.lowercase() }

    /** Lower-cased [versionName] (used by version comparisons). */
    val versionLower: String by lazy(LazyThreadSafetyMode.PUBLICATION) { versionName.lowercase() }

    /** Everything the search box matches against, already lower-cased. */
    val searchLower: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildString(displayName.length + fileName.length + packageName.length + versionName.length + 24) {
            append(displayName).append(' ')
            append(fileName).append(' ')
            append(packageName).append(' ')
            append(versionName).append(' ')
            append(versionCode)
        }.lowercase()
    }

    /** Folder that contains this file. */
    val directory: String by lazy(LazyThreadSafetyMode.PUBLICATION) { FormatUtil.parentPath(path) }

    /** True when the APK could not be parsed at all. */
    val isUnparsed: Boolean
        get() = packageName.trim().isEmpty() || packageName == "unknown"

    /**
     * Identity used for duplicate detection: same package **and** same version
     * are duplicates; different versions of an app never are.
     *
     * When the package name is unknown we also require an identical file size,
     * because two unrelated APKs with the same generic label and version
     * (`"Unknown"`) must never be grouped — deleting the wrong file is far
     * worse than missing a duplicate.
     */
    val duplicateIdentity: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val normalizedPackage = packageName.trim().lowercase()
        if (normalizedPackage.isNotEmpty() && normalizedPackage != "unknown") {
            val versionPart =
                if (versionCode > 0) versionCode.toString()
                else versionName.trim().lowercase()
            return@lazy "pkg:$normalizedPackage|version:$versionPart"
        }
        "fallback:${displayName.lowercase()}|" +
            "${versionName.trim().lowercase()}|$size"
    }

    val formattedSize: String by lazy(LazyThreadSafetyMode.PUBLICATION) { FormatUtil.formatBytes(size) }

    /** Target name for "auto rename": `AppName_VersionName.apk`. */
    val suggestedRename: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val safeAppName = sanitizeFilePart(displayName)
        val versionSource = when {
            versionName.trim().isNotEmpty() && versionName != "Unknown" -> versionName
            versionCode > 0 -> versionCode.toString()
            else -> "unknown"
        }
        val safeVersion = sanitizeFilePart(versionSource)
        "${safeAppName}_$safeVersion.apk"
    }

    /** Stem (file name without extension) of [suggestedRename]. */
    private val suggestedStem: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        suggestedRename.substring(0, suggestedRename.length - ".apk".length)
    }

    /**
     * True when the file already matches the auto-rename target.
     *
     * A numeric conflict suffix added by the native layer (`Name_v1_1.apk`)
     * also counts as "already named", which keeps "Smart Organize" idempotent:
     * a second run does no work instead of renaming files over and over.
     */
    val needsRename: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val currentStem =
            if (fileName.lowercase().endsWith(".apk")) fileName.substring(0, fileName.length - 4)
            else fileName
        val normalizedCurrent =
            if (conflictSuffix.containsMatchIn(currentStem)) {
                currentStem.substring(0, currentStem.lastIndexOf('_'))
            } else currentStem
        normalizedCurrent.lowercase() != suggestedStem.lowercase()
    }

    override fun toString(): String =
        "ApkFile(name: $appName, path: $path, size: $formattedSize)"

    companion object {
        private val unsafeFileChars = Regex("[<>:\"/\\\\|?*\\x00-\\x1F]")
        private val whitespace = Regex("\\s+")
        private val underscores = Regex("_+")
        private val edgeSeparators = Regex("""^[._\s]+|[._\s]+$""")
        private val conflictSuffix = Regex("""_\d+$""")

        fun fromMap(map: Map<String, Any?>): ApkFile = ApkFile(
            fileName = map["fileName"] as? String ?: "",
            path = map["path"] as? String ?: "",
            size = (map["size"] as? Number)?.toLong() ?: 0L,
            appName = map["appName"] as? String ?: "",
            packageName = map["packageName"] as? String ?: "unknown",
            versionName = map["versionName"] as? String ?: "Unknown",
            versionCode = (map["versionCode"] as? Number)?.toLong() ?: 0L,
            iconPath = map["iconPath"] as? String,
            lastModified = (map["lastModified"] as? Number)?.toLong() ?: 0L,
        )

        private fun sanitizeFilePart(value: String): String {
            val sanitized = value.trim()
                .replace(unsafeFileChars, "_")
                .replace(whitespace, "_")
                .replace(underscores, "_")
                .replace(edgeSeparators, "")
            if (sanitized.isEmpty()) return "unknown"
            return if (sanitized.length > 80) sanitized.substring(0, 80) else sanitized
        }

        val compareByDisplayName: Comparator<ApkFile> = Comparator { a, b ->
            val nameCompare = a.sortName.compareTo(b.sortName)
            if (nameCompare != 0) return@Comparator nameCompare
            val versionCompare = b.versionCode.compareTo(a.versionCode)
            if (versionCompare != 0) return@Comparator versionCompare
            val fileCompare = a.fileNameLower.compareTo(b.fileNameLower)
            if (fileCompare != 0) return@Comparator fileCompare
            a.path.compareTo(b.path)
        }

        /** Sorts by file size (ties broken by name so the order is stable). */
        val compareBySize: Comparator<ApkFile> = Comparator { a, b ->
            val sizeCompare = a.size.compareTo(b.size)
            if (sizeCompare != 0) return@Comparator sizeCompare
            compareByDisplayName.compare(a, b)
        }

        /** Sorts by modified date. */
        val compareByDate: Comparator<ApkFile> = Comparator { a, b ->
            val dateCompare = a.lastModified.compareTo(b.lastModified)
            if (dateCompare != 0) return@Comparator dateCompare
            compareByDisplayName.compare(a, b)
        }

        /** Sorts by version code, then by version name, then by name. */
        val compareByVersion: Comparator<ApkFile> = Comparator { a, b ->
            val codeCompare = a.versionCode.compareTo(b.versionCode)
            if (codeCompare != 0) return@Comparator codeCompare
            val versionCompare = a.versionLower.compareTo(b.versionLower)
            if (versionCompare != 0) return@Comparator versionCompare
            compareByDisplayName.compare(a, b)
        }
    }
}
