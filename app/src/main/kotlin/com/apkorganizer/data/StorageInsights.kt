package com.apkorganizer.data

import com.apkorganizer.utils.FormatUtil

/**
 * A snapshot of "smart" facts about the currently scanned APK collection.
 *
 * The home screen recomputes this in a single O(n) pass whenever the master
 * list changes (scan, rename, move, delete, organize). Everything is derived
 * from the exact same identity/keep rules as [DuplicateHandler], so the
 * "duplicate" badges in the list and the one-tap cleanup can never disagree
 * with what Smart Organize actually does.
 */
class StorageInsights(
    /** Total number of APK files found. */
    val totalFiles: Int,
    /** Combined size of every APK file in bytes. */
    val totalBytes: Long,
    /** Number of distinct apps (unique package names, fallback: label). */
    val distinctApps: Int,
    /** Duplicate groups (same package + same version, more than one file). */
    val duplicateGroups: Int,
    /** Files that are redundant copies (everything except the kept file). */
    val duplicateFiles: Int,
    /** Bytes that would be freed by removing the redundant copies. */
    val reclaimableBytes: Long,
    /**
     * Paths of the redundant copies — i.e. the files a duplicate cleanup
     * would delete. O(1) membership test for the list tiles' badges.
     */
    val duplicateCandidatePaths: Set<String>,
) {

    val hasDuplicates: Boolean
        get() = duplicateFiles > 0

    val formattedTotalSize: String by lazy { FormatUtil.formatBytes(totalBytes) }

    val formattedReclaimable: String by lazy { FormatUtil.formatBytes(reclaimableBytes) }

    companion object {

        val EMPTY = StorageInsights(
            totalFiles = 0,
            totalBytes = 0L,
            distinctApps = 0,
            duplicateGroups = 0,
            duplicateFiles = 0,
            reclaimableBytes = 0L,
            duplicateCandidatePaths = emptySet(),
        )

        /**
         * Builds insights over [files] in one pass.
         *
         * The keep-file rule mirrors `DuplicateGroup.fileToKeep` exactly:
         * newest modification time wins, then largest size, then the
         * lexicographically smallest path — so the badges always mark the
         * same files that a cleanup would remove.
         */
        fun compute(files: List<ApkFile>): StorageInsights {
            if (files.isEmpty()) return EMPTY

            var totalBytes = 0L
            val appKeys = HashSet<String>()
            val groups = LinkedHashMap<String, MutableList<ApkFile>>()

            for (file in files) {
                totalBytes += file.size
                val appKey =
                    if (file.isUnparsed) file.sortName
                    else file.packageName.trim().lowercase()
                appKeys.add(appKey)
                groups.getOrPut(file.duplicateIdentity) { mutableListOf() }.add(file)
            }

            var duplicateGroups = 0
            var duplicateFiles = 0
            var reclaimableBytes = 0L
            val candidates = HashSet<String>()

            for (group in groups.values) {
                if (group.size < 2) continue
                duplicateGroups++

                var best = group[0]
                for (i in 1 until group.size) {
                    val file = group[i]
                    val newer = file.lastModified > best.lastModified
                    val sameTime = file.lastModified == best.lastModified
                    val larger = file.size > best.size
                    val sameSize = file.size == best.size
                    if (newer || (sameTime && larger) || (sameTime && sameSize && file.path < best.path)) {
                        best = file
                    }
                }

                for (file in group) {
                    if (file.path == best.path) continue
                    candidates.add(file.path)
                    duplicateFiles++
                    reclaimableBytes += file.size
                }
            }

            return StorageInsights(
                totalFiles = files.size,
                totalBytes = totalBytes,
                distinctApps = appKeys.size,
                duplicateGroups = duplicateGroups,
                duplicateFiles = duplicateFiles,
                reclaimableBytes = reclaimableBytes,
                duplicateCandidatePaths = candidates,
            )
        }
    }
}
