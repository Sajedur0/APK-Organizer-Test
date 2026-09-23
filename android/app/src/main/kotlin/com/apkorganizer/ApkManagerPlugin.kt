package com.apkorganizer

import android.app.Activity
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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/**
 * Native bridge for APK Organizer.
 *
 * Threading model (important for smoothness):
 *  - [coordinatorExecutor] walks the storage tree (single thread, I/O bound).
 *  - [workerPool] parses APK archives / decodes app icons in parallel.
 *  - [ioExecutor] runs file operations (rename / move / delete / backup) so a
 *    long scan can never block them, and vice versa.
 *  - All Flutter `Result` callbacks are posted back on the main thread.
 *
 * Progress events are **batched and throttled** (see [emitProgress]) so a scan
 * of thousands of APKs does not flood the platform channel — that was the main
 * cause of jank during scanning.
 */
class ApkManagerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener, PluginRegistry.RequestPermissionsResultListener {

    private lateinit var channel: MethodChannel
    private lateinit var progressChannel: EventChannel
    private var activity: Activity? = null
    private var pendingStoragePermissionResult: Result? = null
    private var pendingInstallActivityResult: Result? = null
    private var pendingInstallActivityFile: File? = null
    private var pendingInstallPermissionResult: Result? = null
    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private var progressSink: EventChannel.EventSink? = null
    private var receiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Executors -----------------------------------------------------------
    // Bucket size of the parallel work pools: keep small enough not to starve
    // the UI/decoder threads, large enough to keep flash storage busy.
    private val workerCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    private val coordinatorExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "apk-scan").apply { isDaemon = true } }

    private val workerPool: ExecutorService =
        Executors.newFixedThreadPool(workerCount) { r -> Thread(r, "apk-worker").apply { isDaemon = true } }

    private val ioExecutor: ExecutorService =
        Executors.newFixedThreadPool(2) { r -> Thread(r, "apk-io").apply { isDaemon = true } }

    // --- Scan state ----------------------------------------------------------
    /** Incremented whenever a scan starts or is cancelled; stale scans abort. */
    @Volatile
    private var scanGeneration = 0

    private val parsedCount = AtomicInteger(0)
    private val discoveredCount = AtomicInteger(0)

    private val progressLock = Any()
    private val pendingBatch = ArrayList<Map<String, Any?>>(64)

    @Volatile
    private var lastProgressAt = 0L

    @Volatile
    private var currentDir = ""

    private val packageRemovedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED) {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName != null && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    mainHandler.post {
                        channel.invokeMethod("onPackageRemoved", mapOf("packageName" to packageName))
                    }
                }
            }
        }
    }

    companion object {
        private const val CHANNEL_NAME = "com.apkorganizer/apk_manager"
        private const val PROGRESS_CHANNEL_NAME = "com.apkorganizer/scan_progress"
        private const val INSTALL_REQUEST_CODE = 1001
        private const val MANAGE_STORAGE_REQUEST_CODE = 1002
        private const val INSTALL_PERMISSION_REQUEST_CODE = 1003

        /** Directories that are unreadable on Android 11+ (skipped for speed). */
        private val SKIP_DIR_SUFFIXES = listOf("/Android/data", "/Android/obb")

        /** Max directory depth — guards against symlink loops in odd filesystems. */
        private const val MAX_SCAN_DEPTH = 48

        /** Progress events are flushed at most this often (ms) or per batch size. */
        private const val PROGRESS_INTERVAL_MS = 140L
        private const val PROGRESS_BATCH_SIZE = 24

        /** Icons are downscaled to this many pixels on the longest edge. */
        private const val ICON_MAX_SIZE_PX = 128

        /** Icons not touched for this long are pruned from the cache. */
        private const val ICON_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** Larger copy buffer than the default 8 KB — noticeably faster copies. */
        private const val COPY_BUFFER_SIZE = 1 shl 17
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginBinding = binding
        channel = MethodChannel(binding.binaryMessenger, CHANNEL_NAME)
        channel.setMethodCallHandler(this)
        progressChannel = EventChannel(binding.binaryMessenger, PROGRESS_CHANNEL_NAME)
        progressChannel.setStreamHandler(progressStreamHandler)
        // Housekeeping: drop icons of APKs/apps that were removed long ago.
        ioExecutor.execute { pruneIconCache() }
    }

    private val progressStreamHandler = object : EventChannel.StreamHandler {
        override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
            progressSink = events
        }
        override fun onCancel(arguments: Any?) {
            progressSink = null
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        progressSink = null
        flutterPluginBinding = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
        binding.addRequestPermissionsResultListener(this)
        registerPackageRemovedReceiver(binding.activity)
    }

    override fun onDetachedFromActivityForConfigChanges() { activity = null }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
        binding.addRequestPermissionsResultListener(this)
        registerPackageRemovedReceiver(binding.activity)
    }

    override fun onDetachedFromActivity() {
        unregisterPackageRemovedReceiver()
        activity = null
    }

    private fun registerPackageRemovedReceiver(context: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }
        receiverRegistered = try {
            ContextCompat.registerReceiver(
                context,
                packageRemovedReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            true
        } catch (_: Exception) {
            try {
                context.registerReceiver(packageRemovedReceiver, filter)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun unregisterPackageRemovedReceiver() {
        if (!receiverRegistered) return
        try {
            activity?.unregisterReceiver(packageRemovedReceiver)
        } catch (_: Exception) {
            // Receiver was already unregistered (e.g. process recreation).
        }
        receiverRegistered = false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            INSTALL_REQUEST_CODE -> {
                handlePendingInstallActivityResult()
                true
            }
            INSTALL_PERMISSION_REQUEST_CODE -> {
                handlePendingInstallPermissionResult()
                true
            }
            MANAGE_STORAGE_REQUEST_CODE -> {
                handlePendingStoragePermissionResult()
                true
            }
            else -> false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        return if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            handlePendingStoragePermissionResult()
            true
        } else false
    }

    private fun getContext(): Context? = activity ?: flutterPluginBinding?.applicationContext
    private fun packageManager(): PackageManager? =
        activity?.packageManager ?: flutterPluginBinding?.applicationContext?.packageManager

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()

    // ------------------------------------------------------------------------
    // Progress reporting (batched + throttled)
    // ------------------------------------------------------------------------

    private fun sendProgress(type: String, filesFound: Int, dir: String, apks: List<Map<String, Any?>>? = null) {
        if (progressSink == null) return
        val event = mutableMapOf<String, Any?>(
            "type" to type,
            "filesFound" to filesFound,
            "currentDir" to dir,
        )
        if (!apks.isNullOrEmpty()) event["apks"] = apks
        mainHandler.post { progressSink?.success(event) }
    }

    /**
     * Buffers one parsed APK (and/or the directory currently being walked) and
     * flushes a batch when either the time window or the batch size is reached.
     * Call with [force] = true for the final batch so nothing is left buffered.
     */
    private fun emitProgress(
        apk: Map<String, Any?>? = null,
        dir: String? = null,
        discovered: Boolean = false,
        force: Boolean = false,
    ) {
        var flush = force
        var batch: List<Map<String, Any?>>? = null
        var count = 0
        synchronized(progressLock) {
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
        if (flush) sendProgress("progress", count, currentDir, batch)
    }

    // ------------------------------------------------------------------------
    // Icon cache
    // ------------------------------------------------------------------------

    private fun getIconCacheDir(): File {
        val base = getContext()?.cacheDir ?: flutterPluginBinding?.applicationContext?.cacheDir
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
     * cached icon is reused without decoding when nothing changed — this is a
     * large win when re-scanning or reopening the installed-apps list.
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

    // ------------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------------

    private fun isSkippedDir(dir: File, depth: Int): Boolean {
        if (depth > MAX_SCAN_DEPTH) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val path = dir.absolutePath
        for (suffix in SKIP_DIR_SUFFIXES) {
            if (path.endsWith(suffix) || path.contains("$suffix/")) return true
        }
        return false
    }

    private fun isActive(generation: Int): Boolean = generation == scanGeneration

    private fun resetScanState() {
        parsedCount.set(0)
        discoveredCount.set(0)
        synchronized(progressLock) {
            pendingBatch.clear()
            lastProgressAt = 0L
        }
    }

    /** Cancels a running scan: the in-flight scan returns its partial results. */
    private fun cancelScan(result: Result) {
        scanGeneration++
        synchronized(progressLock) { pendingBatch.clear() }
        result.success(true)
    }

    /**
     * Walks [root] iteratively (no recursion → no stack overflow on deep trees)
     * and collects every readable `.apk` file. Publishes discovery progress.
     */
    private fun collectApkFiles(root: File, generation: Int): List<File> {
        val found = ArrayList<File>(128)
        if (!root.exists() || !root.isDirectory) return found

        val queue = ArrayDeque<Pair<File, Int>>()
        queue.addLast(root to 0)
        var dirsSinceReport = 0

        while (queue.isNotEmpty()) {
            if (!isActive(generation)) break
            val (dir, depth) = queue.removeFirst()
            if (isSkippedDir(dir, depth)) continue

            dirsSinceReport++
            if (dirsSinceReport >= 16) {
                dirsSinceReport = 0
                emitProgress(dir = dir.absolutePath, discovered = true)
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
     * Parses [files] in parallel across [workerPool], emitting batched progress.
     * Stops early (returning partial results) if the scan generation changed.
     */
    private fun parseApksParallel(
        files: List<File>,
        generation: Int,
        emit: Boolean = true,
    ): List<Map<String, Any?>> {
        if (files.isEmpty()) return emptyList()
        val iconCacheDir = getIconCacheDir()
        val parsed = ArrayList<Map<String, Any?>>(files.size)

        mapParallel(files) { file ->
            if (!isActive(generation)) return@mapParallel null
            try {
                val info = parseApkInfo(file, iconCacheDir)
                if (emit) emitProgress(apk = info) else parsedCount.incrementAndGet()
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
     * Workers pull items from a shared cursor (dynamic load balancing, so one
     * huge APK can't stall a whole static chunk) and results are collected in a
     * synchronized list. Order is not preserved — every caller sorts or
     * aggregates afterwards.
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

    private fun startScan(result: Result) {
        val generation = ++scanGeneration
        resetScanState()
        val completed = AtomicBoolean(false)
        val succeed: (List<Map<String, Any?>>) -> Unit = { list ->
            if (completed.compareAndSet(false, true)) mainHandler.post { result.success(list) }
        }
        val fail: (String, String) -> Unit = { code, message ->
            if (completed.compareAndSet(false, true)) mainHandler.post { result.error(code, message, null) }
        }

        coordinatorExecutor.execute {
            val root = Environment.getExternalStorageDirectory()
            try {
                sendProgress("start", 0, root.absolutePath)
                val candidates = collectApkFiles(root, generation)
                val parsed = if (candidates.isEmpty()) {
                    emptyList()
                } else {
                    parseApksParallel(candidates, generation)
                }
                emitProgress(force = true)
                sendProgress("complete", parsed.size, "")
                succeed(parsed)
            } catch (e: Exception) {
                sendProgress("error", parsedCount.get(), e.message ?: "Unknown error")
                fail("SCAN_ERROR", "Failed to scan APK files: ${e.message}")
            }
        }
    }

    /** One-shot scan without progress events (kept for API compatibility). */
    private fun scanApkFiles(result: Result) {
        val generation = ++scanGeneration
        resetScanState()
        val completed = AtomicBoolean(false)
        coordinatorExecutor.execute {
            try {
                val root = Environment.getExternalStorageDirectory()
                val candidates = collectApkFiles(root, generation)
                val parsed = if (candidates.isEmpty()) emptyList() else parseApksParallel(candidates, generation, emit = false)
                if (completed.compareAndSet(false, true)) mainHandler.post { result.success(parsed) }
            } catch (e: Exception) {
                if (completed.compareAndSet(false, true)) {
                    mainHandler.post { result.error("SCAN_ERROR", "Failed to scan APK files: ${e.message}", null) }
                }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, 0)
            }
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

    private fun sanitizeFileName(name: String, maxLen: Int = 120): String {
        val cleaned = name.trim().replace(Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]"), "_").replace(Regex("\\s+"), "_").replace(Regex("_+"), "_").trim('.', '_', ' ')
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

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "scanApkFiles" -> scanApkFiles(result)
            "scanApkFilesWithProgress" -> startScan(result)
            "cancelScan" -> cancelScan(result)
            "installApk" -> installApk(call.argument<String>("path"), result)
            "deleteApk" -> ioExecutor.execute { deleteApk(call.argument<String>("path"), result) }
            "renameApk" -> renameApk(call.argument<String>("path"), call.argument<String>("newName"), result)
            "moveApk" -> moveApk(call.argument<String>("sourcePath"), call.argument<String>("destDir"), result)
            "checkStoragePermission" -> result.success(checkStoragePermission())
            "canInstallPackages" -> result.success(canInstallPackages())
            "requestStoragePermission" -> requestStoragePermission(result)
            "requestInstallPermission" -> requestInstallPermission(result)
            "getDirectories" -> ioExecutor.execute {
                val dirs = getDirectories()
                mainHandler.post { result.success(dirs) }
            }
            "getSubdirectories" -> ioExecutor.execute { getSubdirectories(call.argument<String>("parentPath"), result) }
            "createDirectory" -> ioExecutor.execute { createDirectory(call.argument<String>("parentPath"), call.argument<String>("folderName"), result) }
            "scanDirectoryForApks" -> scanDirectoryForApks(call.argument<String>("dirPath"), result)
            "detectDuplicates" -> detectDuplicates(call.argument<List<String>>("filePaths"), result)
            "getInstalledApps" -> getInstalledApps(call.argument<Boolean>("includeSystem") ?: false, result)
            "uninstallPackage" -> uninstallPackage(call.argument<String>("packageName"), result)
            "backupInstalledApp" -> backupInstalledApp(call.argument<String>("packageName"), call.argument<String>("destDir"), result)
            "getJavaVersion" -> {
                val version = System.getProperty("java.version")
                result.success(if (version.isNullOrEmpty() || version == "0") "" else version)
            }
            "getApkDetail" -> getApkDetail(call.argument<String>("path"), result)
            else -> result.notImplemented()
        }
    }

    private fun getApkDetail(path: String?, result: Result) {
        ioExecutor.execute {
            try {
                val file = File(path ?: throw IllegalArgumentException("path is required"))
                if (!file.exists()) throw IllegalArgumentException("File not found: $path")

                val detail = mutableMapOf<String, Any?>()
                val ctx = getContext() ?: throw IllegalStateException("Context not available")
                val pm = ctx.packageManager ?: throw IllegalStateException("PackageManager not available")

                val flags = PackageManager.GET_META_DATA or
                    PackageManager.GET_PERMISSIONS or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)

                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION") pm.getPackageArchiveInfo(file.absolutePath, flags)
                }

                if (packageInfo == null) {
                    mainHandler.post { result.error("PARSE_ERROR", "Failed to parse APK file", null) }
                    return@execute
                }

                detail["packageName"] = packageInfo.packageName ?: ""
                detail["versionName"] = packageInfo.versionName ?: "Unknown"
                detail["versionCode"] = versionCodeOf(packageInfo)

                val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
                val permissionFlags = packageInfo.requestedPermissionsFlags
                val permissionDetails = permissions.mapIndexed { index, perm ->
                    mapOf(
                        "name" to perm,
                        "granted" to (permissionFlags?.getOrNull(index)?.let { it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 } ?: false)
                    )
                }
                detail["permissions"] = permissionDetails

                val appInfo = packageInfo.applicationInfo
                if (appInfo != null) {
                    appInfo.sourceDir = file.absolutePath
                    appInfo.publicSourceDir = file.absolutePath
                    detail["appName"] = try {
                        appInfo.loadLabel(pm).toString()
                    } catch (_: Exception) {
                        file.nameWithoutExtension
                    }
                    detail["minSdkVersion"] = appInfo.minSdkVersion
                    detail["targetSdkVersion"] = appInfo.targetSdkVersion
                    try {
                        loadIconFile(appInfo, pm, getIconCacheDir(), file.nameWithoutExtension, file.lastModified())
                            ?.absolutePath
                            ?.let { detail["iconPath"] = it }
                    } catch (_: Exception) {
                    }
                } else {
                    detail["appName"] = file.nameWithoutExtension
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
                detail["supportedAbis"] = abis

                try {
                    val signatureHash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val signingInfo = packageInfo.signingInfo
                        val certs = signingInfo?.apkContentsSigners
                        if (certs != null && certs.isNotEmpty()) {
                            val digest = MessageDigest.getInstance("SHA-256")
                            for (cert in certs) { digest.update(cert.toByteArray()) }
                            bytesToHex(digest.digest())
                        } else null
                    } else {
                        @Suppress("DEPRECATION")
                        val sigs = packageInfo.signatures
                        if (sigs != null && sigs.isNotEmpty()) {
                            val digest = MessageDigest.getInstance("SHA-256")
                            for (sig in sigs) { digest.update(sig.toByteArray()) }
                            bytesToHex(digest.digest())
                        } else null
                    }
                    detail["signatureHash"] = signatureHash
                } catch (_: Exception) { detail["signatureHash"] = null }

                detail["fileSize"] = file.length()
                detail["fileName"] = file.name
                detail["filePath"] = file.absolutePath

                mainHandler.post { result.success(detail) }
            } catch (e: Exception) {
                mainHandler.post { result.error("DETAIL_ERROR", "Failed to get APK detail: ${e.message}", null) }
            }
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

    private fun installApk(path: String?, result: Result) {
        val act = activity ?: run { result.error("NO_ACTIVITY", "Activity not available", null); return }
        val file = File(path ?: return result.error("INVALID_ARGUMENT", "Path is required", null))
        if (!file.exists()) return result.error("FILE_NOT_FOUND", "APK file not found: $path", null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !act.packageManager.canRequestPackageInstalls()) {
            pendingInstallActivityResult = result
            pendingInstallActivityFile = file
            act.startActivityForResult(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                setData(Uri.parse("package:${act.packageName}"))
            }, INSTALL_REQUEST_CODE)
            return
        }

        startInstallIntent(file, result)
    }

    private fun startInstallIntent(file: File, result: Result) {
        val act = activity ?: run { result.error("NO_ACTIVITY", "Activity not available", null); return }
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", file)
            } else Uri.fromFile(file)
            act.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            result.success(mapOf("status" to "install_started"))
        } catch (e: Exception) {
            result.error("INSTALL_ERROR", "Failed to install APK: ${e.message}", null)
        }
    }

    /**
     * Called when the user returns from the "install unknown apps" settings
     * screen that [installApk] opened. If permission was granted, the install
     * intent that was deferred now actually fires; otherwise the caller is
     * told the user was redirected but nothing was installed, instead of the
     * old behaviour of always reporting "install_started" either way.
     */
    private fun handlePendingInstallActivityResult() {
        val result = pendingInstallActivityResult
        val file = pendingInstallActivityFile
        pendingInstallActivityResult = null
        pendingInstallActivityFile = null
        if (result == null || file == null) return

        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.packageManager?.canRequestPackageInstalls() ?: false
        } else true

        if (granted) {
            startInstallIntent(file, result)
        } else {
            result.success(mapOf("status" to "redirected_to_settings"))
        }
    }

    /**
     * Standalone install-permission request used by the in-app permission
     * rationale dialog (separate from the inline prompt inside [installApk]).
     */
    private fun requestInstallPermission(result: Result) {
        val act = activity ?: return result.error("NO_ACTIVITY", "Activity not available", null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || act.packageManager.canRequestPackageInstalls()) {
            result.success(mapOf("granted" to true, "status" to "granted"))
            return
        }
        if (pendingInstallPermissionResult != null) {
            return result.error("PERMISSION_REQUEST_IN_PROGRESS", "Permission request already in progress", null)
        }
        pendingInstallPermissionResult = result
        try {
            act.startActivityForResult(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                setData(Uri.parse("package:${act.packageName}"))
            }, INSTALL_PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            pendingInstallPermissionResult = null
            result.error("PERMISSION_REQUEST_ERROR", "Error requesting permission: ${e.message}", null)
        }
    }

    private fun handlePendingInstallPermissionResult() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.packageManager?.canRequestPackageInstalls() ?: false
        } else true
        pendingInstallPermissionResult?.success(mapOf("granted" to granted, "status" to if (granted) "granted" else "denied"))
        pendingInstallPermissionResult = null
    }

    private fun deleteApk(path: String?, result: Result) {
        if (path.isNullOrEmpty()) {
            mainHandler.post { result.error("INVALID_ARGUMENT", "Path is required", null) }
            return
        }
        try {
            val file = File(path)
            if (!file.exists()) {
                mainHandler.post { result.error("FILE_NOT_FOUND", "APK file not found: $path", null) }
                return
            }
            val deleted = file.delete()
            if (deleted) {
                mainHandler.post { result.success(mapOf("success" to true, "path" to path)) }
            } else {
                // Never report success: the Dart layer drops deleted entries from
                // the list, so a silent failure would desync the UI from storage.
                mainHandler.post {
                    result.error("DELETE_FAILED", "Could not delete the file. Check storage permission and try again.", null)
                }
            }
        } catch (e: Exception) {
            mainHandler.post { result.error("DELETE_FAILED", "Failed to delete APK: ${e.message}", null) }
        }
    }

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
            try { dest.delete() } catch (_: Exception) {}
            false
        }
    }

    private fun renameApk(path: String?, newName: String?, result: Result) {
        ioExecutor.execute {
            try {
                val file = File(path ?: throw IllegalArgumentException("Path is required"))
                if (!file.exists()) throw IllegalArgumentException("APK file not found: $path")

                val finalName = sanitizeFileName(newName ?: throw IllegalArgumentException("newName is required")).let {
                    if (it.endsWith(".apk", true)) it else "$it.apk"
                }

                val parentDir = file.parentFile ?: throw IllegalArgumentException("Invalid parent directory")
                val newFile = uniqueFile(parentDir, finalName, file.absolutePath)

                if (newFile.absolutePath == file.absolutePath) {
                    mainHandler.post {
                        result.success(mapOf("success" to true, "oldPath" to path, "newPath" to file.absolutePath, "newName" to file.name))
                    }
                    return@execute
                }

                if (file.renameTo(newFile)) {
                    mainHandler.post {
                        result.success(mapOf("success" to true, "oldPath" to path, "newPath" to newFile.absolutePath, "newName" to newFile.name))
                    }
                    return@execute
                }

                // Fallback: copy and delete (different filesystem / bind mount).
                if (!copyFile(file, newFile)) {
                    mainHandler.post {
                        result.error("RENAME_FAILED", "Failed to rename APK file: copy verification failed", null)
                    }
                    return@execute
                }
                val deleted = file.delete()
                if (deleted) {
                    mainHandler.post {
                        result.success(mapOf("success" to true, "oldPath" to path, "newPath" to newFile.absolutePath, "newName" to newFile.name))
                    }
                } else {
                    // Keep storage consistent: roll the copy back so the caller
                    // does not end up with two identical files.
                    newFile.delete()
                    mainHandler.post {
                        result.error("RENAME_FAILED", "Failed to delete original file after copy", null)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    result.error("RENAME_FAILED", "Failed to rename APK file: ${e.message}", null)
                }
            }
        }
    }

    private fun moveApk(sourcePath: String?, destDir: String?, result: Result) {
        ioExecutor.execute {
            try {
                val source = File(sourcePath ?: throw IllegalArgumentException("sourcePath is required"))
                if (!source.exists()) throw IllegalArgumentException("Source file not found: $sourcePath")

                val destDirFile = File(destDir ?: throw IllegalArgumentException("destDir is required"))
                if (!destDirFile.exists() && !destDirFile.mkdirs()) {
                    throw IllegalArgumentException("Failed to create destination: $destDir")
                }

                if (destDirFile.absolutePath == source.parentFile?.absolutePath) {
                    // Nothing to do — report it so the UI does not claim a move.
                    mainHandler.post {
                        result.success(
                            mapOf(
                                "success" to true,
                                "sourcePath" to sourcePath,
                                "destPath" to source.absolutePath,
                                "conflictResolved" to false,
                                "skipped" to true,
                            )
                        )
                    }
                    return@execute
                }

                val destFile = uniqueFile(destDirFile, source.name, source.absolutePath)
                val conflictResolved = destFile.name != source.name

                if (source.renameTo(destFile)) {
                    mainHandler.post {
                        result.success(mapOf("success" to true, "sourcePath" to sourcePath, "destPath" to destFile.absolutePath, "conflictResolved" to conflictResolved, "skipped" to false))
                    }
                    return@execute
                }

                // Fallback: copy and delete (e.g. moving to another volume).
                if (!copyFile(source, destFile)) {
                    mainHandler.post {
                        result.error("MOVE_FAILED", "Failed to move APK file: copy verification failed", null)
                    }
                    return@execute
                }
                if (source.delete()) {
                    mainHandler.post {
                        result.success(mapOf("success" to true, "sourcePath" to sourcePath, "destPath" to destFile.absolutePath, "conflictResolved" to conflictResolved, "skipped" to false))
                    }
                } else {
                    destFile.delete()
                    mainHandler.post {
                        result.error("MOVE_FAILED", "Failed to delete source file after copy", null)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    result.error("MOVE_FAILED", "Failed to move APK file: ${e.message}", null)
                }
            }
        }
    }

    /**
     * Storage roots for the directory picker: internal storage first, then any
     * removable/secondary volumes, then the top level folders of the primary
     * volume.
     */
    private fun getDirectories(): List<Map<String, String>> {
        val dirs = LinkedHashMap<String, Map<String, String>>()
        val primary = Environment.getExternalStorageDirectory()
        dirs[primary.absolutePath] = mapOf("name" to "Internal Storage", "path" to primary.absolutePath)

        try {
            getContext()?.getExternalFilesDirs(null)
                ?.filterNotNull()
                ?.forEach { filesDir ->
                    val root = storageRootOf(filesDir.absolutePath) ?: return@forEach
                    if (!dirs.containsKey(root)) {
                        dirs[root] = mapOf("name" to storageLabel(root), "path" to root)
                    }
                }
        } catch (_: Exception) {
        }

        try {
            primary.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name.lowercase() }
                ?.forEach { dirs.putIfAbsent(it.absolutePath, mapOf("name" to it.name, "path" to it.absolutePath)) }
        } catch (_: Exception) {
        }

        return dirs.values.toList()
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

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else getContext()?.let { ctx ->
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } ?: false
    }

    private fun canInstallPackages(): Boolean {
        val act = activity ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            act.packageManager.canRequestPackageInstalls()
        } else true
    }

    private fun requestStoragePermission(result: Result) {
        val act = activity ?: return result.error("NO_ACTIVITY", "Activity not available", null)
        if (pendingStoragePermissionResult != null) return result.error("PERMISSION_REQUEST_IN_PROGRESS", "Permission request already in progress", null)

        pendingStoragePermissionResult = result
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    setData(Uri.parse("package:${act.packageName}"))
                }
                try {
                    act.startActivityForResult(appIntent, MANAGE_STORAGE_REQUEST_CODE)
                } catch (_: Exception) {
                    // Some OEM builds only expose the global "all files" screen.
                    act.startActivityForResult(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                        MANAGE_STORAGE_REQUEST_CODE,
                    )
                }
            } else {
                act.requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), MANAGE_STORAGE_REQUEST_CODE)
            }
        } catch (e: Exception) {
            pendingStoragePermissionResult = null
            result.error("PERMISSION_REQUEST_ERROR", "Error requesting permission: ${e.message}", null)
        }
    }

    private fun handlePendingStoragePermissionResult() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else activity?.let { act ->
            ContextCompat.checkSelfPermission(act, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(act, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } ?: false

        pendingStoragePermissionResult?.success(mapOf("granted" to granted, "status" to if (granted) "granted" else "denied"))
        pendingStoragePermissionResult = null
    }

    private fun getSubdirectories(parentPath: String?, result: Result) {
        if (parentPath == null) {
            mainHandler.post { result.error("INVALID_ARGUMENT", "parentPath is required", null) }
            return
        }
        val dir = File(parentPath)
        if (!dir.exists() || !dir.isDirectory) {
            mainHandler.post { result.error("INVALID_PATH", "Directory does not exist", null) }
            return
        }
        val subdirs = dir.listFiles()
            ?.filter { it.isDirectory && it.canRead() }
            ?.sortedBy { it.name.lowercase() }
            ?.map { mapOf("name" to it.name, "path" to it.absolutePath) }
            ?: emptyList()
        mainHandler.post { result.success(subdirs) }
    }

    private fun createDirectory(parentPath: String?, folderName: String?, result: Result) {
        if (parentPath == null) {
            mainHandler.post { result.error("INVALID_ARGUMENT", "parentPath is required", null) }
            return
        }
        val parent = File(parentPath)
        if (!parent.exists() || !parent.isDirectory) {
            mainHandler.post { result.error("INVALID_PATH", "Parent directory does not exist", null) }
            return
        }
        if (folderName == null) {
            mainHandler.post { result.error("INVALID_NAME", "Folder name is required", null) }
            return
        }
        val safeName = sanitizeFileName(folderName, 80).replace(Regex("\\s+"), " ")
        if (safeName.isBlank()) {
            mainHandler.post { result.error("INVALID_NAME", "Folder name is required", null) }
            return
        }
        val newDir = File(parent, safeName)
        if (!newDir.exists() && !newDir.mkdirs()) {
            mainHandler.post { result.error("CREATE_DIR_FAILED", "Could not create folder", null) }
            return
        }
        mainHandler.post { result.success(mapOf("name" to newDir.name, "path" to newDir.absolutePath)) }
    }

    private fun scanDirectoryForApks(dirPath: String?, result: Result) {
        ioExecutor.execute {
            val dir = dirPath?.let { File(it) }
            if (dir == null || !dir.exists() || !dir.isDirectory) {
                mainHandler.post { result.success(emptyList<Map<String, Any?>>()) }
                return@execute
            }
            val iconCacheDir = getIconCacheDir()
            val apkList = dir.listFiles()
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
            mainHandler.post { result.success(apkList) }
        }
    }

    private fun detectDuplicates(filePaths: List<String>?, result: Result) {
        ioExecutor.execute {
            val pm = packageManager()
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "Context not available", null) }; return@execute }
            val groups = mutableMapOf<String, MutableList<String>>()

            filePaths?.forEach { path ->
                val file = File(path).takeIf { it.exists() } ?: return@forEach
                var pkgName = "unknown"
                var appName: String
                var versionName: String
                var versionCode = 0L

                try {
                    val info = runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0L))
                        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(path, 0)
                    }.getOrNull()

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

                val versionPart = if (versionCode > 0L) versionCode.toString() else versionName.lowercase()
                val key = if (pkgName != "unknown") "pkg:${pkgName.lowercase()}|version:$versionPart" else "fallback:${appName.lowercase()}|${versionName.lowercase()}"
                groups.getOrPut(key) { mutableListOf() }.add(path)
            }

            mainHandler.post { result.success(groups.values.filter { it.size > 1 }) }
        }
    }

    private fun buildInstalledAppMap(pkg: PackageInfo, pm: PackageManager, iconCacheDir: File): Map<String, Any?>? {
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
        return mapOf(
            "appName" to appName,
            "packageName" to packageName,
            "versionName" to (pkg.versionName ?: "Unknown"),
            "versionCode" to versionCodeOf(pkg),
            "iconPath" to icon?.absolutePath,
            "sourceDir" to sourceDir,
            "size" to if (sourceDir.isNotEmpty()) File(sourceDir).length() else 0L,
            "isSystemApp" to isSystem,
        )
    }

    private fun getInstalledApps(includeSystem: Boolean, result: Result) {
        ioExecutor.execute {
            val ctx = getContext()
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "Context not available", null) }; return@execute }
            val pm = ctx.packageManager
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "PackageManager not available", null) }; return@execute }

            try {
                val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION") pm.getInstalledPackages(0)
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

                // Labels + icons in parallel: this is what made the list slow to
                // appear on devices with many apps.
                val apps = mapParallel(selected) { pkg -> buildInstalledAppMap(pkg, pm, iconCacheDir) }
                    .sortedBy { (it["appName"] as? String)?.lowercase() ?: "" }

                mainHandler.post { result.success(apps) }
            } catch (e: Exception) {
                mainHandler.post { result.error("APPS_ERROR", "Failed to load installed apps: ${e.message}", null) }
            }
        }
    }

    private fun uninstallPackage(packageName: String?, result: Result) {
        val act = activity ?: return result.error("NO_ACTIVITY", "Activity not available", null)
        val pkg = packageName ?: return result.error("INVALID_ARGUMENT", "packageName is required", null)
        if (pkg == act.packageName) return result.error("SELF_UNINSTALL_BLOCKED", "Cannot uninstall self", null)

        try {
            Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { setData(Uri.parse("package:$pkg")) }
                .takeIf { it.resolveActivity(act.packageManager) != null }
                ?.let {
                    act.startActivity(it)
                    result.success(mapOf("status" to "uninstall_requested", "packageName" to pkg))
                } ?: result.error("UNINSTALL_ERROR", "No handler for uninstall", null)
        } catch (e: Exception) {
            result.error("UNINSTALL_ERROR", "Failed to uninstall: ${e.message}", null)
        }
    }

    private fun backupInstalledApp(packageName: String?, destDir: String?, result: Result) {
        ioExecutor.execute {
            val ctx = getContext()
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "Context not available", null) }; return@execute }
            val pm = ctx.packageManager
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "PackageManager not available", null) }; return@execute }
            val pkg = packageName
                ?: run { mainHandler.post { result.error("INVALID_ARGUMENT", "packageName is required", null) }; return@execute }
            val dest = destDir
                ?: run { mainHandler.post { result.error("INVALID_ARGUMENT", "destDir is required", null) }; return@execute }

            try {
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0)
                }

                val ai = pkgInfo.applicationInfo
                    ?: run { mainHandler.post { result.error("SOURCE_NOT_FOUND", "App info not found", null) }; return@execute }
                val sourcePath = ai.sourceDir
                    ?: run { mainHandler.post { result.error("SOURCE_NOT_FOUND", "Source not found", null) }; return@execute }
                val source = File(sourcePath).takeIf { it.exists() }
                    ?: run { mainHandler.post { result.error("SOURCE_NOT_FOUND", "Source file not found", null) }; return@execute }

                val destDirFile = File(dest)
                if (!destDirFile.exists() && !destDirFile.mkdirs()) {
                    mainHandler.post { result.error("DIR_CREATE_FAILED", "Failed to create dest", null) }
                    return@execute
                }
                val label = try {
                    ai.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg
                }
                val version = pkgInfo.versionName ?: "unknown"
                val destFile = uniqueFile(destDirFile, sanitizeFileName("${label}_${version}.apk"))
                if (!copyFile(source, destFile)) {
                    mainHandler.post { result.error("BACKUP_ERROR", "Failed to copy APK (insufficient space?)", null) }
                    return@execute
                }

                mainHandler.post { result.success(mapOf("success" to true, "packageName" to pkg, "destPath" to destFile.absolutePath, "fileName" to destFile.name)) }
            } catch (e: Exception) {
                mainHandler.post { result.error("BACKUP_ERROR", "Failed to backup: ${e.message}", null) }
            }
        }
    }
}
