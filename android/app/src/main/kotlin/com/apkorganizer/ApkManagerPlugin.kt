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
import android.graphics.drawable.BitmapDrawable
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
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.zip.ZipFile

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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: Executor = Executors.newSingleThreadExecutor()

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
        private val SKIP_DIRS = setOf("/Android/data", "/Android/obb")
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginBinding = binding
        channel = MethodChannel(binding.binaryMessenger, CHANNEL_NAME)
        channel.setMethodCallHandler(this)
        progressChannel = EventChannel(binding.binaryMessenger, PROGRESS_CHANNEL_NAME)
        progressChannel.setStreamHandler(progressStreamHandler)
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
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }
        activity?.registerReceiver(packageRemovedReceiver, filter)
    }

    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity?.unregisterReceiver(packageRemovedReceiver)
        activity = null
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

    private fun requireActivity(): Activity = activity ?: throw IllegalStateException("Activity not available")
    private fun requirePackageManager(): PackageManager = requireActivity().packageManager
    private fun getContext(): Context? = activity ?: flutterPluginBinding?.applicationContext

    private inline fun <T> runBackground(crossinline block: () -> T, crossinline onSuccess: (T) -> Unit, crossinline onError: (String) -> Unit = { msg -> }) {
        executor.execute {
            try {
                val result = block()
                mainHandler.post { onSuccess(result) }
            } catch (e: Exception) {
                mainHandler.post { onError(e.message ?: "Unknown error") }
            }
        }
    }

    private fun sendProgress(type: String, filesFound: Int, currentDir: String, apkInfo: Map<String, Any?>? = null) {
        val event = mutableMapOf<String, Any?>(
            "type" to type,
            "filesFound" to filesFound,
            "currentDir" to currentDir
        )
        apkInfo?.let { event["apk"] = it }
        mainHandler.post { progressSink?.success(event) }
    }

    private fun getIconCacheDir(): File {
        val dir = File(flutterPluginBinding?.applicationContext?.cacheDir, "apk_icons")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun shouldSkipDir(path: String): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SKIP_DIRS.any { path.contains(it) }

    private fun parseApkInfo(file: File, iconCacheDir: File): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        result["fileName"] = file.name
        result["path"] = file.absolutePath
        result["size"] = file.length()
        result["lastModified"] = file.lastModified()

        val pm = activity?.packageManager ?: flutterPluginBinding?.applicationContext?.packageManager ?: return result.apply { setFallbackInfo(file) }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_META_DATA else PackageManager.GET_SIGNATURES

        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, flags)
            }
        } catch (_: Exception) { null }

        if (packageInfo == null) {
            result.setFallbackInfo(file)
            return result
        }

        packageInfo.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            result["appName"] = appInfo.loadLabel(pm).toString()
            try {
                saveIconToFile(appInfo.loadIcon(pm), iconCacheDir, file.nameWithoutExtension)?.absolutePath?.let { result["iconPath"] = it }
            } catch (_: Exception) {}
        } ?: result.setFallbackInfo(file)

        result["packageName"] = packageInfo.packageName
        result["versionName"] = packageInfo.versionName ?: "Unknown"
        result["versionCode"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
        return result
    }

    private fun MutableMap<String, Any?>.setFallbackInfo(file: File) {
        this["appName"] = file.nameWithoutExtension
        this["packageName"] = "unknown"
        this["versionName"] = "Unknown"
        this["versionCode"] = 0L
    }

    private fun saveIconToFile(drawable: Drawable, cacheDir: File, baseName: String): File? = try {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawable.setBounds(0, 0, w, h); drawable.draw(this) } }
        }
        val safeName = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        File(cacheDir, "${safeName}_icon.png").apply {
            FileOutputStream(this).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    } catch (_: Exception) { null }

    private fun scanDirectory(directory: File, apkList: MutableList<Map<String, Any?>>, iconCacheDir: File, withProgress: Boolean = false) {
        if (!directory.exists() || !directory.isDirectory || !directory.canRead()) return
        try {
            directory.listFiles()?.forEach { file ->
                when {
                    file.isDirectory && !shouldSkipDir(file.absolutePath) -> {
                        if (withProgress) sendProgress("progress", apkList.size, file.absolutePath)
                        scanDirectory(file, apkList, iconCacheDir, withProgress)
                    }
                    file.isFile && file.name.endsWith(".apk", true) -> {
                        val apkInfo = parseApkInfo(file, iconCacheDir)
                        apkList.add(apkInfo)
                        if (withProgress) sendProgress("progress", apkList.size, file.parent ?: "", apkInfo)
                    }
                }
            }
        } catch (_: SecurityException) {}
    }

    private fun sanitizeFileName(name: String, maxLen: Int = 120): String {
        val cleaned = name.trim().replace(Regex("[<>:\"/\\\\|?*\u0000-\u001F]"), "_").replace(Regex("\\s+"), "_").replace(Regex("_+"), "_").trim('.', '_', ' ')
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
            "scanApkFiles" -> runBackground(
                block = { mutableListOf<Map<String, Any?>>().apply { scanDirectory(Environment.getExternalStorageDirectory(), this, getIconCacheDir()) } },
                onSuccess = { result.success(it) },
                onError = { result.error("SCAN_ERROR", "Failed to scan APK files: $it", null) }
            )
            "scanApkFilesWithProgress" -> executor.execute {
                try {
                    val apkList = mutableListOf<Map<String, Any?>>()
                    sendProgress("start", 0, Environment.getExternalStorageDirectory().absolutePath)
                    scanDirectory(Environment.getExternalStorageDirectory(), apkList, getIconCacheDir(), true)
                    sendProgress("complete", apkList.size, "")
                    mainHandler.post { result.success(apkList) }
                } catch (e: Exception) {
                    sendProgress("error", 0, e.message ?: "Unknown error")
                    mainHandler.post { result.error("SCAN_ERROR", "Failed to scan APK files: ${e.message}", null) }
                }
            }
            "installApk" -> installApk(call.argument<String>("path"), result)
            "deleteApk" -> executor.execute { deleteApk(call.argument<String>("path"), result) }
            "renameApk" -> renameApk(call.argument<String>("path"), call.argument<String>("newName"), result)
            "moveApk" -> moveApk(call.argument<String>("sourcePath"), call.argument<String>("destDir"), result)
            "checkStoragePermission" -> result.success(checkStoragePermission())
            "canInstallPackages" -> result.success(canInstallPackages())
            "requestStoragePermission" -> requestStoragePermission(result)
            "requestInstallPermission" -> requestInstallPermission(result)
            "getDirectories" -> executor.execute {
                val dirs = getDirectories()
                mainHandler.post { result.success(dirs) }
            }
            "getSubdirectories" -> executor.execute { getSubdirectories(call.argument<String>("parentPath"), result) }
            "createDirectory" -> executor.execute { createDirectory(call.argument<String>("parentPath"), call.argument<String>("folderName"), result) }
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
        executor.execute {
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
                detail["versionCode"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong()

                val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
                val permissionFlags = packageInfo.requestedPermissionsFlags
                val permissionDetails = permissions.mapIndexed { index, perm ->
                    mapOf(
                        "name" to perm,
                        "granted" to (permissionFlags?.getOrNull(index)?.let { it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 } ?: false)
                    )
                }
                detail["permissions"] = permissionDetails

                packageInfo.applicationInfo?.let { appInfo ->
                    appInfo.sourceDir = file.absolutePath
                    appInfo.publicSourceDir = file.absolutePath
                    detail["appName"] = appInfo.loadLabel(pm).toString()
                    detail["minSdkVersion"] = appInfo.minSdkVersion
                    detail["targetSdkVersion"] = appInfo.targetSdkVersion
                    try {
                        saveIconToFile(appInfo.loadIcon(pm), getIconCacheDir(), file.nameWithoutExtension)?.absolutePath?.let { detail["iconPath"] = it }
                    } catch (_: Exception) {}
                }

                val abis = mutableListOf<String>()
                try {
                    ZipFile(file).use { zip ->
                        val entries = zip.entries()
                        val libDirs = mutableSetOf<String>()
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
                } catch (_: Exception) {}
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
                            digest.update(sigs[0].toByteArray())
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
        if (path == null) {
            mainHandler.post { result.error("INVALID_ARGUMENT", "Path is required", null) }
            return
        }
        val file = File(path)
        if (!file.exists()) {
            mainHandler.post { result.error("FILE_NOT_FOUND", "APK file not found: $path", null) }
            return
        }
        val deleted = file.delete()
        mainHandler.post {
            result.success(if (deleted) mapOf("success" to true, "path" to path) else mapOf("success" to false))
        }
    }

    private fun renameApk(path: String?, newName: String?, result: Result) {
        executor.execute {
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

                val renamed = file.renameTo(newFile)
                if (renamed) {
                    mainHandler.post {
                        result.success(mapOf("success" to true, "oldPath" to path, "newPath" to newFile.absolutePath, "newName" to newFile.name))
                    }
                } else {
                    // Fallback: copy and delete
                    try {
                        file.copyTo(newFile, overwrite = false)
                        if (newFile.exists() && newFile.length() == file.length()) {
                            val deleted = file.delete()
                            if (deleted) {
                                mainHandler.post {
                                    result.success(mapOf("success" to true, "oldPath" to path, "newPath" to newFile.absolutePath, "newName" to newFile.name))
                                }
                            } else {
                                throw Exception("Failed to delete original file after copy")
                            }
                        } else {
                            newFile.delete()
                            throw Exception("Copy verification failed")
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            result.error("RENAME_FAILED", "Failed to rename APK file: ${e.message}", null)
                        }
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
        executor.execute {
            try {
                val source = File(sourcePath ?: throw IllegalArgumentException("sourcePath is required"))
                if (!source.exists()) throw IllegalArgumentException("Source file not found: $sourcePath")
                
                val destDirFile = File(destDir ?: throw IllegalArgumentException("destDir is required"))
                if (!destDirFile.exists() && !destDirFile.mkdirs()) {
                    throw IllegalArgumentException("Failed to create destination: $destDir")
                }

                val destFile = uniqueFile(destDirFile, source.name, source.absolutePath)
                val conflictResolved = destFile.name != source.name
                val moved = source.renameTo(destFile)

                if (moved) {
                    mainHandler.post {
                        result.success(mapOf("success" to true, "sourcePath" to sourcePath, "destPath" to destFile.absolutePath, "conflictResolved" to conflictResolved))
                    }
                } else {
                    // Fallback: copy and delete
                    try {
                        source.copyTo(destFile, overwrite = false)
                        if (destFile.exists() && destFile.length() == source.length()) {
                            val deleted = source.delete()
                            if (deleted) {
                                mainHandler.post {
                                    result.success(mapOf("success" to true, "sourcePath" to sourcePath, "destPath" to destFile.absolutePath, "conflictResolved" to conflictResolved))
                                }
                            } else {
                                throw Exception("Failed to delete source file after copy")
                            }
                        } else {
                            destFile.delete()
                            throw Exception("Copy verification failed")
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            result.error("MOVE_FAILED", "Failed to move APK file: ${e.message}", null)
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    result.error("MOVE_FAILED", "Failed to move APK file: ${e.message}", null)
                }
            }
        }
    }

    private fun getDirectories(): List<Map<String, String>> {
        val ext = Environment.getExternalStorageDirectory()
        val dirs = mutableListOf<Map<String, String>>().apply {
            add(mapOf("name" to "Internal Storage", "path" to ext.absolutePath))
            ext.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }?.forEach {
                add(mapOf("name" to it.name, "path" to it.absolutePath))
            }
        }
        return dirs
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
                act.startActivityForResult(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    setData(Uri.parse("package:${act.packageName}"))
                }, MANAGE_STORAGE_REQUEST_CODE)
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
        val subdirs = dir.listFiles()?.filter { it.isDirectory && it.canRead() }?.sortedBy { it.name.lowercase() }?.map { mapOf("name" to it.name, "path" to it.absolutePath) } ?: emptyList<Map<String, String>>()
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
        executor.execute {
            val dir = dirPath?.let { File(it) }
            if (dir == null || !dir.exists() || !dir.isDirectory) {
                mainHandler.post { result.success(emptyList<Map<String, Any?>>()) }
                return@execute
            }
            val apkList = mutableListOf<Map<String, Any?>>()
            val iconCacheDir = getIconCacheDir()
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(".apk", true) }?.forEach {
                val info = parseApkInfo(it, iconCacheDir).toMutableMap().apply { put("lastModified", it.lastModified()) }
                apkList.add(info)
            }
            mainHandler.post { result.success(apkList) }
        }
    }

    private fun detectDuplicates(filePaths: List<String>?, result: Result) {
        executor.execute {
            val pm = activity?.packageManager ?: flutterPluginBinding?.applicationContext?.packageManager
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "Context not available", null) }; return@execute }
            val groups = mutableMapOf<String, MutableList<String>>()

            filePaths?.forEach { path ->
                val file = File(path).takeIf { it.exists() } ?: return@forEach
                var pkgName = "unknown"
                var appName: String
                var versionName: String
                var versionCode = 0L

                try {
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_META_DATA else PackageManager.GET_SIGNATURES
                    val info = runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
                        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(path, flags)
                    }.getOrNull()

                    if (info != null) {
                        pkgName = info.packageName ?: "unknown"
                        info.applicationInfo?.let { ai ->
                            ai.sourceDir = path
                            ai.publicSourceDir = path
                            appName = ai.loadLabel(pm).toString()
                        } ?: run { appName = file.nameWithoutExtension }
                        versionName = info.versionName ?: "Unknown"
                        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
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

    private fun getInstalledApps(includeSystem: Boolean, result: Result) {
        executor.execute {
            val ctx = activity ?: flutterPluginBinding?.applicationContext
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "Context not available", null) }; return@execute }
            val pm = ctx.packageManager
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "PackageManager not available", null) }; return@execute }

            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.getInstalledPackages(0)

            val iconCacheDir = getIconCacheDir()
            val apps = packages.mapNotNull { pkg ->
                val app = pkg.applicationInfo ?: return@mapNotNull null
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (includeSystem != isSystem) return@mapNotNull null
                // Always exclude this app itself (its own APK source could be
                // shown/backed up otherwise).
                if (pkg.packageName == ctx.packageName) return@mapNotNull null

                mapOf(
                    "appName" to app.loadLabel(pm).toString(),
                    "packageName" to pkg.packageName,
                    "versionName" to (pkg.versionName ?: "Unknown"),
                    "versionCode" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong(),
                    "iconPath" to runCatching { saveIconToFile(app.loadIcon(pm), iconCacheDir, pkg.packageName)?.absolutePath }.getOrNull(),
                    "sourceDir" to (app.sourceDir ?: ""),
                    "size" to (app.sourceDir?.let { File(it).length() } ?: 0L),
                    "isSystemApp" to isSystem
                )
            }.sortedBy { (it["appName"] as? String)?.lowercase() ?: "" }

            mainHandler.post { result.success(apps) }
        }
    }

    private fun uninstallPackage(packageName: String?, result: Result) {
        val act = activity ?: return result.error("NO_ACTIVITY", "Activity not available", null)
        val pkg = packageName ?: return result.error("INVALID_ARGUMENT", "packageName is required", null)
        if (pkg == act.packageName) return result.error("SELF_UNINSTALL_BLOCKED", "Cannot uninstall self", null)

        try {
            Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { setData(Uri.parse("package:$pkg")) }.takeIf { it.resolveActivity(act.packageManager) != null }?.let {
                act.startActivity(it)
                result.success(mapOf("status" to "uninstall_requested", "packageName" to pkg))
            } ?: result.error("UNINSTALL_ERROR", "No handler for uninstall", null)
        } catch (e: Exception) {
            result.error("UNINSTALL_ERROR", "Failed to uninstall: ${e.message}", null)
        }
    }

    private fun backupInstalledApp(packageName: String?, destDir: String?, result: Result) {
        executor.execute {
            val ctx = activity ?: flutterPluginBinding?.applicationContext
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "Context not available", null) }; return@execute }
            val pm = ctx.packageManager
                ?: run { mainHandler.post { result.error("NO_CONTEXT", "PackageManager not available", null) }; return@execute }
            val pkg = packageName
                ?: run { mainHandler.post { result.error("INVALID_ARGUMENT", "packageName is required", null) }; return@execute }
            val dest = destDir
                ?: run { mainHandler.post { result.error("INVALID_ARGUMENT", "destDir is required", null) }; return@execute }

            try {
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                else @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0)

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
                val label = ai.loadLabel(pm).toString()
                val version = pkgInfo.versionName ?: "unknown"
                val destFile = uniqueFile(destDirFile, sanitizeFileName("${label}_${version}.apk"))
                source.copyTo(destFile, false)

                mainHandler.post { result.success(mapOf("success" to true, "packageName" to pkg, "destPath" to destFile.absolutePath, "fileName" to destFile.name)) }
            } catch (e: Exception) {
                mainHandler.post { result.error("BACKUP_ERROR", "Failed to backup: ${e.message}", null) }
            }
        }
    }
}
