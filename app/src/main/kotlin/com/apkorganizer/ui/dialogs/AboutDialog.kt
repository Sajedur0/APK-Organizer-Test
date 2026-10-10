package com.apkorganizer.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.apkorganizer.R
import com.apkorganizer.data.ApkManager
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.theme.DialogBlurBehind
import com.apkorganizer.ui.theme.glassDialogContainer
import com.apkorganizer.ui.widgets.withAlpha

/** The "About" dialog with gradient header, features row and credits. */
@Composable
fun AboutDialog(
    appVersion: String,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var javaVersion by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val version = ApkManager.getJavaVersion()
        javaVersion = version
    }

    Dialog(onDismissRequest = onDismiss) {
        DialogBlurBehind()
        Surface(
            shape = RoundedCornerShape(AppRadius.dialog),
            color = glassDialogContainer(),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
        ) {
            Column {
                // Header with gradient
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    scheme.primary,
                                    scheme.primary.withAlpha(180),
                                ),
                                start = Offset.Zero,
                                end = Offset.Infinite,
                            ),
                            RoundedCornerShape(
                                topStart = AppRadius.dialog,
                                topEnd = AppRadius.dialog,
                            ),
                        )
                        .padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                scheme.surfaceContainerHighest.withAlpha(60),
                                RoundedCornerShape(AppRadius.control),
                            ),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.app_icon),
                            contentDescription = "APK Organizer",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "APK Organizer",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White.withAlpha(40), RoundedCornerShape(AppRadius.control))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "v$appVersion",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W500,
                        )
                    }
                }

                // Body content
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Scan, organize, and manage all your APK files with ease. " +
                            "A lightweight and powerful utility for Android.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f,
                    )
                    if (javaVersion.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Java: $javaVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.outline,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        AboutFeature(Icons.Rounded.Search, "Scan")
                        AboutFeature(Icons.Rounded.InstallMobile, "Install")
                        AboutFeature(Icons.Rounded.Folder, "Manage")
                    }
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = scheme.outlineVariant.withAlpha(100))
                    Spacer(Modifier.height(12.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Made with care",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.outline,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sajedur0",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.primary,
                            fontWeight = FontWeight.W600,
                            modifier = Modifier
                                .clickable {
                                    ApkManager.openUrl(
                                        context,
                                        "https://play.google.com/store/apps/details?id=com.apkorganizer",
                                    )
                                }
                                .padding(2.dp),
                        )
                    }
                }

                // Close button
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Close", modifier = Modifier.padding(vertical = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun AboutFeature(icon: ImageVector, label: String) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(scheme.primaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = scheme.primary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.W600,
        )
    }
}
