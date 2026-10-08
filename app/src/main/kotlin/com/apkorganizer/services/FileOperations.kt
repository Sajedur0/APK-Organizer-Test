package com.apkorganizer.services

import com.apkorganizer.data.ApkManager
import com.apkorganizer.data.ApkManagerException
import com.apkorganizer.utils.FormatUtil
import com.apkorganizer.utils.runParallel
import java.io.File

/** Result of a single move operation. */
class MoveResult(
    val sourcePath: String,
    val destPath: String?,
    val success: Boolean,
    val conflictResolved: Boolean = false,
    /** True when the file was already inside the destination folder. */
    val skipped: Boolean = false,
    val error: String? = null,
)

/** Summary of a batch move operation. */
class BatchMoveSummary(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
    val conflictsResolved: Int,
    val results: List<MoveResult>,
) {
    val hasUndoableChanges: Boolean
        get() = results.any { it.success && !it.skipped && it.destPath != null }

    /** Human readable one-liner used by the snackbar. */
    fun describe(): String {
        val buffer = StringBuilder("$succeeded file(s) moved")
        if (skipped > 0) buffer.append(", $skipped already there")
        if (failed > 0) buffer.append(", $failed failed")
        if (conflictsResolved > 0) {
            buffer.append(", $conflictsResolved renamed to avoid conflicts")
        }
        return buffer.toString()
    }
}

/**
 * Handles moving APK files with conflict resolution.
 *
 * When a file with the same name exists at the destination, the engine
 * creates a safe suffixed file name instead of replacing existing data.
 */
class FileOperations {

    private val logger = LoggerService

    /**
     * Moves an APK file to the target directory.
     *
     * Returns [MoveResult.skipped] when the file already lives in [destDir];
     * a same-folder "move" never touches the disk.
     */
    suspend fun moveApk(sourcePath: String, destDir: String): MoveResult {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                logger.error("Move", "Source file not found", filePath = sourcePath)
                return MoveResult(
                    sourcePath = sourcePath,
                    destPath = null,
                    success = false,
                    error = "Source file not found",
                )
            }

            val normalizedDest = normalize(destDir)
            val normalizedParent = normalize(FormatUtil.parentPath(sourcePath))
            if (normalizedDest == normalizedParent) {
                return MoveResult(
                    sourcePath = sourcePath,
                    destPath = sourcePath,
                    success = true,
                    skipped = true,
                )
            }

            val destDirObj = File(destDir)
            if (!destDirObj.exists()) {
                try {
                    destDirObj.mkdirs()
                } catch (e: Exception) {
                    return MoveResult(
                        sourcePath = sourcePath,
                        destPath = null,
                        success = false,
                        error = "Could not create destination folder",
                    )
                }
            }

            logger.info("Move", "Moving to $destDir", filePath = sourcePath)
            val result = ApkManager.moveApk(sourcePath, destDir)
            val success = result["success"] as? Boolean ?: false
            val newPath = result["destPath"] as? String
            val conflictResolved = result["conflictResolved"] as? Boolean ?: false
            val skipped = result["skipped"] as? Boolean ?: false

            if (!success || newPath == null) {
                return MoveResult(
                    sourcePath = sourcePath,
                    destPath = null,
                    success = false,
                    error = "Move operation returned failure",
                )
            }

            MoveResult(
                sourcePath = sourcePath,
                destPath = newPath,
                success = true,
                conflictResolved = conflictResolved,
                skipped = skipped,
            )
        } catch (e: ApkManagerException) {
            logger.error("Move", "Failed to move: $e", filePath = sourcePath)
            MoveResult(sourcePath = sourcePath, destPath = null, success = false, error = e.message)
        } catch (e: Exception) {
            logger.error("Move", "Failed to move: $e", filePath = sourcePath)
            MoveResult(sourcePath = sourcePath, destPath = null, success = false, error = e.toString())
        }
    }

    /**
     * Batch moves APK files to a target directory with conflict handling.
     *
     * Moves run with a bounded worker pool and can report progress and be
     * cancelled; results are returned in the same order as [sourcePaths] so
     * the UI can map old paths to new ones reliably.
     */
    suspend fun batchMove(
        sourcePaths: List<String>,
        destDir: String,
        concurrency: Int = 3,
        onProgress: ((Int, Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
    ): BatchMoveSummary {
        val total = sourcePaths.size
        if (total == 0) {
            return BatchMoveSummary(0, 0, 0, 0, 0, emptyList())
        }

        logger.info("Move", "Starting batch move of $total file(s) to $destDir")

        val results = arrayOfNulls<MoveResult>(total)
        var done = 0

        runParallel(total, concurrency, isCancelled) { index ->
            results[index] = moveApk(sourcePaths[index], destDir)
            done++
            onProgress?.invoke(done, total)
        }

        val completed = (0 until total).map { i ->
            results[i] ?: MoveResult(
                sourcePath = sourcePaths[i],
                destPath = null,
                success = false,
                error = "Cancelled",
            )
        }

        var succeeded = 0
        var failed = 0
        var skipped = 0
        var conflicts = 0
        for (result in completed) {
            if (!result.success) {
                failed++
                continue
            }
            if (result.skipped) skipped++ else succeeded++
            if (result.conflictResolved) conflicts++
        }

        logger.info(
            "Move",
            "Batch move complete: $succeeded moved, $skipped skipped, " +
                "$failed failed, $conflicts conflicts resolved",
        )

        return BatchMoveSummary(
            total = total,
            succeeded = succeeded,
            failed = failed,
            skipped = skipped,
            conflictsResolved = conflicts,
            results = completed,
        )
    }

    /** Compares folder paths without a trailing separator. */
    private fun normalize(path: String): String =
        if (path.length > 1 && path.endsWith("/")) path.substring(0, path.length - 1) else path
}
