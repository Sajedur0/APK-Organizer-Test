package com.apkorganizer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.apkorganizer.data.ApkManager
import com.apkorganizer.data.ApkManagerException
import com.apkorganizer.data.InstalledApp
import com.apkorganizer.ui.AppScreen
import com.apkorganizer.ui.ScreenIds
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.widgets.CompactActionChip
import com.apkorganizer.ui.widgets.ConfirmDialog
import com.apkorganizer.ui.widgets.ConfirmRequest
import com.apkorganizer.ui.widgets.DirectoryBrowserSheet
import com.apkorganizer.ui.widgets.DirectoryPickerRequest
import com.apkorganizer.ui.widgets.FileImage
import com.apkorganizer.ui.widgets.HexagonDotsLoading
import com.apkorganizer.ui.widgets.SearchField
import com.apkorganizer.ui.widgets.SnackbarController
import com.apkorganizer.ui.widgets.withAlpha
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** State holder for the installed-apps list — port of `_InstalledAppsPageState`. */
class InstalledAppsState(
    val scope: CoroutineScope,
    val snackbar: SnackbarController,
    private val includeSystem: Boolean,
) {
    var apps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var filteredApps by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var selectedPackages by mutableStateOf<Set<String>>(emptySet())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var backupProgress by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** package name -> app, so lookups never scan the whole list. */
    private val appIndex = HashMap<String, InstalledApp>()
    private val pendingUninstallPackages = HashSet<String>()

    var isSearching by mutableStateOf(false)
    var searchInput by mutableStateOf("")
    var searchQuery by mutableStateOf("")
        private set
    private var searchDebounce: Job? = null

    var directoryPicker by mutableStateOf<DirectoryPickerRequest?>(null)
    var confirmRequest by mutableStateOf<ConfirmRequest?>(null)

    fun onSearchChanged(value: String) {
        searchInput = value
        searchDebounce?.cancel()
        searchDebounce = scope.launch {
            delay(220)
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

    fun applyFilter() {
        if (selectedPackages.isNotEmpty()) {
            selectedPackages = selectedPackages.filterTo(LinkedHashSet()) { appIndex.containsKey(it) }
        }
        val query = searchQuery.trim().lowercase()
        filteredApps = if (query.isEmpty()) {
            apps
        } else {
            apps.filter { it.searchLower.contains(query) }
        }
    }

    fun loadApps() {
        scope.launch {
            isLoading = true
            errorMessage = null
            selectedPackages = emptySet()
            try {
                val loaded = ApkManager.getInstalledApps(includeSystem)
                apps = loaded
                appIndex.clear()
                loaded.forEach { appIndex[it.packageName] = it }
                applyFilter()
                isLoading = false
            } catch (e: ApkManagerException) {
                errorMessage = e.message
                isLoading = false
            }
        }
    }

    fun onAppResumed() {
        scope.launch {
            if (pendingUninstallPackages.isNotEmpty()) {
                pendingUninstallPackages.clear()
                loadApps()
                return@launch
            }

            if (selectedPackages.isNotEmpty()) {
                var hasChanges = false
                val stillSelected = LinkedHashSet<String>()
                for (pkg in selectedPackages) {
                    if (appIndex.containsKey(pkg)) {
                        stillSelected.add(pkg)
                    } else {
                        hasChanges = true
                    }
                }
                if (hasChanges) {
                    selectedPackages = stillSelected
                    snackbar.show("Some apps were uninstalled")
                    loadApps()
                }
            }
        }
    }

    fun onPackageRemoved(packageName: String) {
        val removed = appIndex.remove(packageName) ?: return
        apps = apps.filter { it != removed }
        selectedPackages = selectedPackages - packageName
        pendingUninstallPackages.remove(packageName)
        applyFilter()
    }

    fun toggleSelection(app: InstalledApp) {
        selectedPackages =
            if (selectedPackages.contains(app.packageName)) {
                selectedPackages - app.packageName
            } else {
                selectedPackages + app.packageName
            }
    }

    val allSelected: Boolean
        get() = selectedPackages.isNotEmpty() && selectedPackages.size == filteredApps.size

    fun selectAll() {
        selectedPackages =
            if (allSelected) emptySet()
            else filteredApps.mapTo(LinkedHashSet()) { it.packageName }
    }

    fun clearSelection() {
        selectedPackages = emptySet()
    }

    fun appByPackage(pkg: String): InstalledApp? = appIndex[pkg]

    private fun pickBackupDirectory(onPicked: (String?) -> Unit) {
        scope.launch {
            try {
                val roots = ApkManager.getDirectories()
                directoryPicker = DirectoryPickerRequest(roots) { selected ->
                    directoryPicker = null
                    onPicked(selected)
                }
            } catch (e: ApkManagerException) {
                snackbar.show(e.message ?: "", isError = true)
            }
        }
    }

    fun backupApp(app: InstalledApp) {
        pickBackupDirectory { dir ->
            if (dir == null) return@pickBackupDirectory
            scope.launch {
                try {
                    val result = ApkManager.backupInstalledApp(app.packageName, dir)
                    snackbar.show(
                        "Backup saved: ${(result["fileName"] as? String) ?: app.displayName}",
                    )
                } catch (e: ApkManagerException) {
                    snackbar.show(e.message ?: "", isError = true)
                }
            }
        }
    }

    fun backupSelectedApps() {
        if (selectedPackages.isEmpty()) return
        pickBackupDirectory { dir ->
            if (dir == null) return@pickBackupDirectory
            scope.launch {
                val packages = selectedPackages.toList()
                val failures = BooleanArray(packages.size)
                var done = 0
                isLoading = true
                backupProgress = "Backing up 0/${packages.size}…"
                com.apkorganizer.utils.runParallel(packages.size, 2) { index ->
                    val app = appByPackage(packages[index])
                    if (app != null) {
                        try {
                            ApkManager.backupInstalledApp(app.packageName, dir)
                        } catch (_: Exception) {
                            failures[index] = true
                        }
                    } else {
                        failures[index] = true
                    }
                    done++
                    backupProgress = "Backing up $done/${packages.size}…"
                }
                val ok = failures.count { !it }
                val fail = failures.size - ok
                isLoading = false
                backupProgress = ""
                selectedPackages = emptySet()
                snackbar.show(
                    "$ok app(s) backed up${if (fail > 0) ", $fail failed" else ""}",
                    isError = fail > 0 && ok == 0,
                )
            }
        }
    }

    fun onPackageRemovedEvent(packageName: String) {
        onPackageRemoved(packageName)
    }

    fun uninstallApp(app: InstalledApp) {
        scope.launch {
            try {
                pendingUninstallPackages.add(app.packageName)
                ApkManager.uninstallPackage(app.packageName)
                snackbar.show(
                    "Uninstall requested for ${app.displayName}. " +
                        "Please confirm in system dialog.",
                )
            } catch (e: ApkManagerException) {
                pendingUninstallPackages.remove(app.packageName)
                snackbar.show(e.message ?: "", isError = true)
            }
        }
    }

    fun uninstallSelectedApps() {
        if (selectedPackages.isEmpty()) return
        confirmRequest = ConfirmRequest(
            title = "Uninstall Selected",
            message = "Open uninstall confirmation for ${selectedPackages.size} selected app(s)?",
        ) { confirmed ->
            confirmRequest = null
            if (!confirmed) return@ConfirmRequest
            scope.launch {
                var requested = 0
                var failed = 0
                for (pkg in selectedPackages.toList()) {
                    val app = appByPackage(pkg) ?: continue
                    try {
                        pendingUninstallPackages.add(app.packageName)
                        ApkManager.uninstallPackage(app.packageName)
                        requested++
                    } catch (_: Exception) {
                        pendingUninstallPackages.remove(app.packageName)
                        failed++
                    }
                }
                selectedPackages = emptySet()
                snackbar.show(
                    "Uninstall requested for $requested app(s)" +
                        "${if (failed > 0) ", $failed failed" else ""}. " +
                        "Please confirm in the system dialogs.",
                    isError = failed > 0 && requested == 0,
                )
            }
        }
    }
}

/** Installed / system apps list page. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InstalledAppsPage(
    includeSystem: Boolean,
    title: String,
    onBack: () -> Unit,
    onPush: (AppScreen) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarController(rememberCoroutineScope()) }
    val scope = rememberCoroutineScope()
    val state = remember { InstalledAppsState(scope, snackbar, includeSystem) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        state.loadApps()
        ApkManager.packageRemovedEvents.collect { pkg ->
            state.onPackageRemovedEvent(pkg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSearching) {
                        SearchField(
                            value = state.searchInput,
                            onValueChange = { state.onSearchChanged(it) },
                            hint = "Search apps...",
                            modifier = Modifier.fillMaxWidth(),
                            autofocus = true,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (state.isLoading) {
                                Spacer(Modifier.width(10.dp))
                                HexagonDotsLoading(minRadius = 4.dp)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSearching) {
                        IconButton(
                            onClick = {
                                state.clearSearch()
                                state.isSearching = false
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    } else {
                        IconButton(
                            onClick = { if (state.filteredApps.isNotEmpty()) state.selectAll() },
                        ) {
                            Icon(
                                if (state.allSelected) {
                                    Icons.Filled.CheckBox
                                } else {
                                    Icons.Filled.CheckBoxOutlineBlank
                                },
                                contentDescription = "Select all",
                                tint = if (state.allSelected) scheme.primary else scheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { state.isSearching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(
                            onClick = { if (!state.isLoading) state.loadApps() },
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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
            if (state.selectedPackages.isNotEmpty()) {
                InstalledAppsBottomBar(
                    selectedCount = state.selectedPackages.size,
                    totalCount = state.filteredApps.size,
                    onSelectAll = { state.selectAll() },
                    onClearSelection = { state.clearSelection() },
                    onBackup = { state.backupSelectedApps() },
                    onUninstall = { state.uninstallSelectedApps() },
                )
            }
        },
        snackbarHost = {
            com.apkorganizer.ui.widgets.ApkSnackbarHost(snackbar)
        },
        containerColor = scheme.surface,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.apps.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        HexagonDotsLoading(minRadius = 10.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (includeSystem) "Loading system apps..." else "Loading installed apps...",
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                state.errorMessage != null && state.apps.isEmpty() -> {
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
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            state.errorMessage ?: "",
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { state.loadApps() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
                state.filteredApps.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.searchQuery.isEmpty()) {
                                "No apps found"
                            } else {
                                "No apps match your search"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        // Info banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .background(
                                    scheme.surfaceContainerHighest.withAlpha(128),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (includeSystem) {
                                    Icons.Filled.SettingsApplications
                                } else {
                                    Icons.Filled.Apps
                                },
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.backupProgress.isNotEmpty()) {
                                    state.backupProgress
                                } else {
                                    "${state.filteredApps.size} app(s)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.weight(1f))
                            if (state.isLoading) {
                                HexagonDotsLoading(minRadius = 4.dp)
                            }
                        }

                        PullToRefreshBox(
                            isRefreshing = state.isLoading,
                            onRefresh = { state.loadApps() },
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    top = 0.dp,
                                    bottom = 88.dp,
                                ),
                            ) {
                                items(
                                    state.filteredApps,
                                    key = { it.packageName },
                                ) { app ->
                                    InstalledAppTile(
                                        app = app,
                                        isSelected = state.selectedPackages.contains(app.packageName),
                                        onTap = {
                                            if (state.selectedPackages.isNotEmpty()) {
                                                state.toggleSelection(app)
                                            } else {
                                                state.openAppDetails(app, onPush)
                                            }
                                        },
                                        onLongPress = { state.toggleSelection(app) },
                                        onBackup = { state.backupApp(app) },
                                        onUninstall = { state.uninstallApp(app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Directory picker for backups
    state.directoryPicker?.let { picker ->
        DirectoryBrowserSheet(
            initialDirectories = picker.initialDirectories,
            onSelect = picker.onSelect,
            onMessage = { message, isError -> snackbar.show(message, isError = isError) },
        )
    }

    // Confirmation dialog
    state.confirmRequest?.let { request ->
        ConfirmDialog(
            title = request.title,
            message = request.message,
            confirmLabel = request.confirmLabel,
            danger = request.danger,
            onResult = request.onResult,
        )
    }
}

private fun InstalledAppsState.openAppDetails(
    app: InstalledApp,
    onPush: (AppScreen) -> Unit,
) {
    onPush(
        AppScreen.InstalledAppDetail(
            id = ScreenIds.next(),
            app = app,
            onBackup = { backupApp(app) },
            onUninstall = { uninstallApp(app) },
        ),
    )
}

/** One installed-app card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstalledAppTile(
    app: InstalledApp,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onBackup: () -> Unit,
    onUninstall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = AppRadius.cardShape
    val containerColor =
        if (isSelected) scheme.primaryContainer.withAlpha(110) else scheme.surfaceContainerLow
    val borderColor =
        if (isSelected) scheme.primary.withAlpha(140) else scheme.outlineVariant.withAlpha(120)

    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(50.dp)) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(scheme.primaryContainer, AppRadius.cardShape),
                    contentAlignment = Alignment.Center,
                ) {
                    FileImage(path = app.iconPath, modifier = Modifier.size(50.dp)) {
                        Icon(
                            Icons.Filled.Apps,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp)
                            .size(22.dp)
                            .background(scheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "v${app.versionName} - ${app.formattedSize}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(AppRadius.control),
                    containerColor = scheme.surfaceContainerHigh,
                ) {
                    DropdownMenuItem(
                        text = {
                            MenuRow(Icons.Outlined.Archive, "Backup APK", scheme.onSurface)
                        },
                        onClick = {
                            menuOpen = false
                            onBackup()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            MenuRow(
                                Icons.Outlined.Delete,
                                if (app.isSystemApp) "Uninstall Updates" else "Uninstall",
                                scheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onUninstall()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = tint)
    }
}

/** Selection bottom bar for the installed-apps page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledAppsBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBackup: () -> Unit,
    onUninstall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, spotColor = Color.Black.withAlpha(26))
            .background(scheme.surfaceContainerHighest)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSelectAll) {
                Icon(
                    if (selectedCount == totalCount) {
                        Icons.Filled.CheckBox
                    } else {
                        Icons.Filled.CheckBoxOutlineBlank
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text("Select All")
            }
            Spacer(Modifier.weight(1f))
            Text("$selectedCount selected")
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactActionChip(
                icon = Icons.Outlined.Archive,
                label = "Backup",
                color = scheme.primary,
                onTap = onBackup,
            )
            CompactActionChip(
                icon = Icons.Outlined.Delete,
                label = "Uninstall",
                color = scheme.error,
                onTap = onUninstall,
            )
        }
    }
}

/** Alias so the error icon import stays readable. */
private val Icons.Outlined.ErrorOutlineCompat
    get() = androidx.compose.material.icons.outlined.ErrorOutline
