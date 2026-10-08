package com.apkorganizer.services

import com.apkorganizer.data.ApkFile
import com.apkorganizer.data.ApkManager
import com.apkorganizer.data.ApkManagerException

/**
 * One progress tick from the scanner.
 *
 * Progress arrives in **batches** (the engine throttles events), so the list
 * UI can be updated a handful of times per second instead of once per file —
 * that is what keeps the scan smooth on large storages.
 */
class ScanProgress(
    val filesFound: Int,
    val currentDirectory: String,
    val apks: List<ApkFile>,
    val isDiscovering: Boolean,
    val isComplete: Boolean,
)

class ScanResult(
    val allFiles: List<ApkFile>,
    val totalScanned: Int,
    val durationMs: Long,
    /** True when the user stopped the scan before it finished. */
    val cancelled: Boolean,
)

/** Direct port of the Dart `ScannerService`. */
class ScannerService {

    private val logger = LoggerService

    @Volatile
    private var scanning = false

    val isScanning: Boolean
        get() = scanning

    /**
     * Runs a full storage scan and reports batched progress to [onProgress].
     *
     * A second call while a scan is running is ignored. [isCancelled] is
     * polled as events arrive; when it flips to true the native scan is asked
     * to stop and the files found so far are returned.
     */
    suspend fun scanAllStorage(
        onProgress: (ScanProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): ScanResult {
        if (scanning) {
            logger.warning("Scan", "Scan already running — ignoring duplicate start")
            return ScanResult(emptyList(), 0, 0, cancelled = false)
        }

        scanning = true
        val startedAt = System.currentTimeMillis()
        var cancelRequested = false
        var wasCancelled = false
        logger.info("Scan", "Starting full storage scan")

        try {
            val files = ApkManager.scanAllStorage { event ->
                if (isCancelled() && !cancelRequested) {
                    cancelRequested = true
                    wasCancelled = true
                    ApkManager.cancelScan()
                    logger.info("Scan", "Cancellation requested")
                }

                val batch = event.apks.map { ApkFile.fromMap(it) }
                onProgress(
                    ScanProgress(
                        filesFound = event.filesFound,
                        currentDirectory = event.currentDirectory,
                        apks = batch,
                        isDiscovering = batch.isEmpty() || event.type == "start",
                        isComplete = event.type == "complete",
                    ),
                )

                if (event.type == "error") {
                    logger.error("Scan", "Native scan reported an error: ${event.currentDirectory}")
                }
            }
            val sorted = files.map { ApkFile.fromMap(it) }
                .sortedWith(ApkFile.compareByDisplayName)
            val elapsed = System.currentTimeMillis() - startedAt
            logger.info(
                "Scan",
                "Scan ${if (wasCancelled) "stopped" else "complete"}: " +
                    "${sorted.size} APK file(s) found in ${elapsed}ms",
            )
            return ScanResult(sorted, sorted.size, elapsed, wasCancelled)
        } catch (e: ApkManagerException) {
            logger.error("Scan", "Scan failed: $e")
            throw e
        } catch (e: Exception) {
            logger.error("Scan", "Scan failed: $e")
            throw e
        } finally {
            scanning = false
        }
    }

    suspend fun scanDirectory(dirPath: String): List<ApkFile> {
        logger.info("Scan", "Scanning directory: $dirPath")
        return try {
            val files = ApkManager.scanDirectoryForApks(dirPath)
                .map { ApkFile.fromMap(it) }
                .sortedWith(ApkFile.compareByDisplayName)
            logger.info("Scan", "Found ${files.size} APK(s) in $dirPath")
            files
        } catch (e: Exception) {
            logger.error("Scan", "Failed to scan directory $dirPath: $e")
            throw e
        }
    }
}
