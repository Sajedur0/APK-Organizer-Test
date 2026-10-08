package com.apkorganizer.services

import com.apkorganizer.data.ApkFile
import com.apkorganizer.data.ApkManager
import com.apkorganizer.data.ApkManagerException
import com.apkorganizer.utils.runParallel

/** Result of a single rename operation. */
class RenameResult(
    val originalPath: String,
    val newPath: String?,
    val newName: String?,
    val success: Boolean,
    val error: String? = null,
)

/** Summary of a batch rename operation. */
class BatchRenameSummary(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    /** Files that already matched the target naming scheme and were left alone. */
    val skipped: Int,
    val results: List<RenameResult>,
) {
    val hasUndoableChanges: Boolean
        get() = results.any { it.success && it.newPath != null && it.newName != null }
}

/** Handles renaming APK files with auto-rename and batch support. */
class RenamerService {

    private val logger = LoggerService

    /** Auto-renames a single APK to the format `AppName_VersionName.apk`. */
    suspend fun autoRename(apk: ApkFile): RenameResult {
        if (!apk.needsRename) {
            return RenameResult(
                originalPath = apk.path,
                newPath = apk.path,
                newName = apk.fileName,
                success = false,
                error = "Already named correctly",
            )
        }
        return rename(apk.path, apk.suggestedRename)
    }

    /** Renames an APK file to the given [newName]. */
    suspend fun rename(path: String, newName: String): RenameResult {
        return try {
            val result = ApkManager.renameApk(path, newName)
            val success = result["success"] as? Boolean ?: false
            val newPath = result["newPath"] as? String
            val finalName = result["newName"] as? String

            if (!success || newPath == null || finalName == null) {
                logger.warning("Rename", "Rename returned no path", filePath = path)
                return RenameResult(
                    originalPath = path,
                    newPath = null,
                    newName = null,
                    success = false,
                    error = "Rename operation did not complete",
                )
            }

            logger.info("Rename", "Renamed to $finalName", filePath = newPath)
            RenameResult(
                originalPath = path,
                newPath = newPath,
                newName = finalName,
                success = true,
            )
        } catch (e: ApkManagerException) {
            logger.error("Rename", "Failed to rename: $e", filePath = path)
            RenameResult(originalPath = path, newPath = null, newName = null, success = false, error = e.message)
        } catch (e: Exception) {
            logger.error("Rename", "Failed to rename: $e", filePath = path)
            RenameResult(originalPath = path, newPath = null, newName = null, success = false, error = e.toString())
        }
    }

    /**
     * Batch auto-renames a list of APK files using a bounded worker pool.
     *
     * Files that already follow the target naming scheme (including ones the
     * engine suffixed with `_1`) are skipped without a native call, which
     * makes repeated "Smart Organize" runs cheap and idempotent.
     *
     * [onProgress] reports (done, total) after each file, counting skipped
     * files as done so the progress dialog matches what the user sees.
     * [isCancelled] stops *starting* new renames mid-flight. A failure on a
     * single file is recorded in its [RenameResult] and does not abort the
     * batch.
     */
    suspend fun autoRenameAll(
        apks: List<ApkFile>,
        concurrency: Int = 4,
        onProgress: ((Int, Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
    ): BatchRenameSummary {
        val pending = mutableListOf<ApkFile>()
        var skipped = 0
        for (apk in apks) {
            if (apk.needsRename) pending.add(apk) else skipped++
        }

        val total = apks.size
        var done = skipped
        val results = mutableListOf<RenameResult>()

        if (pending.isNotEmpty()) {
            logger.info(
                "Rename",
                "Auto-renaming ${pending.size} of $total file(s) " +
                    "($skipped already named) with concurrency $concurrency",
            )
        }

        runParallel(pending.size, concurrency, isCancelled) { index ->
            val apk = pending[index]
            val result = rename(apk.path, apk.suggestedRename)
            results.add(result)
            done++
            onProgress?.invoke(minOf(done, total), total)
        }

        // Progress must not appear stuck for the files that were skipped.
        onProgress?.invoke(minOf(done, total), total)

        val succeeded = results.count { it.success }
        val failed = results.size - succeeded

        logger.info(
            "Rename",
            "Batch rename complete: $succeeded succeeded, $failed failed, " +
                "$skipped already named",
        )

        return BatchRenameSummary(
            total = total,
            succeeded = succeeded,
            failed = failed,
            skipped = skipped,
            results = results,
        )
    }
}
