package com.apkorganizer.services

import com.apkorganizer.data.ApkFile
import com.apkorganizer.data.ApkManager
import com.apkorganizer.data.ApkManagerException
import com.apkorganizer.utils.runParallel

class DuplicateGroup(
    val appName: String,
    val versionName: String,
    val files: List<ApkFile>,
) {
    /**
     * The file to keep: newest first, then largest, then stable path order.
     *
     * The final tiebreaker uses the full path in a deterministic order so the
     * same file is always chosen across separate `findDuplicates` /
     * `removeDuplicates` calls (avoiding deleting the wrong duplicate).
     */
    val fileToKeep: ApkFile
        get() {
            var best: ApkFile? = null
            for (file in files) {
                if (best == null) {
                    best = file
                    continue
                }
                val modified = file.lastModified.compareTo(best.lastModified)
                if (modified > 0) {
                    best = file
                    continue
                }
                if (modified == 0) {
                    val size = file.size.compareTo(best.size)
                    if (size > 0) {
                        best = file
                        continue
                    }
                    if (size == 0 && file.path < best.path) {
                        best = file
                    }
                }
            }
            return best ?: files.first()
        }

    /** Files to delete: all except the selected keep file. */
    val filesToDelete: List<ApkFile>
        get() {
            val keep = fileToKeep
            return files.filter { it.path != keep.path }
        }
}

/** Summary of a duplicate removal operation. */
class DuplicateRemovalSummary(
    val duplicateGroups: Int,
    val filesDeleted: Int,
    val filesKept: Int,
    val deletedPaths: List<String>,
    val errors: List<String>,
    /** Total size of the files that were actually deleted. */
    val bytesFreed: Long = 0L,
) {
    val formattedBytesFreed: String
        get() = com.apkorganizer.utils.FormatUtil.formatBytes(bytesFreed)
}

/** Analysis result: true duplicate groups + apps present in several versions. */
class DuplicateAnalysis(
    val duplicates: Map<String, List<ApkFile>>,
    val multiVersion: Map<String, List<ApkFile>>,
)

/**
 * Detects and removes duplicate APK files.
 *
 * Package name + version code is the safest duplicate identity. If APK
 * parsing fails, the fallback is display name + version name + size.
 * Different versions of the same package are never treated as duplicates.
 */
class DuplicateHandler {

    private val logger = LoggerService

    /**
     * Single pass over [files] building both groupings needed for duplicate
     * analysis:
     *  - [DuplicateAnalysis.duplicates]: keyed by [ApkFile.duplicateIdentity]
     *    (same package + same version) — these are true duplicates.
     *  - [DuplicateAnalysis.multiVersion]: keyed by package name / display
     *    name — used to log apps that appear in several *different* versions
     *    (never deleted).
     */
    fun analyzeApks(files: List<ApkFile>): DuplicateAnalysis {
        val byIdentity = LinkedHashMap<String, MutableList<ApkFile>>()
        val byApp = LinkedHashMap<String, MutableList<ApkFile>>()

        for (file in files) {
            byIdentity.getOrPut(file.duplicateIdentity) { mutableListOf() }.add(file)
            val key =
                if (file.isUnparsed) file.sortName
                else file.packageName.trim().lowercase()
            byApp.getOrPut(key) { mutableListOf() }.add(file)
        }

        return DuplicateAnalysis(byIdentity, byApp)
    }

    fun findDuplicates(files: List<ApkFile>): List<DuplicateGroup> {
        logger.info("Duplicates", "Scanning ${files.size} file(s) for duplicates")

        val analysis = analyzeApks(files)
        logAppsWithMultipleVersions(analysis.multiVersion)

        val duplicates = mutableListOf<DuplicateGroup>()
        for (group in analysis.duplicates.values) {
            if (group.size > 1) {
                duplicates.add(
                    DuplicateGroup(
                        appName = group.first().displayName,
                        versionName = group.first().versionName,
                        files = group,
                    ),
                )
            }
        }

        logger.info(
            "Duplicates",
            "Found ${duplicates.size} duplicate group(s) (same package + same version)",
        )

        return duplicates
    }

    suspend fun removeDuplicates(
        files: List<ApkFile>,
        concurrency: Int = 4,
        onProgress: ((Int, Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
    ): DuplicateRemovalSummary {
        // Fast path: if every file has a unique duplicate identity, no work is
        // possible. Skipping `findDuplicates` (which builds grouping maps and
        // logs multi-version apps) saves a full-list scan in the common case
        // of zero duplicates.
        val identitySeen = HashSet<String>()
        var hasDuplicates = false
        for (file in files) {
            if (!identitySeen.add(file.duplicateIdentity)) {
                hasDuplicates = true
                break
            }
        }
        if (!hasDuplicates) {
            logger.info(
                "Duplicates",
                "No duplicate identities among ${files.size} file(s); skipping removal",
            )
            return DuplicateRemovalSummary(0, 0, 0, emptyList(), emptyList(), 0L)
        }

        val groups = findDuplicates(files)
        val filesKept = groups.size
        val deletedPaths = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Flatten every file we intend to delete across all groups. The keep
        // file per group is decided up-front (deterministically) so deleting
        // files in any order is safe.
        val toDelete = mutableListOf<ApkFile>()
        for (group in groups) {
            toDelete.addAll(group.filesToDelete)
        }
        val totalDeletes = toDelete.size

        if (totalDeletes == 0) {
            return DuplicateRemovalSummary(
                groups.size, 0, filesKept, emptyList(), emptyList(),
            )
        }

        var done = 0
        // Only mutated on the caller's dispatcher (runParallel workers pull
        // indexes but resume on the same single-threaded context), matching
        // the unsynchronized `deletedPaths` accumulation above.
        var freedBytes = 0L
        runParallel(totalDeletes, concurrency, isCancelled) { index ->
            val file = toDelete[index]
            try {
                ApkManager.deleteApk(file.path)
                deletedPaths.add(file.path)
                freedBytes += file.size
            } catch (e: ApkManagerException) {
                errors.add("Failed to delete ${file.fileName}: ${e.message}")
                logger.error(
                    "Duplicates",
                    "Failed to delete duplicate: $e",
                    filePath = file.path,
                )
            }
            done++
            onProgress?.invoke(done, totalDeletes)
        }

        val summary = DuplicateRemovalSummary(
            duplicateGroups = groups.size,
            filesDeleted = deletedPaths.size,
            filesKept = filesKept,
            deletedPaths = deletedPaths,
            errors = errors,
            bytesFreed = freedBytes,
        )

        logger.info(
            "Duplicates",
            "Removal complete: ${summary.duplicateGroups} groups, " +
                "${summary.filesDeleted} deleted (${summary.formattedBytesFreed} freed), " +
                "${summary.filesKept} kept, ${summary.errors.size} error(s)",
        )

        return summary
    }

    private fun logAppsWithMultipleVersions(appGroups: Map<String, List<ApkFile>>) {
        for (entry in appGroups) {
            val versions = entry.value.map { it.versionName }.toSet()
            if (entry.value.size > 1 && versions.size > 1) {
                logger.info(
                    "Duplicates",
                    "App \"${entry.key}\" has ${entry.value.size} different versions: " +
                        "${versions.joinToString(", ")} - these will NOT be deleted",
                )
            }
        }
    }
}
