package com.apkorganizer.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.theme.DeepOrange
import com.apkorganizer.ui.theme.FrostedSurface

/** Floating selection action bar shown when APK files are multi-selected. */
@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    onAutoRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)

    FrostedSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            )
            .shadow(16.dp, shape, spotColor = Color.Black.copy(alpha = 40f / 255f)),
        shape = shape,
        tint = scheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, Color.White.withAlpha(30)),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .background(scheme.primaryContainer, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "$selectedCount selected",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.W600,
                    color = scheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onClearSelection,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = scheme.surfaceContainerHighest,
                ),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
        }

        // Horizontally scrollable so all five actions stay reachable even on
        // narrow screens.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactActionChip(
                icon = Icons.Filled.InstallMobile,
                label = "Install",
                color = scheme.primary,
                onTap = onInstall,
            )
            CompactActionChip(
                icon = Icons.Filled.AutoFixHigh,
                label = "Auto Rename",
                color = scheme.tertiary,
                onTap = onAutoRename,
            )
            CompactActionChip(
                icon = Icons.Filled.DriveFileMove,
                label = "Move",
                color = DeepOrange,
                onTap = onMove,
            )
            CompactActionChip(
                icon = Icons.Filled.Share,
                label = "Share",
                color = scheme.secondary,
                onTap = onShare,
            )
            CompactActionChip(
                icon = Icons.Filled.Delete,
                label = "Delete",
                color = scheme.error,
                onTap = onDelete,
            )
        }
    }
    }
}
