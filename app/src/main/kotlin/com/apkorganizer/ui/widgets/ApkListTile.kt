package com.apkorganizer.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkorganizer.data.ApkFile
import com.apkorganizer.ui.theme.AppRadius

/** One APK card in the home list — v2 redesign with softer, roomier cards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ApkListTile(
    apk: ApkFile,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    onAutoRename: () -> Unit,
    onManualRename: () -> Unit,
    onMove: () -> Unit,
    onDetails: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    /**
     * Marks this file as a redundant copy (another file of the same app +
     * version will be kept). Comes from the Smart Insights analysis, so it
     * always matches what "Clean duplicates" would remove.
     */
    isDuplicate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = AppRadius.cardShape
    val containerColor =
        if (isSelected) scheme.primaryContainer.withAlpha(100) else scheme.surfaceContainerLow
    val borderColor =
        if (isSelected) scheme.primary.withAlpha(160) else scheme.outlineVariant.withAlpha(90)
    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 56dp icon container with the selection badge on top.
            Box(modifier = Modifier.size(56.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            scheme.primaryContainer.withAlpha(140),
                            RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    FileImage(
                        path = apk.iconPath,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.Android,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(22.dp)
                            .background(scheme.primary, CircleShape)
                            .border(BorderStroke(2.dp, scheme.surface), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    apk.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                scheme.primaryContainer,
                                RoundedCornerShape(AppRadius.chip),
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "v${apk.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onPrimaryContainer,
                            fontWeight = FontWeight.W600,
                        )
                    }
                    if (isDuplicate) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    scheme.tertiaryContainer,
                                    RoundedCornerShape(AppRadius.chip),
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    tint = scheme.onTertiaryContainer,
                                    modifier = Modifier.size(10.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "Duplicate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onTertiaryContainer,
                                    fontWeight = FontWeight.W600,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        apk.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    apk.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.outline,
                    fontSize = 11.sp,
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
                    if (onDetails != null) {
                        MenuEntry(Icons.Outlined.Info, "Details", scheme.onSurface) {
                            menuOpen = false
                            onDetails()
                        }
                    }
                    MenuEntry(Icons.Filled.InstallMobile, "Install", scheme.onSurface) {
                        menuOpen = false
                        onInstall()
                    }
                    MenuEntry(Icons.Filled.AutoFixHigh, "Auto Rename", scheme.onSurface) {
                        menuOpen = false
                        onAutoRename()
                    }
                    MenuEntry(Icons.Outlined.DriveFileRenameOutline, "Rename", scheme.onSurface) {
                        menuOpen = false
                        onManualRename()
                    }
                    MenuEntry(Icons.Filled.DriveFileMove, "Move", scheme.onSurface) {
                        menuOpen = false
                        onMove()
                    }
                    if (onShare != null) {
                        MenuEntry(Icons.Filled.Share, "Share", scheme.onSurface) {
                            menuOpen = false
                            onShare()
                        }
                    }
                    MenuEntry(Icons.Outlined.Delete, "Delete", scheme.error) {
                        menuOpen = false
                        onDelete()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, color = tint)
            }
        },
        onClick = onClick,
    )
}
