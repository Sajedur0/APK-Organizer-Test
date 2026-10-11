package com.apkorganizer.services

import com.apkorganizer.data.ApkFile
import com.apkorganizer.data.ApkManager
import java.util.concurrent.atomic.AtomicBoolean

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

    private val scanning = AtomicBoolean(false)

    val isScanning: Boolean
        get() = scanning.get()

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
        // Atomic check-and-set: two racing callers can never both start a scan.
        if (!scanning.compareAndSet(false, true)) {
            logger.warning("Scan", "Scan already running — ignoring duplicate start")
            return ScanResult(emptyList(), 0, 0, cancelled = false)
        }

        val startedAt = System.currentTimeMillis()
        var cancelRequested = false
        var wasCancelled = false
        logger.info("Scan", "Starting full storage scan")

        // Every ApkFile instance built for a progress batch is kept here so
        // the final result list can reuse them instead of re-parsing every
        // map a second time (N allocations + cold lazy caches avoided).
        val builtByPath = HashMap<String, ApkFile>()

        try {
            val files = ApkManager.scanAllStorage { event ->
                if (isCancelled() && !cancelRequested) {
                    cancelRequested = true
                    wasCancelled = true
                    ApkManager.cancelScan()
                    logger.info("Scan", "Cancellation requested")
                }

                val batch = if (event.apks.isEmpty()) {
                    emptyList()
                } else {
                    event.apks.map { raw ->
                        ApkFile.fromMap(raw).also { builtByPath[it.path] = it }
                    }
                }
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
            val sorted = files
                .map { raw -> builtByPath[raw["path"] as? String] ?: ApkFile.fromMap(raw) }
                .sortedWith(ApkFile.compareByDisplayName)
            val elapsed = System.currentTimeMillis() - startedAt
            logger.info(
                "Scan",
                "Scan ${if (wasCancelled) "stopped" else "complete"}: " +
                    "${sorted.size} APK file(s) found in ${elapsed}ms",
            )
            return ScanResult(sorted, sorted.size, elapsed, wasCancelled)
        } catch (e: Exception) {
            logger.error("Scan", "Scan failed: $e")
            throw e
        } finally {
            scanning.set(false)
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
