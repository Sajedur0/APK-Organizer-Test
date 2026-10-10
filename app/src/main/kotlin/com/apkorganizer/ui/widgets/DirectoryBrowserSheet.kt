package com.apkorganizer.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkorganizer.data.ApkManager
import com.apkorganizer.data.ApkManagerException
import com.apkorganizer.data.DirectoryEntry
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.theme.DialogBlurBehind
import com.apkorganizer.ui.theme.LocalAppDividerColor
import com.apkorganizer.ui.theme.glassDialogContainer
import kotlinx.coroutines.launch

/**
 * Bottom sheet for choosing a destination folder: navigates the directory
 * tree, supports going back and creating new folders. A port of the Flutter
 * `DirectoryBrowserSheet`.
 *
 * When [recentDirectoryPath] is provided (the folder used by the previous
 * move/backup), it is offered as a one-tap shortcut at the top of the root
 * list — repeat operations no longer need to navigate the tree again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryBrowserSheet(
    initialDirectories: List<DirectoryEntry>,
    onSelect: (String?) -> Unit,
    onMessage: (String, Boolean) -> Unit,
    recentDirectoryPath: String? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var subdirectories by remember { mutableStateOf(initialDirectories) }
    var currentDirectoryPath by remember { mutableStateOf<String?>(null) }
    var currentDirectoryName by remember { mutableStateOf("Internal Storage") }
    var isLoading by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val navigationHistory = remember { mutableStateListOf<HistoryEntry>() }
    val isRoot = navigationHistory.isEmpty()

    fun navigateBack() {
        if (navigationHistory.isEmpty()) return
        val previousState = navigationHistory.removeAt(navigationHistory.lastIndex)
        subdirectories = previousState.directories
        currentDirectoryPath = previousState.path
        currentDirectoryName = previousState.name
    }

    fun navigateInto(directory: DirectoryEntry) {
        isLoading = true
        navigationHistory.add(
            HistoryEntry(
                directories = subdirectories.toList(),
                path = currentDirectoryPath,
                name = currentDirectoryName,
            ),
        )
        scope.launch {
            try {
                val subdirs = ApkManager.getSubdirectories(directory.path)
                subdirectories = subdirs
                currentDirectoryPath = directory.path
                currentDirectoryName = directory.name
                isLoading = false
            } catch (e: ApkManagerException) {
                isLoading = false
                onMessage("Failed to open directory: ${e.message}", true)
            } catch (e: Exception) {
                isLoading = false
                onMessage("Failed to open directory: $e", true)
            }
        }
    }

    fun createFolder(folderName: String) {
        val parentPath = currentDirectoryPath
        if (parentPath.isNullOrEmpty()) return
        if (folderName.trim().isEmpty()) {
            onMessage("Folder name cannot be empty", false)
            return
        }
        val trimmedFolderName = folderName.trim()
        isLoading = true
        scope.launch {
            try {
                val createdDirectory = ApkManager.createDirectory(parentPath, trimmedFolderName)
                val updatedSubdirectories = ApkManager.getSubdirectories(parentPath)
                subdirectories = updatedSubdirectories
                isLoading = false
                onMessage("Folder created: ${createdDirectory.name}", false)
            } catch (e: ApkManagerException) {
                isLoading = false
                onMessage("Failed to create folder: ${e.message}", true)
            } catch (e: Exception) {
                isLoading = false
                onMessage("Failed to create folder: $e", true)
            }
        }
    }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = { onSelect(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = AppRadius.sheetShape,
        containerColor = glassDialogContainer(),
        dragHandle = null,
    ) {
        DialogBlurBehind()
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f)) {
            SheetHandle(Modifier.align(Alignment.CenterHorizontally))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isRoot) {
                    IconButton(onClick = { navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    if (isRoot) "Select destination folder" else currentDirectoryName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!isRoot) {
                    IconButton(
                        onClick = { showCreateDialog = true },
                        enabled = !isLoading,
                    ) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = "Create new folder",
                        )
                    }
                }
            }
            if (!isRoot) {
                Text(
                    currentDirectoryPath ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            HorizontalDivider(color = LocalAppDividerColor.current, thickness = 1.dp)

            Box(Modifier.weight(1f)) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HexagonDotsLoading()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            vertical = 8.dp,
                        ),
                    ) {
                        if (!isRoot) {
                            item(key = "use-this-folder") {
                                val path = currentDirectoryPath ?: ""
                                DirectoryRow(
                                    icon = Icons.Filled.CheckCircleOutline,
                                    iconTint = scheme.primary,
                                    title = "Use this folder",
                                    titleColor = scheme.primary,
                                    titleWeight = FontWeight.W600,
                                    subtitle = path,
                                    onClick = { onSelect(path) },
                                )
                            }
                        } else if (!recentDirectoryPath.isNullOrEmpty()) {
                            item(key = "recent-folder") {
                                DirectoryRow(
                                    icon = Icons.Filled.Schedule,
                                    iconTint = scheme.tertiary,
                                    title = "Recently used folder",
                                    titleColor = scheme.onSurface,
                                    titleWeight = FontWeight.W500,
                                    subtitle = recentDirectoryPath,
                                    trailing = {
                                        Icon(
                                            Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            tint = scheme.onSurfaceVariant,
                                        )
                                    },
                                    onClick = { onSelect(recentDirectoryPath) },
                                )
                            }
                        }
                        items(subdirectories, key = { it.path }) { dir ->
                            DirectoryRow(
                                icon = Icons.Filled.Folder,
                                iconTint = scheme.primary,
                                title = dir.name,
                                titleColor = scheme.onSurface,
                                titleWeight = FontWeight.W500,
                                subtitle = dir.path,
                                trailing = {
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = scheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { navigateInto(dir) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        TextInputDialog(
            title = "Create New Folder",
            label = "Folder name",
            hint = "Enter folder name",
            initialText = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                createFolder(name)
            },
        )
    }
}

private class HistoryEntry(
    val directories: List<DirectoryEntry>,
    val path: String?,
    val name: String,
)

/** A folder list row styled after Flutter's `ListTile`. */
@Composable
private fun DirectoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    titleColor: androidx.compose.ui.graphics.Color,
    titleWeight: FontWeight,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(scheme.primaryContainer, RoundedCornerShape(10.dp))
                .padding(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = titleColor,
                fontWeight = titleWeight,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing()
    }
}
