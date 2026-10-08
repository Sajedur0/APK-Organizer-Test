package com.apkorganizer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.apkorganizer.R
import com.apkorganizer.data.ApkFile
import com.apkorganizer.data.ApkManager
import com.apkorganizer.data.ApkManagerException
import com.apkorganizer.services.AppUpdateService
import com.apkorganizer.services.DuplicateHandler
import com.apkorganizer.services.FileOperations
import com.apkorganizer.services.PreferencesService
import com.apkorganizer.services.RenamerService
import com.apkorganizer.services.ScannerService
import com.apkorganizer.ui.AppScreen
import com.apkorganizer.ui.ScreenIds
import com.apkorganizer.ui.dialogs.AboutDialog
import com.apkorganizer.ui.dialogs.SummaryDialog
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.theme.DeepOrange
import com.apkorganizer.ui.widgets.ApkSnackbarHost
import com.apkorganizer.ui.widgets.ApkListTile
import com.apkorganizer.ui.widgets.BottomSheetAction
import com.apkorganizer.ui.widgets.ConfirmDialog
import com.apkorganizer.ui.widgets.ConfirmRequest
import com.apkorganizer.ui.widgets.DetailRow
import com.apkorganizer.ui.widgets.DirectoryBrowserSheet
import com.apkorganizer.ui.widgets.DirectoryPickerRequest
import com.apkorganizer.ui.widgets.HexagonDotsLoading
import com.apkorganizer.ui.widgets.PermissionDeniedView
import com.apkorganizer.ui.widgets.PermissionRationaleDialog
import com.apkorganizer.ui.widgets.PermissionRationaleRequest
import com.apkorganizer.ui.widgets.SearchField
import com.apkorganizer.ui.widgets.SelectionBottomBar
import com.apkorganizer.ui.widgets.SnackbarController
import com.apkorganizer.ui.widgets.TextInputDialog
import com.apkorganizer.ui.widgets.withAlpha
import com.apkorganizer.utils.CancellationToken
import com.apkorganizer.utils.FormatUtil
import com.apkorganizer.utils.runParallel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Sort modes for the APK list, persisted through [PreferencesService]. */
enum class ApkSortMode(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Date"),
    VERSION("Version"),
}

/** Undo entry for a rename that can be reverted from a snackbar action. */
private class RenameUndoItem(
    val newPath: String,
    val originalPath: String,
    val originalName: String,
)

/** Mutable progress state driving the "Auto Organize" dialog. */
class OrganizeProgressState(val token: CancellationToken) {
    var done by mutableStateOf(0)
    var total by mutableStateOf(0)
    var phase by mutableStateOf("Renaming")
}

/** Data for the summary dialog shown after "Smart Organize". */
class SummaryData(
    val title: String,
    val stats: List<Pair<String, Int>>,
    val details: List<String>,
    val errors: List<String>,
)

/**
 * State holder for the home screen — a faithful port of `_HomePageState`.
 * All state lives behind Compose snapshot state; every mutation happens on
 * the main dispatcher, mirroring Flutter's single-isolate UI model.
 */
class HomeState(
    val scope: kotlinx.coroutines.CoroutineScope,
    val snackbar: SnackbarController,
    val appVersion: String,
    private val push: (AppScreen) -> Unit,
    private val exitApp: () -> Unit,
) {
    /** Master list in scan order (never re-sorted in place). */
    val allApkFiles = mutableStateListOf<ApkFile>()

    /** O(1) lookup/update maps so selection, rename, move and delete never scan the whole list. */
    private val apkIndex = HashMap<String, ApkFile>()
    private val positionByPath = HashMap<String, Int>()

    /** Sorted + filtered view that the list renders. */
    var filteredApkFiles by mutableStateOf<List<ApkFile>>(emptyList())
        private set

    var selectedPaths by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoading by mutableStateOf(false)
    var hasPermission by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var searchInput by mutableStateOf("")
    var searchQuery by mutableStateOf("")
        private set
    var isSearching by mutableStateOf(false)

    var sortMode by mutableStateOf(ApkSortMode.NAME)
        private set
    var sortAscending by mutableStateOf(true)
    var filterDirectory by mutableStateOf<String?>(null)

    // --- Live scan state -----------------------------------------------------
    var scanFoundCount by mutableStateOf(0)
        private set
    var scanDirectory by mutableStateOf("")
        private set
    var isDiscovering by mutableStateOf(true)
        private set
    private var stopRequested = false
    private var lastScanFinishedAt: Long? = null

    private val scanBuffer = mutableListOf<ApkFile>()
    private var scanFlushJob: Job? = null
    private var cachedDirectoryList: List<String>? = null
    private var cachedDirectoryListCount = -1
    private var searchDebounce: Job? = null

    private val scannerService = ScannerService()
    private val renamerService = RenamerService()
    private val duplicateHandler = DuplicateHandler()
    private val fileOperations = FileOperations()

    // --- UI requests ---------------------------------------------------------
    var confirmRequest by mutableStateOf<ConfirmRequest?>(null)
    var rationaleRequest by mutableStateOf<PermissionRationaleRequest?>(null)
    var directoryPicker by mutableStateOf<DirectoryPickerRequest?>(null)
    var renameDialogFor by mutableStateOf<ApkFile?>(null)
    var detailsSheetFor by mutableStateOf<ApkFile?>(null)
    var filterSheetVisible by mutableStateOf(false)
    var aboutVisible by mutableStateOf(false)
    var exitDialogVisible by mutableStateOf(false)
    var organizeProgress by mutableStateOf<OrganizeProgressState?>(null)
    var summaryData by mutableStateOf<SummaryData?>(null)

    init {
        loadSortPreferences()
    }

    private fun loadSortPreferences() {
        val savedIndex = PreferencesService.sortModeIndex
        if (savedIndex != null && savedIndex in ApkSortMode.entries.indices) {
            sortMode = ApkSortMode.entries[savedIndex]
        }
        sortAscending = PreferencesService.sortAscending
    }

    // --- permission helpers (port of PermissionUtils) ------------------------

    private suspend fun hasStoragePermission(): Boolean = try {
        ApkManager.checkStoragePermission()
    } catch (_: Exception) {
        false
    }

    private suspend fun requestStoragePermissionWithRationale(): Boolean = try {
        val result = ApkManager.requestStoragePermission()
        val status = result["status"] as? String
        val granted = result["granted"] as? Boolean ?: false
        if (!granted && status == "redirected_to_settings") {
            false
        } else {
            granted
        }
    } catch (_: Exception) {
        false
    }

    private suspend fun requestInstallPermission(): Boolean = try {
        val result = ApkManager.requestInstallPermission()
        result["granted"] as? Boolean ?: false
    } catch (_: Exception) {
        false
    }

    /** Shows the rationale dialog and runs [request] if the user accepts. */
    private suspend fun showRationale(
        title: String,
        message: String,
        request: suspend () -> Boolean,
    ): Boolean {
        val showAgain = suspendCancellableCoroutine { cont ->
            rationaleRequest = PermissionRationaleRequest(title, message) { accepted ->
                rationaleRequest = null
                if (cont.isActive) cont.resume(accepted)
            }
        }
        if (!showAgain) return false
        return request()
    }

    private suspend fun suspendConfirm(title: String, message: String): Boolean =
        suspendCancellableCoroutine { cont ->
            confirmRequest = ConfirmRequest(title, message, danger = true) { confirmed ->
                confirmRequest = null
                if (cont.isActive) cont.resume(confirmed)
            }
        }

    // --- lifecycle -----------------------------------------------------------

    fun onAppResumed() {
        scope.launch {
            try {
                val hasPerm = hasStoragePermission()
                if (!hasPerm) {
                    if (hasPermission) hasPermission = false
                    return@launch
                }

                if (!hasPermission) {
                    hasPermission = true
                    scanApkFilesInner()
                    return@launch
                }

                // Rescanning on every resume is wasteful; only refresh when
                // the list is empty and nothing ran recently.
                val lastScan = lastScanFinishedAt
                val recentlyScanned =
                    lastScan != null && System.currentTimeMillis() - lastScan < MIN_RESUME_RESCAN_GAP_MS
                if (allApkFiles.isEmpty() && !isLoading && !recentlyScanned) {
                    scanApkFilesInner()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun requestPermission() {
        scope.launch {
            isLoading = true
            try {
                ApkManager.requestStoragePermission()
                // The request resolves when the settings screen returns, so
                // re-check straight away instead of waiting on a timer.
                checkPermissionAndScanInner()
            } catch (e: ApkManagerException) {
                snackbar.show(e.message ?: "", isError = true)
            } finally {
                isLoading = false
            }
        }
    }

    fun checkPermissionAndScan() {
        scope.launch { checkPermissionAndScanInner() }
    }

    private suspend fun checkPermissionAndScanInner() {
        isLoading = true
        errorMessage = null
        try {
            val hasPerm = hasStoragePermission()
            hasPermission = hasPerm
            if (hasPerm) {
                scanApkFilesInner()
            } else {
                isLoading = false
            }
        } catch (e: ApkManagerException) {
            errorMessage = e.message
            isLoading = false
        }
    }

    // --- scanning ------------------------------------------------------------

    fun scanApkFiles() {
        scope.launch { scanApkFilesInner() }
    }

    private suspend fun scanApkFilesInner() {
        // Never start a second scan on top of a running one.
        if (scannerService.isScanning) return

        val hasPerm = hasStoragePermission()
        if (!hasPerm) {
            isLoading = false
            errorMessage = "Storage permission is required to scan APK files."
            return
        }

        scanFlushJob?.cancel()
        scanBuffer.clear()
        isLoading = true
        errorMessage = null
        stopRequested = false
        scanFoundCount = 0
        scanDirectory = ""
        isDiscovering = true
        selectedPaths = emptySet()
        allApkFiles.clear()
        apkIndex.clear()
        positionByPath.clear()
        filteredApkFiles = emptyList()

        try {
            val result = scannerService.scanAllStorage(
                isCancelled = { stopRequested },
                onProgress = { onScanProgress(it) },
            )

            // Apply whatever the final batch left in the buffer.
            flushScanBuffer()

            apkIndex.clear()
            positionByPath.clear()
            allApkFiles.clear()
            allApkFiles.addAll(result.allFiles)
            for (i in allApkFiles.indices) {
                val apk = allApkFiles[i]
                apkIndex[apk.path] = apk
                positionByPath[apk.path] = i
            }
            scanFoundCount = result.allFiles.size
            applyFilter()

            if (result.cancelled) {
                snackbar.show(
                    "Scan stopped — showing ${result.allFiles.size} file(s) found so far",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApkManagerException) {
            errorMessage = e.message
            snackbar.show(e.message ?: "", isError = true)
        } finally {
            scanFlushJob?.cancel()
            scanFlushJob = null
            lastScanFinishedAt = System.currentTimeMillis()
            isLoading = false
            scanDirectory = ""
        }
    }

    /** Receives throttled batches from the scanner and buffers them until the next UI flush. */
    private fun onScanProgress(progress: com.apkorganizer.services.ScanProgress) {
        scanFoundCount = progress.filesFound
        if (progress.currentDirectory.isNotEmpty()) {
            scanDirectory = progress.currentDirectory
        }
        isDiscovering = progress.isDiscovering
        if (progress.apks.isNotEmpty()) scanBuffer.addAll(progress.apks)

        if (scanFlushJob?.isActive == true) return
        scanFlushJob = scope.launch {
            delay(SCAN_FLUSH_INTERVAL_MS)
            scanFlushJob = null
            flushScanBuffer()
        }
    }

    private fun flushScanBuffer() {
        if (scanBuffer.isEmpty()) return
        for (apk in scanBuffer) {
            upsert(apk)
        }
        scanBuffer.clear()
        applyFilter()
    }

    private fun upsert(apk: ApkFile) {
        if (apk.path.isEmpty()) return
        val position = positionByPath[apk.path]
        if (position == null) {
            positionByPath[apk.path] = allApkFiles.size
            allApkFiles.add(apk)
        } else {
            allApkFiles[position] = apk
        }
        apkIndex[apk.path] = apk
    }

    /** Replaces the entry at [oldPath] with [apk] (used after rename/move). */
    private fun replaceEntry(oldPath: String, apk: ApkFile) {
        val position = positionByPath.remove(oldPath)
        apkIndex.remove(oldPath)
        if (position != null) {
            allApkFiles[position] = apk
            positionByPath[apk.path] = position
        } else {
            positionByPath[apk.path] = allApkFiles.size
            allApkFiles.add(apk)
        }
        apkIndex[apk.path] = apk
        if (selectedPaths.contains(oldPath)) {
            selectedPaths = buildSet {
                addAll(selectedPaths.filter { it != oldPath })
                add(apk.path)
            }
        }
    }

    /** Removes [path] from every in-memory structure. */
    private fun removeEntry(path: String) {
        val position = positionByPath.remove(path)
        apkIndex.remove(path)
        if (position != null && position < allApkFiles.size) {
            if (position == allApkFiles.size - 1) {
                allApkFiles.removeAt(position)
            } else {
                allApkFiles.removeAt(position)
                for (i in position until allApkFiles.size) {
                    positionByPath[allApkFiles[i].path] = i
                }
            }
        }
        if (selectedPaths.contains(path)) {
            selectedPaths = selectedPaths - path
        }
    }

    // --- filtering / sorting -------------------------------------------------

    fun applyFilter() {
        if (selectedPaths.isNotEmpty()) {
            selectedPaths = selectedPaths.filterTo(LinkedHashSet()) { apkIndex.containsKey(it) }
        }

        val query = searchQuery.trim().lowercase()
        val directory = filterDirectory
        val hasQuery = query.isNotEmpty()
        val hasDirectory = !directory.isNullOrEmpty()

        val result: MutableList<ApkFile> =
            if (!hasQuery && !hasDirectory) {
                allApkFiles.toMutableList()
            } else {
                val filtered = mutableListOf<ApkFile>()
                for (apk in allApkFiles) {
                    if (hasQuery && !apk.searchLower.contains(query)) continue
                    if (hasDirectory && !apk.path.startsWith(directory!!)) continue
                    filtered.add(apk)
                }
                filtered
            }

        sortFiles(result)
        filteredApkFiles = result
    }

    fun onSearchChanged(value: String) {
        searchInput = value
        searchDebounce?.cancel()
        searchDebounce = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            searchQuery = value
            applyFilter()
        }
    }

    fun clearSearch() {
        searchDebounce?.cancel()
        searchQuery = ""
        searchInput = ""
        applyFilter()
    }

    private fun sortFiles(files: MutableList<ApkFile>) {
        val comparator = when (sortMode) {
            ApkSortMode.NAME -> ApkFile.compareByDisplayName
            ApkSortMode.SIZE -> ApkFile.compareBySize
            ApkSortMode.DATE -> ApkFile.compareByDate
            ApkSortMode.VERSION -> ApkFile.compareByVersion
        }
        files.sortWith(if (sortAscending) comparator else comparator.reversed())
    }

    fun onSortSelected(mode: ApkSortMode) {
        if (sortMode == mode) {
            sortAscending = !sortAscending
        } else {
            sortMode = mode
            sortAscending = true
        }
        applyFilter()
        PreferencesService.setSort(sortMode.ordinal, sortAscending)
    }

    fun onFilterSelected(selected: String?) {
        filterDirectory = selected
        applyFilter()
    }

    fun directoryListForFilter(): List<String> {
        // Reuse the cached list while the scan set is unchanged.
        if (cachedDirectoryList == null || cachedDirectoryListCount != allApkFiles.size) {
            val dirs = allApkFiles.mapTo(LinkedHashSet()) { it.directory }.toSortedSet()
            cachedDirectoryList = dirs.toList()
            cachedDirectoryListCount = allApkFiles.size
        }
        return cachedDirectoryList ?: emptyList()
    }

    // --- install -------------------------------------------------------------

    private suspend fun installApk(apk: ApkFile, silent: Boolean = false) {
        val canInstall = try {
            ApkManager.canInstallPackages()
        } catch (_: Exception) {
            false
        }
        if (!canInstall) {
            val granted = showRationale(
                title = "Install Permission Required",
                message = "APK Organizer needs permission to install APK files on your device.",
                request = { requestInstallPermission() },
            )
            if (!granted) {
                snackbar.show("Install permission denied.", isError = true)
                return
            }
        }
        try {
            val result = ApkManager.installApk(apk.path)
            val status = result["status"] as? String ?: ""
            if (status == "redirected_to_settings") {
                snackbar.show("Please enable \"Install unknown apps\" and try again.")
            } else if (!silent) {
                snackbar.show("Installation started for ${apk.displayName}")
            }
        } catch (e: ApkManagerException) {
            if (!silent) snackbar.show(e.message ?: "", isError = true)
            throw e
        }
    }

    fun installApk(apk: ApkFile) {
        scope.launch { installApk(apk, silent = false) }
    }

    fun installSelectedApks() {
        if (selectedPaths.isEmpty()) return
        scope.launch {
            val apps = selectedPaths.mapNotNull { apkByPath(it) }
            if (apps.isEmpty()) return@launch

            var started = 0
            var failed = 0
            val failedNames = mutableListOf<String>()
            for (i in apps.indices) {
                // Android shows one installer at a time, so the intents are
                // spaced out instead of fired back to back.
                if (i > 0) {
                    delay(INSTALL_SPACING_MS)
                }
                try {
                    installApk(apps[i], silent = true)
                    started++
                } catch (_: Exception) {
                    failed++
                    if (failedNames.size < 3) failedNames.add(apps[i].displayName)
                }
            }
            val failedLabel =
                if (failedNames.isEmpty()) {
                    ""
                } else {
                    ", $failed failed (${failedNames.joinToString(", ")}" +
                        "${if (failed > failedNames.size) "…" else ""})"
                }
            snackbar.show(
                "$started installation(s) started$failedLabel",
                isError = failed > 0 && started == 0,
            )
        }
    }

    // --- delete --------------------------------------------------------------

    fun deleteApk(apk: ApkFile) {
        scope.launch {
            val hasPerm = hasStoragePermission()
            if (!hasPerm) {
                val granted = showRationale(
                    title = "Storage Permission Required",
                    message = "APK Organizer needs access to your device's storage to delete APK files.",
                    request = { requestStoragePermissionWithRationale() },
                )
                if (!granted) {
                    snackbar.show("Storage permission denied.", isError = true)
                    return@launch
                }
            }
            val confirmed = suspendConfirm(
                "Delete APK",
                "Are you sure you want to delete \"${apk.displayName}\"?",
            )
            if (!confirmed) return@launch
            try {
                ApkManager.deleteApk(apk.path)
                removeEntry(apk.path)
                applyFilter()
                snackbar.show("${apk.displayName} deleted")
            } catch (e: ApkManagerException) {
                snackbar.show(e.message ?: "", isError = true)
            }
        }
    }

    fun deleteSelectedApks() {
        if (selectedPaths.isEmpty()) return
        scope.launch {
            val hasPerm = hasStoragePermission()
            if (!hasPerm) {
                val granted = showRationale(
                    title = "Storage Permission Required",
                    message = "APK Organizer needs access to your device's storage to delete APK files.",
                    request = { requestStoragePermissionWithRationale() },
                )
                if (!granted) {
                    snackbar.show("Storage permission denied.", isError = true)
                    return@launch
                }
            }
            val confirmed = suspendConfirm(
                "Delete Selected",
                "Are you sure you want to delete ${selectedPaths.size} APK file(s)?",
            )
            if (!confirmed) return@launch
            val paths = selectedPaths.toList()
            val failed = mutableListOf<String>()
            for (path in paths) {
                try {
                    ApkManager.deleteApk(path)
                } catch (_: ApkManagerException) {
                    failed.add(path)
                }
            }
            for (path in paths) {
                if (failed.contains(path)) continue
                removeEntry(path)
            }
            selectedPaths = emptySet()
            applyFilter()
            when {
                failed.isEmpty() -> snackbar.show("${paths.size} APK file(s) deleted")
                failed.size == paths.size ->
                    snackbar.show("Could not delete the selected file(s)", isError = true)
                else -> snackbar.show(
                    "${paths.size - failed.size} deleted, ${failed.size} failed to delete",
                    isError = true,
                )
            }
        }
    }

    // --- rename --------------------------------------------------------------

    fun autoRenameApk(apk: ApkFile) {
        scope.launch {
            val hasPerm = hasStoragePermission()
            if (!hasPerm) {
                val granted = showRationale(
                    title = "Storage Permission Required",
                    message = "APK Organizer needs access to your device's storage to rename APK files.",
                    request = { requestStoragePermissionWithRationale() },
                )
                if (!granted) {
                    snackbar.show("Storage permission denied.", isError = true)
                    return@launch
                }
            }
            if (!apk.needsRename) {
                snackbar.show("${apk.fileName} already follows AppName_Version.apk")
                return@launch
            }
            val newName = apk.suggestedRename
            try {
                val result = ApkManager.renameApk(apk.path, newName)
                val oldName = apk.fileName
                val newPath = result["newPath"] as? String
                val newNameResult = result["newName"] as? String
                applyRenameResult(apk.path, result)
                snackbar.show(
                    "Renamed to ${newNameResult ?: newName}",
                    actionLabel = "Undo",
                    onAction = {
                        if (newPath != null) {
                            scope.launch {
                                try {
                                    ApkManager.renameApk(newPath, oldName)
                                    applyRenameResultFrom(newPath, apk.path, oldName)
                                    snackbar.show("Renamed back")
                                } catch (e: ApkManagerException) {
                                    snackbar.show(e.message ?: "", isError = true)
                                }
                            }
                        }
                    },
                    durationMs = UNDO_SNACKBAR_MS,
                )
            } catch (e: ApkManagerException) {
                snackbar.show(e.message ?: "", isError = true)
            }
        }
    }

    fun manualRenameApk(apk: ApkFile) {
        scope.launch {
            val hasPerm = hasStoragePermission()
            if (!hasPerm) {
                val granted = showRationale(
                    title = "Storage Permission Required",
                    message = "APK Organizer needs access to your device's storage to rename APK files.",
                    request = { requestStoragePermissionWithRationale() },
                )
                if (!granted) {
                    snackbar.show("Storage permission denied.", isError = true)
                    return@launch
                }
            }
            renameDialogFor = apk
        }
    }

    fun submitManualRename(apk: ApkFile, newName: String) {
        scope.launch {
            if (newName.trim().isEmpty()) return@launch
            if (newName.trim().lowercase() == apk.fileName.lowercase()) {
                snackbar.show("File name is unchanged")
                return@launch
            }
            try {
                val result = ApkManager.renameApk(apk.path, newName.trim())
                applyRenameResult(apk.path, result)
                val applied = result["newName"] as? String ?: newName.trim()
                snackbar.show("Renamed to $applied")
            } catch (e: ApkManagerException) {
                snackbar.show(e.message ?: "", isError = true)
            }
        }
    }

    private fun applyRenameResult(oldPath: String, result: Map<String, Any?>): String? {
        val newPath = result["newPath"] as? String
        val newName = result["newName"] as? String
        if (newPath == null || newName == null) return null
        val existing = apkIndex[oldPath] ?: return newPath
        replaceEntry(oldPath, existing.copyWith(path = newPath, fileName = newName))
        applyFilter()
        return newPath
    }

    private fun applyRenameResultFrom(newPath: String, originalPath: String, originalName: String) {
        val existing = apkIndex[newPath] ?: return
        replaceEntry(newPath, existing.copyWith(path = originalPath, fileName = originalName))
        applyFilter()
    }

    fun renameSelectedApksAuto() {
        if (selectedPaths.isEmpty()) return
        scope.launch {
            val hasPerm = hasStoragePermission()
            if (!hasPerm) {
                val granted = showRationale(
                    title = "Storage Permission Required",
                    message = "APK Organizer needs access to your device's storage to rename APK files.",
                    request = { requestStoragePermissionWithRationale() },
                )
                if (!granted) {
                    snackbar.show("Storage permission denied.", isError = true)
                    return@launch
                }
            }
            val confirmed = suspendConfirm(
                "Auto Rename",
                "Rename ${selectedPaths.size} APK file(s) to AppName_Version.apk?\n\n" +
                    "Files that already follow this format are skipped.",
            )
            if (!confirmed) return@launch
            val selected = selectedPaths.mapNotNull { apkByPath(it) }
            val summary = renamerService.autoRenameAll(selected)
            for (r in summary.results) {
                if (!r.success || r.newPath == null || r.newName == null) continue
                val existing = apkIndex[r.originalPath] ?: continue
                replaceEntry(
                    r.originalPath,
                    existing.copyWith(path = r.newPath, fileName = r.newName),
                )
            }
            selectedPaths = emptySet()
            applyFilter()
            val unchanged = summary.total - summary.succeeded - summary.failed
            val undoItems = summary.results
                .filter { it.success && it.newPath != null && it.originalPath.isNotEmpty() }
                .map {
                    RenameUndoItem(
                        newPath = it.newPath!!,
                        originalPath = it.originalPath,
                        originalName = FormatUtil.fileName(it.originalPath),
                    )
                }
            snackbar.show(
                "${summary.succeeded} file(s) renamed" +
                    "${if (unchanged > 0) ", $unchanged already named" else ""}" +
                    "${if (summary.failed > 0) ", ${summary.failed} failed" else ""}",
                actionLabel = if (undoItems.isNotEmpty()) "Undo" else null,
                onAction = { undoRename(undoItems) },
                durationMs = UNDO_SNACKBAR_MS,
            )
        }
    }

    private fun undoRename(items: List<RenameUndoItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val results = arrayOfNulls<String?>(items.size)
            runParallel(items.size, 3) { index ->
                val item = items[index]
                try {
                    val result = ApkManager.renameApk(item.newPath, item.originalName)
                    results[index] = result["newPath"] as? String ?: item.originalPath
                } catch (_: ApkManagerException) {
                    results[index] = null
                }
            }
            var failed = 0
            for (i in items.indices) {
                val restoredPath = results[i]
                if (restoredPath == null) {
                    failed++
                    continue
                }
                val existing = apkIndex[items[i].newPath] ?: continue
                replaceEntry(
                    items[i].newPath,
                    existing.copyWith(path = restoredPath, fileName = FormatUtil.fileName(restoredPath)),
                )
            }
            applyFilter()
            snackbar.show(
                if (failed == 0) "Rename undone" else "$failed file(s) failed to restore",
                isError = failed > 0,
            )
        }
    }

    // --- move ----------------------------------------------------------------

    fun moveApk(apk: ApkFile) {
        showMoveDialog(listOf(apk.path))
    }

    fun moveSelectedApks() {
        if (selectedPaths.isEmpty()) return
        showMoveDialog(selectedPaths.toList())
    }

    private fun showMoveDialog(paths: List<String>) {
        scope.launch {
            val hasPerm = hasStoragePermission()
            if (!hasPerm) {
                val granted = showRationale(
                    title = "Storage Permission Required",
                    message = "APK Organizer needs access to your device's storage to move APK files.",
                    request = { requestStoragePermissionWithRationale() },
                )
                if (!granted) {
                    snackbar.show("Storage permission denied.", isError = true)
                    return@launch
                }
            }
            try {
                val roots = ApkManager.getDirectories()
                directoryPicker = DirectoryPickerRequest(roots) { selectedDir ->
                    directoryPicker = null
                    if (selectedDir != null) performMove(paths, selectedDir)
                }
            } catch (e: ApkManagerException) {
                snackbar.show(e.message ?: "", isError = true)
            }
        }
    }

    private fun performMove(paths: List<String>, destDir: String) {
        scope.launch {
            try {
                val summary = fileOperations.batchMove(paths, destDir)
                for (r in summary.results) {
                    val newPath = r.destPath ?: continue
                    if (!r.success || r.skipped) continue
                    val existing = apkIndex[r.sourcePath] ?: continue
                    replaceEntry(
                        r.sourcePath,
                        existing.copyWith(path = newPath, fileName = FormatUtil.fileName(newPath)),
                    )
                }
                selectedPaths = emptySet()
                applyFilter()

                val undoMap = LinkedHashMap<String, String>()
                for (r in summary.results) {
                    val dest = r.destPath
                    if (r.success && !r.skipped && dest != null) {
                        undoMap[dest] = r.sourcePath
                    }
                }
                snackbar.show(
                    summary.describe(),
                    actionLabel = if (undoMap.isNotEmpty()) "Undo" else null,
                    onAction = { undoMove(undoMap) },
                    durationMs = UNDO_SNACKBAR_MS,
                )
            } catch (e: ApkManagerException) {
                snackbar.show(e.message ?: "", isError = true)
            }
        }
    }

    private fun undoMove(undoMap: Map<String, String>) {
        if (undoMap.isEmpty()) return
        scope.launch {
            val entries = undoMap.entries.toList()
            val restoredPaths = arrayOfNulls<String?>(entries.size)
            runParallel(entries.size, 3) { index ->
                val entry = entries[index]
                try {
                    val result = ApkManager.moveApk(entry.key, FormatUtil.parentPath(entry.value))
                    restoredPaths[index] = result["destPath"] as? String
                } catch (_: ApkManagerException) {
                    restoredPaths[index] = null
                }
            }
            var failed = 0
            for (i in entries.indices) {
                val restoredPath = restoredPaths[i]
                if (restoredPath == null) {
                    failed++
                    continue
                }
                val existing = apkIndex[entries[i].key] ?: continue
                replaceEntry(
                    entries[i].key,
                    existing.copyWith(path = restoredPath, fileName = FormatUtil.fileName(restoredPath)),
                )
            }
            applyFilter()
            snackbar.show(
                if (failed == 0) "Move undone" else "$failed file(s) failed to restore",
                isError = failed > 0,
            )
        }
    }

    // --- selection -----------------------------------------------------------

    fun toggleSelection(apk: ApkFile) {
        selectedPaths =
            if (selectedPaths.contains(apk.path)) {
                selectedPaths - apk.path
            } else {
                selectedPaths + apk.path
            }
    }

    val allSelected: Boolean
        get() = filteredApkFiles.isNotEmpty() && selectedPaths.size == filteredApkFiles.size

    fun selectAll() {
        selectedPaths =
            if (allSelected) {
                emptySet()
            } else {
                filteredApkFiles.mapTo(LinkedHashSet()) { it.path }
            }
    }

    fun clearSelection() {
        selectedPaths = emptySet()
    }

    fun apkByPath(path: String): ApkFile? = apkIndex[path]

    // --- smart organize ------------------------------------------------------

    fun smartOrganize() {
        if (!hasPermission) {
            snackbar.show(
                "Storage permission required to organize APK files.",
                isError = true,
            )
            return
        }
        if (allApkFiles.isEmpty()) {
            snackbar.show("No APK files found to organize.")
            return
        }
        scope.launch {
            val confirmed = suspendConfirm(
                "Auto Organize",
                "This will:\n" +
                    "1. Auto-rename all APK files to AppName_VersionName.apk\n" +
                    "2. Remove duplicate files (keeping the newest)\n\n" +
                    "Continue with ${allApkFiles.size} file(s)?",
            )
            if (!confirmed) return@launch
            runSmartOrganize()
        }
    }

    private suspend fun runSmartOrganize() {
        val token = CancellationToken()
        val progress = OrganizeProgressState(token)
        organizeProgress = progress
        val initialCount = allApkFiles.size

        try {
            // 1. Parallel rename (bounded worker pool, cancellable, progress).
            val renameSummary = renamerService.autoRenameAll(
                allApkFiles.toList(),
                onProgress = { done, total ->
                    progress.done = done
                    progress.total = total
                    progress.phase = "Renaming"
                },
                isCancelled = { token.isCancelled },
            )

            for (r in renameSummary.results) {
                if (!r.success) continue
                val newPath = r.newPath
                val newName = r.newName
                if (newPath == null || newName == null) continue
                val existing = apkIndex[r.originalPath] ?: continue
                replaceEntry(
                    r.originalPath,
                    existing.copyWith(path = newPath, fileName = newName),
                )
            }
            applyFilter()

            // 2. Remove duplicates (separate pass so the keep decision and the
            //    on-disk files can never race).
            progress.done = 0
            progress.total = 0
            progress.phase = "Removing duplicates"
            val dupSummary = duplicateHandler.removeDuplicates(
                allApkFiles.toList(),
                onProgress = { done, total ->
                    progress.done = done
                    progress.total = total
                    progress.phase = "Removing duplicates"
                },
                isCancelled = { token.isCancelled },
            )

            for (path in dupSummary.deletedPaths) {
                removeEntry(path)
            }
            selectedPaths = emptySet()
            applyFilter()

            organizeProgress = null
            summaryData = SummaryData(
                title = "Auto Organize Complete",
                stats = listOf(
                    "Total Files Scanned" to initialCount,
                    "Files Renamed" to renameSummary.succeeded,
                    "Rename Failures" to renameSummary.failed,
                    "Duplicate Groups" to dupSummary.duplicateGroups,
                    "Duplicates Removed" to dupSummary.filesDeleted,
                ),
                details = buildList {
                    if (renameSummary.succeeded > 0) {
                        add("Renamed ${renameSummary.succeeded} file(s)")
                    }
                    if (renameSummary.skipped > 0) {
                        add("${renameSummary.skipped} file(s) already had the correct name")
                    }
                    if (dupSummary.filesDeleted > 0) {
                        add("Removed ${dupSummary.filesDeleted} duplicate(s)")
                    }
                    if (dupSummary.errors.isNotEmpty()) {
                        add("${dupSummary.errors.size} duplicate(s) could not be removed")
                    }
                },
                errors = buildList {
                    addAll(
                        renameSummary.results
                            .filter { !it.success }
                            .map { "Rename ${it.originalPath.substringAfterLast('/')}: ${it.error}" },
                    )
                    addAll(dupSummary.errors)
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            organizeProgress = null
            snackbar.show("Smart organize failed: $e", isError = true)
        }
    }

    fun stopScan() {
        if (!scannerService.isScanning) return
        stopRequested = true
        snackbar.show("Stopping scan…")
    }

    // --- misc actions --------------------------------------------------------

    fun showApkDetailsBottomSheet(apk: ApkFile) {
        detailsSheetFor = apk
    }

    fun showApkDetailPage(apk: ApkFile) {
        push(AppScreen.ApkDetail(ScreenIds.next(), apk.path, apk.displayName))
    }

    fun showInstalledApps() {
        push(AppScreen.InstalledApps(ScreenIds.next(), includeSystem = false, title = "Installed Apps"))
    }

    fun showSystemApps() {
        push(AppScreen.InstalledApps(ScreenIds.next(), includeSystem = true, title = "System Apps"))
    }

    fun showPrivacyPolicy() {
        push(AppScreen.PrivacyPolicy(ScreenIds.next(), appVersion))
    }

    fun showAbout() {
        aboutVisible = true
    }

    fun shareApkFiles(apks: List<ApkFile>, context: android.content.Context) {
        scope.launch {
            val valid = apks.filter {
                withContext(Dispatchers.IO) {
                    try {
                        File(it.path).exists()
                    } catch (_: Exception) {
                        false
                    }
                }
            }
            if (valid.isEmpty()) {
                snackbar.show("No valid APK file(s) to share.", isError = true)
                return@launch
            }
            val text =
                if (valid.size == 1) {
                    "Sharing ${valid.first().displayName}"
                } else {
                    "Sharing ${valid.size} APK files"
                }
            try {
                val ok = ApkManager.shareApkFiles(context, valid.map { it.path }, text)
                if (!ok) {
                    snackbar.show("Failed to share files", isError = true)
                }
            } catch (e: Exception) {
                snackbar.show("Failed to share: $e", isError = true)
            }
        }
    }

    fun shareSelectedApks(context: android.content.Context) {
        if (selectedPaths.isEmpty()) return
        val apks = selectedPaths.mapNotNull { apkByPath(it) }
        shareApkFiles(apks, context)
    }

    fun shareApp(context: android.content.Context) {
        val playStoreUrl = "https://play.google.com/store/apps/details?id=com.apkorganizer"
        ApkManager.shareText(
            context,
            "Check out APK Organizer - Manage and organize your APK files!\n\n$playStoreUrl",
        )
    }

    fun rateUs(context: android.content.Context) {
        val url = "https://play.google.com/store/apps/details?id=com.apkorganizer"
        val ok = ApkManager.openUrl(context, url)
        if (!ok) {
            snackbar.show("Could not open Play Store", isError = true)
        }
    }

    fun toggleTheme() {
        val prefs = PreferencesService
        val next =
            if (prefs.themeMode.value == PreferencesService.ThemeMode.DARK) {
                PreferencesService.ThemeMode.LIGHT
            } else {
                PreferencesService.ThemeMode.DARK
            }
        prefs.setThemeMode(next)
    }

    fun requestExit() {
        exitDialogVisible = true
    }

    fun confirmExit() {
        exitDialogVisible = false
        exitApp()
    }

    fun dismissDetailsSheet() {
        detailsSheetFor = null
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 220L
        private const val SCAN_FLUSH_INTERVAL_MS = 130L
        private const val MIN_RESUME_RESCAN_GAP_MS = 20_000L
        private const val INSTALL_SPACING_MS = 900L
        private const val UNDO_SNACKBAR_MS = 6_000L
    }
}

/** The home screen — port of the Flutter `HomePage`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    appVersion: String,
    push: (AppScreen) -> Unit,
    exitApp: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarController(scope) }
    val state = remember { HomeState(scope, snackbar, appVersion, push, exitApp) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val themeMode by PreferencesService.themeMode.collectAsStateSafe()
    val isDark = themeMode == PreferencesService.ThemeMode.DARK

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Initial permission check + scan, and the In-App Update prompt after the
    // first frame (mirroring initState + addPostFrameCallback).
    LaunchedEffect(Unit) {
        state.checkPermissionAndScan()
        AppUpdateService.checkAndPromptUpdate()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                appVersion = appVersion,
                apkCount = state.allApkFiles.size,
                isDark = isDark,
                onThemeToggle = { state.toggleTheme() },
                onItemSelected = { route ->
                    scope.launch { drawerState.close() }
                    onDrawerItemSelected(state, route, context)
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (state.isSearching) {
                            SearchField(
                                value = state.searchInput,
                                onValueChange = { state.onSearchChanged(it) },
                                hint = "Search APKs...",
                                modifier = Modifier.fillMaxWidth(),
                                autofocus = true,
                            )
                        } else if (state.isLoading) {
                            Box(modifier = Modifier.size(20.dp)) {
                                HexagonDotsLoading(minRadius = 4.dp)
                            }
                        }
                    },
                    actions = {
                        ApkCountBadge(count = state.allApkFiles.size)
                        if (state.isSearching) {
                            IconButton(
                                onClick = {
                                    state.clearSearch()
                                    state.isSearching = false
                                },
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        } else if (state.allApkFiles.isNotEmpty()) {
                            IconButton(
                                onClick = { state.toggleTheme() },
                            ) {
                                Icon(
                                    if (isDark) {
                                        Icons.Rounded.LightMode
                                    } else {
                                        Icons.Rounded.DarkMode
                                    },
                                    contentDescription = if (isDark) "Light Mode" else "Dark Mode",
                                )
                            }
                            SortMenuButton(state)
                            IconButton(
                                onClick = {
                                    if (state.allApkFiles.isNotEmpty()) state.filterSheetVisible = true
                                },
                            ) {
                                Icon(
                                    Icons.Filled.FilterList,
                                    contentDescription = "Filter by directory",
                                    tint = if (state.filterDirectory != null) scheme.primary else scheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(onClick = { state.isSearching = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = scheme.surface,
                        scrolledContainerColor = scheme.surface,
                    ),
                )
            },
            bottomBar = {
                if (state.selectedPaths.isNotEmpty()) {
                    SelectionBottomBar(
                        selectedCount = state.selectedPaths.size,
                        totalCount = state.filteredApkFiles.size,
                        onSelectAll = { state.selectAll() },
                        onClearSelection = { state.clearSelection() },
                        onInstall = { state.installSelectedApks() },
                        onDelete = { state.deleteSelectedApks() },
                        onAutoRename = { state.renameSelectedApksAuto() },
                        onMove = { state.moveSelectedApks() },
                        onShare = { state.shareSelectedApks(context) },
                    )
                }
            },
            snackbarHost = { ApkSnackbarHost(snackbar) },
            floatingActionButton = {
                if (state.hasPermission && state.selectedPaths.isEmpty() && !state.isSearching) {
                    if (state.isLoading) {
                        ExtendedFloatingActionButton(
                            onClick = { state.stopScan() },
                            containerColor = scheme.errorContainer,
                            contentColor = scheme.onErrorContainer,
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop")
                        }
                    } else {
                        ExtendedFloatingActionButton(
                            onClick = { state.scanApkFiles() },
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan Now")
                        }
                    }
                }
            },
            containerColor = scheme.surface,
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                HomeBody(state = state, context = context)
            }
        }
    }

    // ---- Overlays -----------------------------------------------------------

    state.confirmRequest?.let { request ->
        ConfirmDialog(
            title = request.title,
            message = request.message,
            confirmLabel = request.confirmLabel,
            danger = request.danger,
            onResult = request.onResult,
        )
    }

    state.rationaleRequest?.let { request ->
        PermissionRationaleDialog(
            title = request.title,
            message = request.message,
            onResult = request.onResult,
        )
    }

    state.directoryPicker?.let { picker ->
        DirectoryBrowserSheet(
            initialDirectories = picker.initialDirectories,
            onSelect = picker.onSelect,
            onMessage = { message, isError -> snackbar.show(message, isError = isError) },
        )
    }

    state.renameDialogFor?.let { apk ->
        TextInputDialog(
            title = "Rename APK",
            label = "New file name",
            hint = "e.g., MyApp_1.0.apk",
            initialText = apk.suggestedRename,
            confirmLabel = "Rename",
            onDismiss = { state.renameDialogFor = null },
            onConfirm = { name ->
                state.renameDialogFor = null
                state.submitManualRename(apk, name)
            },
        )
    }

    state.detailsSheetFor?.let { apk ->
        ApkDetailsBottomSheet(
            apk = apk,
            onDismiss = { state.dismissDetailsSheet() },
            onDetails = {
                state.dismissDetailsSheet()
                state.showApkDetailPage(apk)
            },
            onInstall = {
                state.dismissDetailsSheet()
                state.installApk(apk)
            },
            onAutoRename = {
                state.dismissDetailsSheet()
                state.autoRenameApk(apk)
            },
            onManualRename = {
                state.dismissDetailsSheet()
                state.manualRenameApk(apk)
            },
            onMove = {
                state.dismissDetailsSheet()
                state.moveApk(apk)
            },
            onShare = {
                state.dismissDetailsSheet()
                state.shareApkFiles(listOf(apk), context)
            },
            onDelete = {
                state.dismissDetailsSheet()
                state.deleteApk(apk)
            },
        )
    }

    if (state.filterSheetVisible) {
        DirectoryFilterSheet(
            directories = state.directoryListForFilter(),
            currentFilter = state.filterDirectory,
            onSelect = { selected ->
                state.filterSheetVisible = false
                if (selected != null) {
                    state.onFilterSelected(selected)
                }
            },
        )
    }

    if (state.aboutVisible) {
        AboutDialog(appVersion = appVersion, onDismiss = { state.aboutVisible = false })
    }

    if (state.exitDialogVisible) {
        ConfirmDialog(
            title = "Exit",
            message = "Are you sure you want to exit?",
            confirmLabel = "Exit",
            onResult = { confirmed ->
                state.exitDialogVisible = false
                if (confirmed) state.confirmExit()
            },
        )
    }

    state.organizeProgress?.let { progress ->
        OrganizeProgressDialog(progress)
    }

    state.summaryData?.let { data ->
        SummaryDialog(
            title = data.title,
            stats = data.stats,
            details = data.details,
            errors = data.errors,
            onDismiss = { state.summaryData = null },
        )
    }
}

private fun onDrawerItemSelected(
    state: HomeState,
    route: String,
    context: android.content.Context,
) {
    when (route) {
        "apk_manager" -> {}
        "smart_organize" -> state.smartOrganize()
        "installed" -> state.showInstalledApps()
        "app_system" -> state.showSystemApps()
        "theme" -> state.toggleTheme()
        "rate_us" -> state.rateUs(context)
        "privacy_policy" -> state.showPrivacyPolicy()
        "about" -> state.showAbout()
        "share" -> state.shareApp(context)
        "exit" -> state.requestExit()
    }
}

/** Collects a StateFlow without the compose-runtime-collectAsState dependency split. */
@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateSafe(): androidx.compose.runtime.State<T> =
    androidx.compose.runtime.collectAsState(this)

@Composable
private fun SortMenuButton(state: HomeState) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    val sortIcons = mapOf(
        ApkSortMode.NAME to Icons.Filled.SortByAlpha,
        ApkSortMode.SIZE to Icons.Outlined.Storage,
        ApkSortMode.DATE to Icons.Filled.Schedule,
        ApkSortMode.VERSION to Icons.Outlined.Tag,
    )

    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                sortIcons[state.sortMode] ?: Icons.Filled.SortByAlpha,
                contentDescription = "Sort: ${state.sortMode.label}",
                modifier = Modifier.size(20.dp),
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(AppRadius.control),
        ) {
            ApkSortMode.entries.forEach { mode ->
                val selected = state.sortMode == mode
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                sortIcons[mode] ?: Icons.Filled.SortByAlpha,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                mode.label,
                                color = if (selected) scheme.primary else scheme.onSurface,
                                fontWeight = if (selected) FontWeight.W600 else null,
                            )
                            Spacer(Modifier.weight(1f))
                            if (selected) {
                                Icon(
                                    if (state.sortAscending) {
                                        Icons.Filled.ArrowUpward
                                    } else {
                                        Icons.Filled.ArrowDownward
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = scheme.primary,
                                )
                            }
                        }
                    },
                    onClick = {
                        menuOpen = false
                        state.onSortSelected(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun ApkCountBadge(count: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .height(32.dp)
            .background(scheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.FolderZip,
            contentDescription = null,
            tint = scheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onPrimaryContainer,
            fontWeight = FontWeight.W700,
        )
    }
}

@Composable
private fun HomeBody(state: HomeState, context: android.content.Context) {
    val scheme = MaterialTheme.colorScheme

    if (!state.hasPermission && !state.isLoading) {
        PermissionDeniedView(onRequestPermission = { state.requestPermission() })
        return
    }

    if (state.errorMessage != null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = scheme.error,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                state.errorMessage ?: "",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { state.checkPermissionAndScan() }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
        return
    }

    if (state.filteredApkFiles.isEmpty()) {
        if (state.isLoading) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                HexagonDotsLoading(minRadius = 10.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    if (state.isDiscovering) "Searching storage…" else "Reading APK files…",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurfaceVariant,
                )
                if (state.scanFoundCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${state.scanFoundCount} found so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.outline,
                    )
                }
                if (state.scanDirectory.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        state.scanDirectory,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.outline,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = { state.stopScan() }) {
                    Icon(Icons.Outlined.StopCircleCompat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = scheme.outline,
                    modifier = Modifier.size(80.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (state.searchQuery.isNotEmpty()) {
                        "No APK files match your search"
                    } else {
                        "No APK files found"
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant,
                )
                if (state.searchQuery.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap Scan Now to look for APK files on this device",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { state.scanApkFiles() }) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Now")
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (state.isLoading) {
            ScanProgressBar(state)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 4.dp,
                bottom = 88.dp,
            ),
        ) {
            items(state.filteredApkFiles, key = { it.path }) { apk ->
                ApkListTile(
                    apk = apk,
                    isSelected = state.selectedPaths.contains(apk.path),
                    onTap = {
                        if (state.selectedPaths.isNotEmpty()) {
                            state.toggleSelection(apk)
                        } else {
                            state.showApkDetailsBottomSheet(apk)
                        }
                    },
                    onLongPress = { state.toggleSelection(apk) },
                    onInstall = { state.installApk(apk) },
                    onDelete = { state.deleteApk(apk) },
                    onAutoRename = { state.autoRenameApk(apk) },
                    onManualRename = { state.manualRenameApk(apk) },
                    onMove = { state.moveApk(apk) },
                    onDetails = { state.showApkDetailPage(apk) },
                    onShare = { state.shareApkFiles(listOf(apk), context) },
                )
            }
        }
    }
}

/** Thin progress strip shown above the list while a scan is running. */
@Composable
private fun ScanProgressBar(state: HomeState) {
    val scheme = MaterialTheme.colorScheme
    val directory = state.scanDirectory
    val label = if (state.isDiscovering) "Searching storage…" else "Reading APK files…"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow)
            .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = scheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${state.scanFoundCount} found · $label",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.W600,
            )
            if (directory.isNotEmpty()) {
                Text(
                    directory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.outline,
                )
            }
        }
        TextButton(onClick = { state.stopScan() }) {
            Text("Stop")
        }
    }
}

@Composable
private fun AppDrawerContent(
    appVersion: String,
    apkCount: Int,
    isDark: Boolean,
    onThemeToggle: () -> Unit,
    onItemSelected: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalDrawerSheet(
        drawerContainerColor = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 24.dp, end = 28.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = "APK Organizer",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(
                            12.dp,
                            RoundedCornerShape(14.dp),
                            spotColor = Color.Black.withAlpha(60),
                            ambientColor = Color.Black.withAlpha(60),
                        )
                        .background(scheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "APK Organizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "v$appVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.outline,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 28.dp),
                color = scheme.outlineVariant.withAlpha(100),
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                label = { Text("APK Manager ($apkCount)") },
                selected = true,
                onClick = { onItemSelected("apk_manager") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                label = { Text("Smart Organize") },
                selected = false,
                onClick = { onItemSelected("smart_organize") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 28.dp),
                color = scheme.outlineVariant.withAlpha(100),
            )

            DrawerSectionLabel("Device Apps")
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Apps, contentDescription = null) },
                label = { Text("Installed Apps") },
                selected = false,
                onClick = { onItemSelected("installed") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.SettingsApplications, contentDescription = null) },
                label = { Text("System Apps") },
                selected = false,
                onClick = { onItemSelected("app_system") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 28.dp),
                color = scheme.outlineVariant.withAlpha(100),
            )

            DrawerSectionLabel("App Settings")
            NavigationDrawerItem(
                icon = {
                    Icon(
                        if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = null,
                    )
                },
                label = { Text(if (isDark) "Light Mode" else "Dark Mode") },
                selected = false,
                onClick = onThemeToggle,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                label = { Text("Rate Us") },
                selected = false,
                onClick = { onItemSelected("rate_us") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
                label = { Text("Privacy Policy") },
                selected = false,
                onClick = { onItemSelected("privacy_policy") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                label = { Text("About") },
                selected = false,
                onClick = { onItemSelected("about") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                label = { Text("Share") },
                selected = false,
                onClick = { onItemSelected("share") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 28.dp),
                color = scheme.outlineVariant.withAlpha(100),
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                label = { Text("Exit") },
                selected = false,
                onClick = { onItemSelected("exit") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrawerSectionLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, end = 28.dp, bottom = 8.dp),
    )
}

/** The APK details bottom sheet shown when tapping a tile. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ApkDetailsBottomSheet(
    apk: ApkFile,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onInstall: () -> Unit,
    onAutoRename: () -> Unit,
    onManualRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = AppRadius.sheetShape,
        containerColor = scheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .verticalScroll(rememberScrollState()),
        ) {
            com.apkorganizer.ui.widgets.SheetHandle(
                Modifier.align(Alignment.CenterHorizontally),
            )

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(
                            12.dp,
                            RoundedCornerShape(18.dp),
                            spotColor = scheme.primary.withAlpha(30),
                            ambientColor = scheme.primary.withAlpha(30),
                        )
                        .background(scheme.primaryContainer, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    com.apkorganizer.ui.widgets.FileImage(
                        path = apk.iconPath,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            Icons.Filled.Android,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        apk.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                scheme.surfaceContainerHighest,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            apk.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            HorizontalDivider(color = scheme.outlineVariant.withAlpha(100), thickness = 1.dp)

            // Actions
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    "Actions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W600,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BottomSheetAction(
                        icon = Icons.Outlined.Info,
                        label = "Details",
                        color = scheme.primary,
                        onTap = onDetails,
                    )
                    BottomSheetAction(
                        icon = Icons.Filled.InstallMobile,
                        label = "Install",
                        color = scheme.primary,
                        onTap = onInstall,
                    )
                    BottomSheetAction(
                        icon = Icons.Filled.AutoFixHigh,
                        label = "Auto Rename",
                        color = scheme.tertiary,
                        onTap = onAutoRename,
                    )
                    BottomSheetAction(
                        icon = Icons.Outlined.DriveFileRenameOutline,
                        label = "Rename",
                        color = scheme.secondary,
                        onTap = onManualRename,
                    )
                    BottomSheetAction(
                        icon = Icons.Outlined.DriveFileMove,
                        label = "Move",
                        color = DeepOrange,
                        onTap = onMove,
                    )
                    BottomSheetAction(
                        icon = Icons.Outlined.Share,
                        label = "Share",
                        color = scheme.secondary,
                        onTap = onShare,
                    )
                    BottomSheetAction(
                        icon = Icons.Outlined.Delete,
                        label = "Delete",
                        color = scheme.error,
                        onTap = onDelete,
                    )
                }
            }

            HorizontalDivider(color = scheme.outlineVariant.withAlpha(100), thickness = 1.dp)

            // File details
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                DetailRow(
                    icon = Icons.Outlined.Info,
                    label = "Version",
                    value = "${apk.versionName} (${apk.versionCode})",
                )
                Spacer(Modifier.height(12.dp))
                DetailRow(
                    icon = Icons.Outlined.Folder,
                    label = "Directory",
                    value = apk.directory,
                )
                Spacer(Modifier.height(12.dp))
                DetailRow(
                    icon = Icons.Outlined.Storage,
                    label = "File Size",
                    value = apk.formattedSize,
                )
                Spacer(Modifier.height(12.dp))
                DetailRow(
                    icon = Icons.Outlined.Description,
                    label = "File Name",
                    value = apk.fileName,
                )
                if (apk.lastModified > 0) {
                    Spacer(Modifier.height(12.dp))
                    DetailRow(
                        icon = Icons.Outlined.Schedule,
                        label = "Modified",
                        value = FormatUtil.formatAge(apk.lastModified),
                    )
                }
            }
        }
    }
}

/** The searchable directory filter bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryFilterSheet(
    directories: List<String>,
    currentFilter: String?,
    onSelect: (String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { onSelect(null) },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = false,
        ),
        shape = AppRadius.sheetShape,
        containerColor = scheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
        ) {
            com.apkorganizer.ui.widgets.SheetHandle(
                Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "Filter by directory",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
            )
            SearchField(
                value = query,
                onValueChange = { query = it },
                hint = "Search directories...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            val q = query.trim().lowercase()
            val filtered =
                if (q.isEmpty()) directories
                else directories.filter { it.lowercase().contains(q) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                item(key = "__all__") {
                    val isSel = currentFilter == null
                    FilterRow(
                        icon = Icons.Filled.AllInclusive,
                        title = "All Directories",
                        subtitle = null,
                        selected = isSel,
                        onClick = { onSelect("all") },
                    )
                }
                items(filtered, key = { it }) { dir ->
                    val isSel = currentFilter == dir
                    FilterRow(
                        icon = Icons.Outlined.Folder,
                        title = dir.substringAfterLast('/'),
                        subtitle = dir,
                        selected = isSel,
                        onClick = { onSelect(dir) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .androidx.compose.foundation.clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected) scheme.primary else scheme.onSurface,
                fontWeight = if (selected) FontWeight.W600 else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Blocking progress dialog shown during "Smart Organize". */
@Composable
private fun OrganizeProgressDialog(progress: OrganizeProgressState) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(AppRadius.dialog),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Auto Organize — ${progress.phase}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(6.dp),
                        ),
                ) {
                    LinearProgressIndicator(
                        progress = {
                            if (progress.total == 0) 0f
                            else progress.done.toFloat() / progress.total
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (progress.total == 0) {
                        "Working…"
                    } else {
                        "${progress.done} / ${progress.total} file(s)"
                    },
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { progress.token.cancel() },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
