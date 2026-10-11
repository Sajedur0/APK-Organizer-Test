package com.apkorganizer.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/** One progress tick from the native scanner (batched + throttled). */
class ScanEvent(
    val type: String,
    val filesFound: Int,
    val currentDirectory: String,
    val apks: List<Map<String, Any?>>,
)

/**
 * Native engine for APK Organizer — a direct port of the original
 * `ApkManagerPlugin`, with the Flutter platform-channel bridge replaced by
 * suspend functions and callback events.
 *
 * Threading model (important for smoothness):
 *  - [scanDispatcher] walks the storage tree (single thread, I/O bound).
 *  - [workerPool] parses APK archives / decodes app icons in parallel.
 *  - File operations run on [Dispatchers.IO] so a long scan can never block
 *    them, and vice versa.
 *
 * Progress events are **batched and throttled** (see [ScanSession.emit]) so a
 * scan of thousands of APKs does not flood the UI.
 */
object ApkManager {

    // --- Launchers provided by MainActivity ---------------------------------
    @Volatile var manageStorageLauncher: ActivityResultLauncher<Intent>? = null
    @Volatile var runtimePermissionsLauncher: ActivityResultLauncher<Array<String>>? = null
    @Volatile var installPermissionLauncher: ActivityResultLauncher<Intent>? = null
    @Volatile var installFromInstallLauncher: ActivityResultLauncher<Intent>? = null

    /** Events for installed-package removals (package name values). */
    val packageRemovedEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 32,
    )

    @Volatile private var appContext: Context? = null
    @Volatile var activity: Activity? = null
        private set

    private val mainThreadHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // --- Executors -----------------------------------------------------------
    private val workerCount: Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    private val scanDispatcher: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "apk-scan").apply { isDaemon = true } }

    private val workerPool: ExecutorService =
        Executors.newFixedThreadPool(workerCount) { r -> Thread(r, "apk-worker").apply { isDaemon = true } }

    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO,
    )

    // --- Scan state ----------------------------------------------------------
    /** Incremented whenever a scan starts or is cancelled; stale scans abort. */
    @Volatile
    private var scanGeneration = 0

    private val parsedCount = AtomicInteger(0)
    private val discoveredCount = AtomicInteger(0)

    @Volatile
    private var activeSession: ScanSession? = null

    private var storagePermissionDeferred: CompletableDeferred<Map<String, Any?>>? = null
    private var installPermissionDeferred: CompletableDeferred<Map<String, Any?>>? = null
    private var installFromInstallDeferred: CompletableDeferred<Boolean>? = null

    private var receiverRegistered = false
    private val packageRemovedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED) {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName != null &&
                    !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                ) {
                    packageRemovedEvents.tryEmit(packageName)
                }
            }
        }
    }

    private const val PROGRESS_INTERVAL_MS = 140L
    private const val PROGRESS_BATCH_SIZE = 24
    private const val ICON_MAX_SIZE_PX = 128
    private const val ICON_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val COPY_BUFFER_SIZE = 1 shl 17
    private const val MAX_SCAN_DEPTH = 48
    private val SKIP_DIR_SUFFIXES = listOf("/Android/data", "/Android/obb")

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        // Housekeeping: drop icons of APKs/apps that were removed long ago.
        appScope.launch { pruneIconCache() }
    }

    fun attach(
        activity: Activity,
        manageStorage: ActivityResultLauncher<Intent>,
        runtimePermissions: ActivityResultLauncher<Array<String>>,
        installPermission: ActivityResultLauncher<Intent>,
        installFromInstall: ActivityResultLauncher<Intent>,
    ) {
        this.activity = activity
        manageStorageLauncher = manageStorage
        runtimePermissionsLauncher = runtimePermissions
        installPermissionLauncher = installPermission
        installFromInstallLauncher = installFromInstall
    }

    fun detach(activity: Activity) {
        if (this.activity === activity) this.activity = null
    }

    fun registerPackageRemovedReceiver(activity: Activity) {
        if (receiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }
        receiverRegistered = try {
            ContextCompat.registerReceiver(
                activity,
                packageRemovedReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            true
        } catch (_: Exception) {
            try {
                activity.registerReceiver(packageRemovedReceiver, filter)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun unregisterPackageRemovedReceiver(activity: Activity) {
        if (!receiverRegistered) return
        try {
            activity.unregisterReceiver(packageRemovedReceiver)
        } catch (_: Exception) {
            // Receiver was already unregistered (e.g. process recreation).
        }
        receiverRegistered = false
    }

    private fun getContext(): Context? = activity ?: appContext

    private fun packageManager(): PackageManager? = getContext()?.packageManager

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()

    // ------------------------------------------------------------------
    // Meta
    // ------------------------------------------------------------------

    fun getJavaVersion(): String {
        val version = System.getProperty("java.version")
        return if (version.isNullOrEmpty() || version == "0") "" else version
    }

    // ------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------

    private fun isSkippedDir(dir: File, depth: Int): Boolean {
        if (depth > MAX_SCAN_DEPTH) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val path = dir.absolutePath
        for (suffix in SKIP_DIR_SUFFIXES) {
            if (path.endsWith(suffix) || path.contains("$suffix/")) return true
        }
        return false
    }

    /** Cancels a running scan: the in-flight scan returns its partial results. */
    fun cancelScan() {
        scanGeneration++
        activeSession?.clearBatch()
    }

    /**
     * Starts a full-storage scan, streaming batched progress events to
     * [onEvent] (delivered on the caller's dispatcher) while it runs.
     *
     * The returned list is complete unless the scan was cancelled through
     * [cancelScan], in which case it contains everything found so far.
     */
    suspend fun scanAllStorage(onEvent: (ScanEvent) -> Unit): List<Map<String, Any?>> =
        coroutineScope {
            val generation = ++scanGeneration
            resetScanState()
            val channel = Channel<ScanEvent>(Channel.UNLIMITED)
            val producer = async(Dispatchers.IO) {
                try {
                    runNativeScan(generation) { channel.trySend(it) }
                } finally {
                    channel.close()
                }
            }
            val consumer = launch {
                for (event in channel) onEvent(event)
            }
            val result = producer.await()
            consumer.join()
            result
        }

    /** Blocking scan implementation; runs on [scanDispatcher]. */
    private fun runNativeScan(
        generation: Int,
        emitEvent: (ScanEvent) -> Unit,
    ): List<Map<String, Any?>> {
        val session = ScanSession(generation, emitEvent)
        activeSession = session
        val iconCacheDir = getIconCacheDir()
        val root = Environment.getExternalStorageDirectory()
        try {
            emitEvent(ScanEvent("start", 0, root.absolutePath, emptyList()))
            val candidates = collectApkFiles(root, session)
            val parsed = if (candidates.isEmpty()) {
                emptyList()
            } else {
                parseApksParallel(candidates, session, iconCacheDir)
            }
            session.emit(force = true)
            emitEvent(ScanEvent("complete", parsed.size, "", emptyList()))
            return parsed
        } catch (e: Exception) {
            emitEvent(
                ScanEvent("error", parsedCount.get(), e.message ?: "Unknown error", emptyList()),
            )
            throw ApkManagerException("Failed to scan APK files: ${e.message}")
        }
    }

    /** One-shot scan without progress events (kept for API compatibility). */
    suspend fun scanApkFiles(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val generation = ++scanGeneration
        resetScanState()
        try {
            val root = Environment.getExternalStorageDirectory()
            val candidates = collectApkFiles(root, ScanSession(generation) {})
            if (candidates.isEmpty()) {
                emptyList()
            } else {
                parseApksParallel(candidates, null, getIconCacheDir())
            }
        } catch (e: Exception) {
            throw ApkManagerException("Failed to scan APK files: ${e.message}")
        }
    }

    private fun resetScanState() {
        parsedCount.set(0)
        discoveredCount.set(0)
        activeSession = null
    }

    /**
     * Walks [root] iteratively (no recursion → no stack overflow on deep
     * trees) and collects every readable `.apk` file. Publishes discovery
     * progress.
     */
    private fun collectApkFiles(root: File, session: ScanSession): List<File> {
        val found = ArrayList<File>(128)
        if (!root.exists() || !root.isDirectory) return found

        val queue = ArrayDeque<Pair<File, Int>>()
        queue.addLast(root to 0)
        var dirsSinceReport = 0

        while (queue.isNotEmpty()) {
            if (!session.isActive) break
            val (dir, depth) = queue.removeFirst()
            if (isSkippedDir(dir, depth)) continue

            dirsSinceReport++
            if (dirsSinceReport >= 16) {
                dirsSinceReport = 0
                session.emit(dir = dir.absolutePath, discovered = true)
            }

            val children = try {
                dir.listFiles()
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
            if (children == null) continue

            for (child in children) {
                val name = child.name
                if (child.isDirectory) {
                    if (name != "." && name != "..") queue.addLast(child to (depth + 1))
                } else if (child.isFile && name.length > 4 && name.endsWith(".apk", ignoreCase = true)) {
                    if (child.length() > 0L) {
                        found.add(child)
                        discoveredCount.incrementAndGet()
                    }
                }
            }
        }
        return found
    }

    /**
     * Parses [files] in parallel across [workerPool], emitting batched
     * progress. Stops early (returning partial results) when cancelled.
     */
    private fun parseApksParallel(
        files: List<File>,
        session: ScanSession?,
        iconCacheDir: File,
    ): List<Map<String, Any?>> {
        if (files.isEmpty()) return emptyList()
        val parsed = ArrayList<Map<String, Any?>>(files.size)

        mapParallel(files) { file ->
            if (session != null && !session.isActive) return@mapParallel null
            try {
                val info = parseApkInfo(file, iconCacheDir)
                if (session != null) session.emit(apk = info) else parsedCount.incrementAndGet()
                info
            } catch (_: Exception) {
                // Unreadable / corrupt APK — skip instead of failing the scan.
                null
            }
        }.forEach { info ->
            if (info != null) parsed.add(info)
        }
        return parsed
    }

    /**
     * Runs [transform] over [items] in parallel on [workerPool].
     *
     * Workers pull items from a shared cursor (dynamic load balancing) and
     * results are collected in a synchronized list. Order is not preserved —
     * every caller sorts or aggregates afterwards.
     */
    private fun <T, R> mapParallel(items: List<T>, transform: (T) -> R?): List<R> {
        if (items.isEmpty()) return emptyList()
        val workerTotal = minOf(workerCount, items.size)
        val cursor = AtomicInteger(0)
        val out = Collections.synchronizedList(ArrayList<R>(items.size))
        val latch = CountDownLatch(workerTotal)

        repeat(workerTotal) {
            workerPool.execute {
                try {
                    while (true) {
                        val index = cursor.getAndIncrement()
                        if (index >= items.size) break
                        val value = try {
                            transform(items[index])
                        } catch (_: Exception) {
                            null
                        }
                        if (value != null) out.add(value)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        try {
            latch.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return ArrayList(out)
    }

    /** State of one running scan: batching/throttling of progress events. */
    private class ScanSession(
        val generation: Int,
        private val emitEvent: (ScanEvent) -> Unit,
    ) {
        val isActive: Boolean
            get() = generation == scanGeneration

        private val lock = Any()
        private val pendingBatch = ArrayList<Map<String, Any?>>(64)
        private var lastProgressAt = 0L
        private var currentDir = ""

        fun clearBatch() {
            synchronized(lock) { pendingBatch.clear() }
        }

        fun emit(
            apk: Map<String, Any?>? = null,
            dir: String? = null,
            discovered: Boolean = false,
            force: Boolean = false,
        ) {
            var flush = force
            var batch: List<Map<String, Any?>>? = null
            var count = 0
            synchronized(lock) {
                if (apk != null) {
                    pendingBatch.add(apk)
                    parsedCount.incrementAndGet()
                }
                if (!dir.isNullOrEmpty()) currentDir = dir
                if (!flush) {
                    val now = System.currentTimeMillis()
                    flush = pendingBatch.size >= PROGRESS_BATCH_SIZE ||
                        (now - lastProgressAt) >= PROGRESS_INTERVAL_MS
                }
                if (flush) {
                    lastProgressAt = System.currentTimeMillis()
                    if (pendingBatch.isNotEmpty()) {
                        batch = ArrayList(pendingBatch)
                        pendingBatch.clear()
                    }
                    count = if (discovered) discoveredCount.get() else parsedCount.get()
                }
            }
            if (flush) {
                emitEvent(ScanEvent("progress", count, currentDir, batch ?: emptyList()))
            }
        }
    }

    private fun parseApkInfo(file: File, iconCacheDir: File): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        result["fileName"] = file.name
        result["path"] = file.absolutePath
        result["size"] = file.length()
        result["lastModified"] = file.lastModified()

        val pm = packageManager()
        if (pm == null) {
            result.setFallbackInfo(file)
            return result
        }

        // Lightweight parse: the list only needs label/package/version, so we
        // skip metadata + signature extraction (a large per-file speed up).
        val packageInfo = try {
            getArchivePackageInfo(pm, file.absolutePath, 0)
        } catch (_: Exception) {
            null
        }

        if (packageInfo == null) {
            result.setFallbackInfo(file)
            return result
        }

        val appInfo = packageInfo.applicationInfo
        if (appInfo != null) {
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            result["appName"] = try {
                appInfo.loadLabel(pm).toString()
            } catch (_: Exception) {
                file.nameWithoutExtension
            }
            try {
                loadIconFile(appInfo, pm, iconCacheDir, file.nameWithoutExtension, file.lastModified())
                    ?.absolutePath
                    ?.let { result["iconPath"] = it }
            } catch (_: Exception) {
            }
        } else {
            result.setFallbackInfo(file)
        }

        result["packageName"] = packageInfo.packageName ?: "unknown"
        result["versionName"] = packageInfo.versionName ?: "Unknown"
        result["versionCode"] = versionCodeOf(packageInfo)
        return result
    }

    private fun MutableMap<String, Any?>.setFallbackInfo(file: File) {
        this["appName"] = file.nameWithoutExtension
        this["packageName"] = "unknown"
        this["versionName"] = "Unknown"
        this["versionCode"] = 0L
    }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(
        pm: PackageManager,
        path: String,
        flags: Int,
    ): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(path, flags)
        }

    // ------------------------------------------------------------------
    // Icon cache
    // ------------------------------------------------------------------

    private fun getIconCacheDir(): File {
        val base = getContext()?.cacheDir
        val dir = if (base != null) File(base, "apk_icons") else File("apk_icons")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun iconPrefix(baseName: String): String =
        baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64) + "_"

    private fun iconFileFor(cacheDir: File, baseName: String, stamp: Long): File =
        File(cacheDir, "${iconPrefix(baseName)}$stamp" + "_icon.png")

    /** Removes icons that have not been used for [ICON_CACHE_MAX_AGE_MS]. */
    private fun pruneIconCache() {
        try {
            val dir = getIconCacheDir()
            val cutoff = System.currentTimeMillis() - ICON_CACHE_MAX_AGE_MS
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Loads an app/APK icon and writes a downscaled PNG into the cache.
     *
     * The cache key contains [stamp] (APK mtime / package update time), so a
     * cached icon is reused without decoding when nothing changed.
     */
    private fun loadIconFile(
        appInfo: ApplicationInfo,
        pm: PackageManager,
        cacheDir: File,
        baseName: String,
        stamp: Long,
    ): File? {
        val target = iconFileFor(cacheDir, baseName, stamp)
        if (target.exists() && target.length() > 0L) return target
        val drawable: Drawable = try {
            appInfo.loadIcon(pm)
        } catch (_: Exception) {
            return null
        }
        return writeIcon(drawable, target)
    }

    private fun writeIcon(drawable: Drawable, target: File): File? = try {
        val intrinsicW = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else ICON_MAX_SIZE_PX
        val intrinsicH = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else ICON_MAX_SIZE_PX
        val longest = maxOf(intrinsicW, intrinsicH)
        val scale = if (longest > ICON_MAX_SIZE_PX) ICON_MAX_SIZE_PX.toFloat() / longest else 1f
        val outW = maxOf(1, (intrinsicW * scale).toInt())
        val outH = maxOf(1, (intrinsicH * scale).toInt())

        // Always draw into a fresh, small bitmap: never mutate the (possibly
        // shared / hardware backed) bitmap owned by the drawable.
        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, outW, outH)
        drawable.draw(canvas)

        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        target
    } catch (_: Exception) {
        try {
            target.delete()
        } catch (_: Exception) {
        }
        null
    }

    // ------------------------------------------------------------------
    // File name helpers
    // ------------------------------------------------------------------

    fun sanitizeFileName(name: String, maxLen: Int = 120): String {
        val cleaned = name.trim()
            .replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1F]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('.', '_', ' ')
        return if (cleaned.isBlank()) "unknown" else cleaned.take(maxLen)
    }

    private fun uniqueFile(directory: File?, name: String, originalPath: String? = null): File {
        val dir = directory ?: Environment.getExternalStorageDirectory()
        val baseName = sanitizeFileName(name.substringBeforeLast(".", name))
        val ext = name.substringAfterLast(".", "")
        var candidate = File(dir, if (ext.isBlank()) baseName else "$baseName.$ext")
        if (candidate.absolutePath == originalPath) return candidate
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isBlank()) "${baseName}_$counter" else "${baseName}_$counter.$ext")
            counter++
        }
        return candidate
    }

    // ------------------------------------------------------------------
    // File operations
    // ------------------------------------------------------------------

    /** Copies [source] to [dest] with a large buffer; verifies the result. */
    private fun copyFile(source: File, dest: File): Boolean {
        return try {
            source.inputStream().use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                    output.flush()
                }
            }
            dest.exists() && dest.length() == source.length()
        } catch (_: Exception) {
            try {
                dest.delete()
            } catch (_: Exception) {
            }
            false
        }
    }

    suspend fun renameApk(apkPath: String, newName: String): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(apkPath)
                if (!file.exists()) {
                    throw IllegalArgumentException("APK file not found: $apkPath")
                }

                val finalName = sanitizeFileName(newName).let {
                    if (it.endsWith(".apk", true)) it else "$it.apk"
                }

                val parentDir = file.parentFile
                    ?: throw IllegalArgumentException("Invalid parent directory")
                val newFile = uniqueFile(parentDir, finalName, file.absolutePath)

                if (newFile.absolutePath == file.absolutePath) {
                    return@withContext mapOf(
                        "success" to true,
                        "oldPath" to apkPath,
                        "newPath" to file.absolutePath,
                        "newName" to file.name,
                    )
                }

                if (file.renameTo(newFile)) {
                    return@withContext mapOf(
                        "success" to true,
                        "oldPath" to apkPath,
                        "newPath" to newFile.absolutePath,
                        "newName" to newFile.name,
                    )
                }

                // Fallback: copy and delete (different filesystem / bind mount).
                if (!copyFile(file, newFile)) {
                    throw IllegalStateException("copy verification failed")
                }
                if (file.delete()) {
                    mapOf(
                        "success" to true,
                        "oldPath" to apkPath,
                        "newPath" to newFile.absolutePath,
                        "newName" to newFile.name,
                    )
                } else {
                    // Keep storage consistent: roll the copy back so the caller
                    // does not end up with two identical files.
                    newFile.delete()
                    throw IllegalStateException("Failed to delete original file after copy")
                }
            } catch (e: Exception) {
                throw ApkManagerException("Failed to rename APK: ${e.message}")
            }
        }

    suspend fun moveApk(sourcePath: String, destDir: String): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            try {
                val source = File(sourcePath)
                if (!source.exists()) {
                    throw IllegalArgumentException("Source file not found: $sourcePath")
                }

                val destDirFile = File(destDir)
                if (!destDirFile.exists() && !destDirFile.mkdirs()) {
                    throw IllegalArgumentException("Failed to create destination: $destDir")
                }

                if (destDirFile.absolutePath == source.parentFile?.absolutePath) {
                    // Nothing to do — report it so the UI does not claim a move.
                    return@withContext mapOf(
                        "success" to true,
                        "sourcePath" to sourcePath,
                        "destPath" to source.absolutePath,
                        "conflictResolved" to false,
                        "skipped" to true,
                    )
                }

                val destFile = uniqueFile(destDirFile, source.name, source.absolutePath)
                val conflictResolved = destFile.name != source.name

                if (source.renameTo(destFile)) {
                    return@withContext mapOf(
                        "success" to true,
                        "sourcePath" to sourcePath,
                        "destPath" to destFile.absolutePath,
                        "conflictResolved" to conflictResolved,
                        "skipped" to false,
                    )
                }

                // Fallback: copy and delete (e.g. moving to another volume).
                if (!copyFile(source, destFile)) {
                    throw IllegalStateException("copy verification failed")
                }
                if (source.delete()) {
                    mapOf(
                        "success" to true,
                        "sourcePath" to sourcePath,
                        "destPath" to destFile.absolutePath,
                        "conflictResolved" to conflictResolved,
                        "skipped" to false,
                    )
                } else {
                    destFile.delete()
                    throw IllegalStateException("Failed to delete source file after copy")
                }
            } catch (e: Exception) {
                throw ApkManagerException("Failed to move APK: ${e.message}")
            }
        }

    /**
     * Deletes an APK.
     *
     * Throws [ApkManagerException] when the file could not actually be
     * removed, so callers never drop an entry from the list while it still
     * exists on disk.
     */
    suspend fun deleteApk(apkPath: String): Unit = withContext(Dispatchers.IO) {
        try {
            val file = File(apkPath)
            if (!file.exists()) {
                throw ApkManagerException("APK file not found: $apkPath")
            }
            val deleted = file.delete()
            if (deleted) {
                return@withContext
            }
            throw ApkManagerException(
                "Could not delete the file. Check storage permission and try again.",
            )
        } catch (e: ApkManagerException) {
            throw e
        } catch (e: Exception) {
            throw ApkManagerException("Failed to delete APK: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Install / uninstall
    // ------------------------------------------------------------------

    suspend fun installApk(apkPath: String): Map<String, Any?> {
        val act = activity ?: throw ApkManagerException("Failed to install APK: Activity not available")
        val file = File(apkPath)
        if (!file.exists()) {
            throw ApkManagerException("Failed to install APK: APK file not found: $apkPath")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !act.packageManager.canRequestPackageInstalls()
        ) {
            val granted = awaitInstallFromSettings(act)
            if (!granted) return mapOf("status" to "redirected_to_settings")
        }

        startInstallIntent(act, file)
        return mapOf("status" to "install_started")
    }

    private fun startInstallIntent(act: Activity, file: File) {
        try {
            val uri = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", file)
            act.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            throw ApkManagerException("Failed to install APK: ${e.message}")
        }
    }

    /**
     * Opens the "install unknown apps" settings screen and resolves once the
     * user returns, reporting whether the permission is now granted.
     */
    private suspend fun awaitInstallFromSettings(act: Activity): Boolean {
        val launcher = installFromInstallLauncher
            ?: throw ApkManagerException("Failed to install APK: Activity not available")
        if (installFromInstallDeferred != null) {
            throw ApkManagerException("Failed to install APK: request already in progress")
        }
        val deferred = CompletableDeferred<Boolean>()
        installFromInstallDeferred = deferred
        try {
            launcher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${act.packageName}")
                },
            )
        } catch (e: Exception) {
            installFromInstallDeferred = null
            throw ApkManagerException("Failed to install APK: ${e.message}")
        }
        return deferred.await()
    }

    fun onInstallFromInstallResult() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.packageManager?.canRequestPackageInstalls() ?: false
        } else {
            true
        }
        installFromInstallDeferred?.complete(granted)
        installFromInstallDeferred = null
    }

    fun uninstallPackage(packageName: String): Map<String, Any?> {
        val act = activity
            ?: throw ApkManagerException("Failed to uninstall app: Activity not available")
        if (packageName == act.packageName) {
            throw ApkManagerException("Failed to uninstall app: Cannot uninstall self")
        }
        return try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
            }
            if (intent.resolveActivity(act.packageManager) != null) {
                act.startActivity(intent)
                mapOf("status" to "uninstall_requested", "packageName" to packageName)
            } else {
                throw ApkManagerException("Failed to uninstall app: No handler for uninstall")
            }
        } catch (e: ApkManagerException) {
            throw e
        } catch (e: Exception) {
            throw ApkManagerException("Failed to uninstall app: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            getContext()?.let { ctx ->
                ContextCompat.checkSelfPermission(
                    ctx,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        ctx,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
            } ?: false
        }
    }

    fun canInstallPackages(): Boolean {
        val act = activity ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            act.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    suspend fun requestStoragePermission(): Map<String, Any?> {
        val act = activity
            ?: throw ApkManagerException("Failed to request permission: Activity not available")
        if (storagePermissionDeferred != null) {
            throw ApkManagerException(
                "Failed to request permission: Permission request already in progress",
            )
        }
        val deferred = CompletableDeferred<Map<String, Any?>>()
        storagePermissionDeferred = deferred
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val launcher = manageStorageLauncher
                    ?: throw ApkManagerException("Failed to request permission: Activity not available")
                val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${act.packageName}")
                }
                try {
                    launcher.launch(appIntent)
                } catch (_: Exception) {
                    // Some OEM builds only expose the global "all files" screen.
                    launcher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                val launcher = runtimePermissionsLauncher
                    ?: throw ApkManagerException("Failed to request permission: Activity not available")
                launcher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ),
                )
            }
        } catch (e: ApkManagerException) {
            storagePermissionDeferred = null
            throw e
        } catch (e: Exception) {
            storagePermissionDeferred = null
            throw ApkManagerException("Failed to request permission: ${e.message}")
        }
        return deferred.await()
    }

    private fun completeStoragePermission(granted: Boolean) {
        storagePermissionDeferred?.complete(
            mapOf(
                "granted" to granted,
                "status" to if (granted) "granted" else "denied",
            ),
        )
        storagePermissionDeferred = null
    }

    fun onManageStorageResult() {
        completeStoragePermission(checkStoragePermission())
    }

    fun onRuntimePermissionsResult(grants: Map<String, Boolean>) {
        val granted = grants.values.all { it } && checkStoragePermission()
        completeStoragePermission(granted)
    }

    suspend fun requestInstallPermission(): Map<String, Any?> {
        val act = activity
            ?: throw ApkManagerException(
                "Failed to request install permission: Activity not available",
            )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            act.packageManager.canRequestPackageInstalls()
        ) {
            return mapOf("granted" to true, "status" to "granted")
        }
        if (installPermissionDeferred != null) {
            throw ApkManagerException(
                "Failed to request install permission: Permission request already in progress",
            )
        }
        val deferred = CompletableDeferred<Map<String, Any?>>()
        installPermissionDeferred = deferred
        try {
            val launcher = installPermissionLauncher
                ?: throw ApkManagerException(
                    "Failed to request install permission: Activity not available",
                )
            launcher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${act.packageName}")
                },
            )
        } catch (e: ApkManagerException) {
            installPermissionDeferred = null
            throw e
        } catch (e: Exception) {
            installPermissionDeferred = null
            throw ApkManagerException(
                "Failed to request install permission: ${e.message}",
            )
        }
        return deferred.await()
    }

    fun onInstallPermissionResult() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.packageManager?.canRequestPackageInstalls() ?: false
        } else {
            true
        }
        installPermissionDeferred?.complete(
            mapOf(
                "granted" to granted,
                "status" to if (granted) "granted" else "denied",
            ),
        )
        installPermissionDeferred = null
    }

    // ------------------------------------------------------------------
    // Directories
    // ------------------------------------------------------------------

    /**
     * Storage roots for the directory picker: internal storage first, then
     * any removable/secondary volumes, then the top level folders of the
     * primary volume.
     */
    suspend fun getDirectories(): List<DirectoryEntry> = withContext(Dispatchers.IO) {
        try {
            val dirs = LinkedHashMap<String, DirectoryEntry>()
            val primary = Environment.getExternalStorageDirectory()
            dirs[primary.absolutePath] =
                DirectoryEntry("Internal Storage", primary.absolutePath)

            try {
                getContext()?.getExternalFilesDirs(null)
                    ?.filterNotNull()
                    ?.forEach { filesDir ->
                        val root = storageRootOf(filesDir.absolutePath) ?: return@forEach
                        if (!dirs.containsKey(root)) {
                            dirs[root] = DirectoryEntry(storageLabel(root), root)
                        }
                    }
            } catch (_: Exception) {
            }

            try {
                primary.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedBy { it.name.lowercase() }
                    ?.forEach {
                        dirs.putIfAbsent(it.absolutePath, DirectoryEntry(it.name, it.absolutePath))
                    }
            } catch (_: Exception) {
            }

            dirs.values.toList()
        } catch (e: Exception) {
            throw ApkManagerException("Failed to load directories: ${e.message}")
        }
    }

    /** `/storage/XXXX-XXXX/Android/data/<pkg>/files` → `/storage/XXXX-XXXX`. */
    private fun storageRootOf(path: String): String? {
        val index = path.indexOf("/Android/")
        return if (index > 0) path.substring(0, index) else null
    }

    private fun storageLabel(root: String): String {
        val fallback = root.substringAfterLast('/').ifEmpty { root }
        return try {
            if (Environment.isExternalStorageRemovable(File(root))) "SD Card" else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    suspend fun getSubdirectories(parentPath: String): List<DirectoryEntry> =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(parentPath)
                if (!dir.exists() || !dir.isDirectory) {
                    throw IllegalStateException("Directory does not exist")
                }
                dir.listFiles()
                    ?.filter { it.isDirectory && it.canRead() }
                    ?.sortedBy { it.name.lowercase() }
                    ?.map { DirectoryEntry(it.name, it.absolutePath) }
                    ?: emptyList()
            } catch (e: ApkManagerException) {
                throw e
            } catch (e: Exception) {
                throw ApkManagerException("Failed to load directories: ${e.message}")
            }
        }

    suspend fun createDirectory(parentPath: String, folderName: String): DirectoryEntry =
        withContext(Dispatchers.IO) {
            try {
                val parent = File(parentPath)
                if (!parent.exists() || !parent.isDirectory) {
                    throw IllegalArgumentException("Parent directory does not exist")
                }
                val safeName = sanitizeFileName(folderName, 80).replace(Regex("\\s+"), " ")
                if (safeName.isBlank()) {
                    throw IllegalArgumentException("Folder name is required")
                }
                val newDir = File(parent, safeName)
                if (!newDir.exists() && !newDir.mkdirs()) {
                    throw IllegalStateException("Could not create folder")
                }
                DirectoryEntry(newDir.name, newDir.absolutePath)
            } catch (e: Exception) {
                throw ApkManagerException("Failed to create directory: ${e.message}")
            }
        }

    suspend fun scanDirectoryForApks(dirPath: String): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) {
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext emptyList<Map<String, Any?>>()
            }
            val iconCacheDir = getIconCacheDir()
            dir.listFiles()
                ?.filter { it.isFile && it.name.length > 4 && it.name.endsWith(".apk", ignoreCase = true) }
                ?.sortedBy { it.name.lowercase() }
                ?.mapNotNull { file ->
                    try {
                        parseApkInfo(file, iconCacheDir)
                    } catch (_: Exception) {
                        null
                    }
                }
                ?: emptyList()
        }

    suspend fun detectDuplicates(filePaths: List<String>): List<List<String>> =
        withContext(Dispatchers.IO) {
            val pm = packageManager()
                ?: throw ApkManagerException("Context not available")

            // Computing each file's duplicate key means parsing the archive —
            // the expensive part — so it runs across the worker pool instead
            // of sequentially; grouping afterwards is a cheap O(n) pass.
            val keyed = mapParallel(filePaths) { path ->
                val file = File(path).takeIf { it.exists() } ?: return@mapParallel null
                path to duplicateKeyFor(pm, file)
            }

            val groups = LinkedHashMap<String, MutableList<String>>()
            for ((path, key) in keyed) {
                groups.getOrPut(key) { mutableListOf() }.add(path)
            }
            groups.values.filter { it.size > 1 }
        }

    /** Duplicate identity of one APK file (same rules as `ApkFile.duplicateIdentity`). */
    private fun duplicateKeyFor(pm: PackageManager, file: File): String {
        val path = file.absolutePath
        var pkgName = "unknown"
        var appName: String
        var versionName: String
        var versionCode = 0L

        try {
            val info = runCatching { getArchivePackageInfo(pm, path, 0) }.getOrNull()
            if (info != null) {
                pkgName = info.packageName ?: "unknown"
                info.applicationInfo?.let { ai ->
                    ai.sourceDir = path
                    ai.publicSourceDir = path
                    appName = ai.loadLabel(pm).toString()
                } ?: run { appName = file.nameWithoutExtension }
                versionName = info.versionName ?: "Unknown"
                versionCode = versionCodeOf(info)
            } else {
                appName = file.nameWithoutExtension
                versionName = "Unknown"
            }
        } catch (_: Exception) {
            appName = file.nameWithoutExtension
            versionName = "Unknown"
        }

        val versionPart =
            if (versionCode > 0L) versionCode.toString() else versionName.lowercase()
        return if (pkgName != "unknown") {
            "pkg:${pkgName.lowercase()}|version:$versionPart"
        } else {
            "fallback:${appName.lowercase()}|${versionName.lowercase()}"
        }
    }

    // ------------------------------------------------------------------
    // APK detail
    // ------------------------------------------------------------------

    suspend fun getApkDetail(path: String): ApkDetailInfo = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) throw IllegalArgumentException("File not found: $path")

            val ctx = getContext() ?: throw IllegalStateException("Context not available")
            val pm = ctx.packageManager ?: throw IllegalStateException("PackageManager not available")

            @Suppress("DEPRECATION")
            val flags = PackageManager.GET_META_DATA or
                PackageManager.GET_PERMISSIONS or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                })

            val packageInfo = getArchivePackageInfo(pm, file.absolutePath, flags)
                ?: throw ApkManagerException("Failed to parse APK file")

            val permissions = packageInfo.requestedPermissions?.mapIndexed { index, perm ->
                ApkPermission(
                    name = perm,
                    granted = packageInfo.requestedPermissionsFlags
                        ?.getOrNull(index)
                        ?.let { it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
                        ?: false,
                )
            } ?: emptyList()

            var appName = file.nameWithoutExtension
            var minSdk = 1
            var targetSdk = 1
            var iconPath: String? = null
            val appInfo = packageInfo.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = file.absolutePath
                appInfo.publicSourceDir = file.absolutePath
                appName = try {
                    appInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    file.nameWithoutExtension
                }
                @Suppress("DEPRECATION")
                run {
                    minSdk = appInfo.minSdkVersion
                    targetSdk = appInfo.targetSdkVersion
                }
                try {
                    iconPath = loadIconFile(
                        appInfo,
                        pm,
                        getIconCacheDir(),
                        file.nameWithoutExtension,
                        file.lastModified(),
                    )?.absolutePath
                } catch (_: Exception) {
                }
            }

            val abis = mutableListOf<String>()
            try {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    val libDirs = sortedSetOf<String>()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (name.startsWith("lib/")) {
                            val parts = name.split("/")
                            if (parts.size >= 2 && parts[1].isNotEmpty() && !parts[1].contains(".")) {
                                libDirs.add(parts[1])
                            }
                        }
                    }
                    abis.addAll(libDirs)
                }
            } catch (_: Exception) {
            }

            val signatureHash: String? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val signingInfo = packageInfo.signingInfo
                    val certs = signingInfo?.apkContentsSigners
                    if (certs != null && certs.isNotEmpty()) {
                        val digest = MessageDigest.getInstance("SHA-256")
                        for (cert in certs) {
                            digest.update(cert.toByteArray())
                        }
                        bytesToHex(digest.digest())
                    } else {
                        null
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val sigs = packageInfo.signatures
                    if (sigs != null && sigs.isNotEmpty()) {
                        val digest = MessageDigest.getInstance("SHA-256")
                        for (sig in sigs) {
                            digest.update(sig.toByteArray())
                        }
                        bytesToHex(digest.digest())
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }

            ApkDetailInfo(
                packageName = packageInfo.packageName ?: "",
                versionName = packageInfo.versionName ?: "Unknown",
                versionCode = versionCodeOf(packageInfo),
                appName = appName,
                minSdkVersion = minSdk,
                targetSdkVersion = targetSdk,
                supportedAbis = abis,
                signatureHash = signatureHash,
                iconPath = iconPath,
                fileSize = file.length(),
                fileName = file.name,
                filePath = file.absolutePath,
                permissions = permissions,
            )
        } catch (e: ApkManagerException) {
            throw e
        } catch (e: Exception) {
            throw ApkManagerException("Failed to get APK details: ${e.message}")
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }

    // ------------------------------------------------------------------
    // Installed apps
    // ------------------------------------------------------------------

    private fun buildInstalledApp(
        pkg: PackageInfo,
        pm: PackageManager,
        iconCacheDir: File,
    ): InstalledApp? {
        val app = pkg.applicationInfo ?: return null
        val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val packageName = pkg.packageName ?: return null
        val appName = try {
            app.loadLabel(pm).toString()
        } catch (_: Exception) {
            packageName
        }
        val sourceDir = app.sourceDir ?: ""
        val icon = try {
            loadIconFile(app, pm, iconCacheDir, packageName, pkg.lastUpdateTime)
        } catch (_: Exception) {
            null
        }
        return InstalledApp(
            appName = appName,
            packageName = packageName,
            versionName = pkg.versionName ?: "Unknown",
            versionCode = versionCodeOf(pkg),
            iconPath = icon?.absolutePath,
            sourceDir = sourceDir,
            size = if (sourceDir.isNotEmpty()) File(sourceDir).length() else 0L,
            isSystemApp = isSystem,
        )
    }

    suspend fun getInstalledApps(includeSystem: Boolean): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            try {
                val ctx = getContext()
                    ?: throw IllegalStateException("Context not available")
                val pm = ctx.packageManager
                    ?: throw IllegalStateException("PackageManager not available")

                @Suppress("DEPRECATION")
                val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getInstalledPackages(0)
                }

                val selfPackage = ctx.packageName
                val iconCacheDir = getIconCacheDir()

                // Filter before doing any expensive work (labels/icons).
                val selected = packages.filter { pkg ->
                    val app = pkg.applicationInfo ?: return@filter false
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    includeSystem == isSystem && pkg.packageName != selfPackage
                }

                // Labels + icons in parallel: this is what made the list slow
                // to appear on devices with many apps.
                mapParallel(selected) { pkg -> buildInstalledApp(pkg, pm, iconCacheDir) }
                    .filterNotNull()
                    .sortedBy { it.appName.lowercase() }
            } catch (e: ApkManagerException) {
                throw e
            } catch (e: Exception) {
                throw ApkManagerException("Failed to load installed apps: ${e.message}")
            }
        }

    suspend fun backupInstalledApp(packageName: String, destDir: String): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            try {
                val ctx = getContext() ?: throw IllegalStateException("Context not available")
                val pm = ctx.packageManager ?: throw IllegalStateException("Context not available")

                @Suppress("DEPRECATION")
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getPackageInfo(packageName, 0)
                }

                val ai = pkgInfo.applicationInfo
                    ?: throw IllegalStateException("App info not found")
                val sourcePath = ai.sourceDir
                    ?: throw IllegalStateException("Source not found")
                val source = File(sourcePath).takeIf { it.exists() }
                    ?: throw IllegalStateException("Source file not found")

                val destDirFile = File(destDir)
                if (!destDirFile.exists() && !destDirFile.mkdirs()) {
                    throw IllegalStateException("Failed to create dest")
                }
                val label = try {
                    ai.loadLabel(pm).toString()
                } catch (_: Exception) {
                    packageName
                }
                val version = pkgInfo.versionName ?: "unknown"
                val destFile = uniqueFile(destDirFile, sanitizeFileName("${label}_$version.apk"))
                if (!copyFile(source, destFile)) {
                    throw IllegalStateException("Failed to copy APK (insufficient space?)")
                }

                mapOf(
                    "success" to true,
                    "packageName" to packageName,
                    "destPath" to destFile.absolutePath,
                    "fileName" to destFile.name,
                )
            } catch (e: Exception) {
                throw ApkManagerException("Failed to backup app: ${e.message}")
            }
        }

    // ------------------------------------------------------------------
    // Share / external navigation helpers
    // ------------------------------------------------------------------

    /**
     * Shares one or more APK files via the system share sheet.
     * Returns false when none of the files exists.
     */
    fun shareApkFiles(context: Context, paths: List<String>, text: String): Boolean {
        val uris = paths.map { File(it) }
            .filter { it.exists() }
            .map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        if (uris.isEmpty()) return false

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/vnd.android.package-archive"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(
                Intent.createChooser(intent, null)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            return true
        } catch (_: ActivityNotFoundException) {
            return false
        }
    }

    /** Opens an external URL; returns false when no handler exists. */
    fun openUrl(context: Context, url: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) {
        false
    }

    /** Shares plain text via the system share sheet. */
    fun shareText(context: Context, text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, null))
        } catch (_: Exception) {
        }
    }

}
