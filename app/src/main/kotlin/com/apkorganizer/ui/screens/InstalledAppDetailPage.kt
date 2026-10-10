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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.apkorganizer.data.InstalledApp
import com.apkorganizer.ui.theme.AppGradients
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.widgets.CompactActionChip
import com.apkorganizer.ui.widgets.FileImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Detail page for one installed application. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppDetailPage(
    app: InstalledApp,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onUninstall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    // The stat call is async: the page paints immediately and fills the date
    // in as soon as the filesystem answers.
    var installedDate by remember { mutableStateOf("…") }
    LaunchedEffect(app.sourceDir) {
        val label = withContext(Dispatchers.IO) {
            try {
                val file = File(app.sourceDir)
                if (file.exists() && file.lastModified() > 0L) {
                    com.apkorganizer.utils.FormatUtil.formatDate(file.lastModified())
                } else {
                    "Unknown"
                }
            } catch (_: Exception) {
                "Unknown"
            }
        }
        installedDate = label
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        app.displayName,
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Header
            InfoCard {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                Brush.linearGradient(AppGradients.hero),
                                RoundedCornerShape(18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        FileImage(path = app.iconPath, modifier = Modifier.size(64.dp)) {
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
                            app.displayName,
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
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("App Info")
            Spacer(Modifier.height(8.dp))
            InfoCard {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Column {
                        AppInfoRow(Icons.Outlined.Info, "Version", app.versionName)
                        AppInfoRow(Icons.Outlined.Code, "Version Code", app.versionCode.toString())
                        AppInfoRow(
                            Icons.Outlined.Category,
                            "Type",
                            if (app.isSystemApp) "System App" else "User App",
                        )
                        AppInfoRow(Icons.Outlined.Folder, "Package", app.packageName)
                        AppInfoRow(Icons.Outlined.Storage, "Size", app.formattedSize)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Installation")
            Spacer(Modifier.height(8.dp))
            InfoCard {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Column {
                        AppInfoRow(Icons.Outlined.Description, "Source", app.sourceDir)
                        AppInfoRow(Icons.Outlined.EventAvailable, "Updated", installedDate)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row {
                Box(Modifier.weight(1f)) {
                    CompactActionChip(
                        icon = Icons.Outlined.Archive,
                        label = "Backup APK",
                        color = scheme.primary,
                        onTap = onBackup,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    CompactActionChip(
                        icon = Icons.Filled.Delete,
                        label = if (app.isSystemApp) "Uninstall Updates" else "Uninstall",
                        color = scheme.error,
                        onTap = onUninstall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AppInfoRow(icon: ImageVector, label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
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
