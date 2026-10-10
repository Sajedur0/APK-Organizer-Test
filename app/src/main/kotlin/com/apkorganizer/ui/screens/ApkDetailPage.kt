package com.apkorganizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkorganizer.data.ApkDetailInfo
import com.apkorganizer.data.ApkManager
import com.apkorganizer.data.ApkManagerException
import com.apkorganizer.data.ApkPermission
import com.apkorganizer.ui.theme.AppGradients
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.widgets.FileImage
import com.apkorganizer.ui.widgets.HexagonDotsLoading
import kotlinx.coroutines.launch

/** Full detail page for one APK file — port of the Flutter `ApkDetailPage`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkDetailPage(
    filePath: String,
    appName: String,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<ApkDetailInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAllPermissions by remember { mutableStateOf(false) }

    fun loadDetail() {
        scope.launch {
            isLoading = true
            error = null
            try {
                detail = ApkManager.getApkDetail(filePath)
                isLoading = false
            } catch (e: ApkManagerException) {
                error = e.message
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadDetail() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.appName?.takeIf { it.isNotEmpty() } ?: appName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HexagonDotsLoading(minRadius = 10.dp)
                    }
                }
                error != null -> {
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
                        Text(error ?: "", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { loadDetail() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
                else -> detail?.let { info ->
                    ApkDetailContent(
                        detail = info,
                        showAllPermissions = showAllPermissions,
                        onTogglePermissions = { showAllPermissions = !showAllPermissions },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApkDetailContent(
    detail: ApkDetailInfo,
    showAllPermissions: Boolean,
    onTogglePermissions: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val displayPermissions =
        if (showAllPermissions) detail.permissions
        else detail.permissions.take(10)
    val hasMore = detail.permissions.size > 10

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item { ApkDetailHeader(detail) }
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("App Info") }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            InfoCard {
                InfoRow(Icons.Outlined.Info, "Version", "${detail.versionName} (${detail.versionCode})")
                InfoRow(Icons.Outlined.SdCard, "SDK", detail.sdkDisplay)
                InfoRow(Icons.Outlined.Folder, "Package", detail.packageName)
                InfoRow(Icons.Outlined.Storage, "Size", detail.formattedSize)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionTitle("ABI Support") }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            InfoCard {
                InfoRow(Icons.Outlined.Memory, "Supported ABIs", detail.abisDisplay)
            }
        }
        if (!detail.signatureHash.isNullOrEmpty()) {
            item { Spacer(Modifier.height(20.dp)) }
            item { SectionTitle("Signature") }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                InfoCard {
                    InfoRow(Icons.Outlined.Security, "SHA-256", detail.signatureDisplay)
                }
            }
        }
        if (detail.permissions.isNotEmpty()) {
            item { Spacer(Modifier.height(20.dp)) }
            item { SectionTitle("Permissions (${detail.permissions.size})") }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = scheme.surfaceContainerLow,
                    ),
                    shape = RoundedCornerShape(AppRadius.card),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.14f),
                    ),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        displayPermissions.forEach { permission ->
                            PermissionRow(permission)
                        }
                    }
                }
            }
            if (hasMore) {
                item {
                    TextButton(onClick = onTogglePermissions) {
                        Icon(
                            if (showAllPermissions) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = null,
                        )
                        Text(
                            if (showAllPermissions) {
                                "Show less"
                            } else {
                                "Show all ${detail.permissions.size}"
                            },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ApkDetailHeader(detail: ApkDetailInfo) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        shape = RoundedCornerShape(AppRadius.card),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.14f),
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Brush.linearGradient(AppGradients.hero), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                FileImage(path = detail.iconPath, modifier = Modifier.size(64.dp)) {
                    Icon(
                        Icons.Filled.Android,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    detail.appName.takeIf { it.isNotEmpty() } ?: detail.fileName,
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
                        detail.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.W600,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
internal fun InfoCard(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        shape = RoundedCornerShape(AppRadius.card),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.14f),
        ),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column { content() }
        }
    }
}

@Composable
internal fun InfoRow(icon: ImageVector, label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.W500,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PermissionRow(permission: ApkPermission) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (permission.granted) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Outlined.RemoveCircleOutline
            },
            contentDescription = null,
            tint = if (permission.granted) scheme.primary else scheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                permission.shortName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                permission.name,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
