package com.apkorganizer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkorganizer.data.ApkManager
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.theme.FrostedSurface
import com.apkorganizer.ui.theme.LocalGlassBlurState
import com.apkorganizer.ui.theme.glassBlurSource
import com.apkorganizer.ui.widgets.withAlpha

/** The in-app Privacy Policy page — a verbatim port of the Flutter page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyPage(
    appVersion: String,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Scaffold(
        topBar = {
            FrostedSurface(Modifier.fillMaxWidth()) {
            TopAppBar(
                title = { Text("Privacy Policy") },
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
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .glassBlurSource(LocalGlassBlurState.current)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                scheme.primaryContainer,
                                scheme.primaryContainer.withAlpha(180),
                            ),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                        RoundedCornerShape(AppRadius.dialog),
                    )
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(scheme.primary.withAlpha(40), CircleShape)
                        .padding(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                    )
                    Text(
                        "APK Organizer v$appVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.withAlpha(150),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            PolicySection(
                icon = Icons.Outlined.Info,
                title = "1. Introduction",
                content = "APK Organizer (\"we\", \"our\", or \"the app\") is a local utility " +
                    "application designed to help users scan, organize, and manage APK files " +
                    "stored on their Android devices. We are committed to protecting your " +
                    "privacy. This Privacy Policy explains how the app handles your data.",
            )
            Spacer(Modifier.height(16.dp))

            PolicySection(
                icon = Icons.Outlined.DataUsage,
                title = "2. Data Collection",
                content = "We do NOT collect any personal data.\n\n" +
                    "• No personal information (name, email, phone number, contacts) is collected.\n" +
                    "• No location data is accessed or transmitted.\n" +
                    "• No usage analytics or tracking are performed.\n" +
                    "• No crash reports or diagnostic data are sent to any server.\n" +
                    "• No advertising identifiers are collected or used.\n\n" +
                    "The app operates entirely offline. All data processing happens locally " +
                    "on your device.",
            )
            Spacer(Modifier.height(16.dp))

            PolicySection(
                icon = Icons.Outlined.Security,
                title = "3. Permissions & How They Are Used",
                content = "The app requests the following Android permissions, each used " +
                    "solely for its stated purpose:\n\n" +
                    "• Storage Access (READ/WRITE/MANAGE_EXTERNAL_STORAGE): Required to scan, " +
                    "read, rename, move, and delete APK files on your device.\n" +
                    "• REQUEST_INSTALL_PACKAGES: Allows you to install APK files through the " +
                    "system package installer.\n" +
                    "• REQUEST_DELETE_PACKAGES: Allows you to uninstall apps through the " +
                    "system uninstall dialog.\n" +
                    "• QUERY_ALL_PACKAGES: Retrieves metadata of installed apps for display " +
                    "and backup purposes.\n\n" +
                    "No other permissions are requested. The app does not access your camera, " +
                    "microphone, location, contacts, or any other sensitive data.",
            )
            Spacer(Modifier.height(16.dp))

            PolicySection(
                icon = Icons.Outlined.Storage,
                title = "4. Data Storage & Security",
                content = "• All data remains on your device. No data is uploaded, synced, or " +
                    "transmitted to any external server.\n" +
                    "• The app does not use any remote databases or cloud storage.\n" +
                    "• App preferences (theme, sort mode) are stored locally using Android " +
                    "SharedPreferences and are automatically removed when the app is " +
                    "uninstalled.\n" +
                    "• Cached app icons are stored in the app's private cache directory and " +
                    "can be cleared at any time through your device settings.\n" +
                    "• The app does not maintain any internet connection and does not declare " +
                    "the INTERNET permission.",
            )
            Spacer(Modifier.height(16.dp))

            PolicySection(
                icon = Icons.Outlined.LinkOff,
                title = "5. Third-Party Services",
                content = "The app uses a minimal set of system components, none of which " +
                    "collect user data:\n\n" +
                    "• System browser intent — Opens the Google Play Store page in your " +
                    "external browser. No data is transmitted by the app itself.\n" +
                    "• System share sheet — Invokes the system share sheet to share the Play " +
                    "Store link. No data is collected.\n" +
                    "• Android PackageManager — Reads app version information locally. No " +
                    "network activity.\n" +
                    "• Android SharedPreferences — Stores user preferences locally. No " +
                    "network activity.\n\n" +
                    "No analytics, advertising, crash reporting, or other tracking services " +
                    "are included in the app.",
            )
            Spacer(Modifier.height(16.dp))

            PolicySection(
                icon = Icons.Outlined.ChildCare,
                title = "6. Children's Privacy",
                content = "The app does not knowingly collect any data from children under " +
                    "the age of 13. As the app does not collect any personal data from any " +
                    "user, COPPA requirements are not applicable.",
            )
            Spacer(Modifier.height(16.dp))

            PolicySection(
                icon = Icons.Outlined.Update,
                title = "7. Changes to This Privacy Policy",
                content = "We may update this Privacy Policy from time to time. Any changes " +
                    "will be reflected in the app with an updated revision date. We encourage " +
                    "you to review this policy periodically.",
            )
            Spacer(Modifier.height(16.dp))

            PolicySection(
                icon = Icons.Outlined.VerifiedUser,
                title = "8. Your Rights & Control",
                content = "You have complete control over your data:\n\n" +
                    "• Uninstall the app at any time — this removes all locally stored data.\n" +
                    "• Revoke any permission through your device's Settings > Apps > APK Organizer.\n" +
                    "• Delete any APK files managed through the app at your discretion.\n" +
                    "• The app does not retain any data after uninstallation.",
            )
            Spacer(Modifier.height(16.dp))

            // Contact section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        scheme.surface.withAlpha(80),
                        RoundedCornerShape(16.dp),
                    )
                    .border(
                        BorderStroke(1.dp, scheme.outlineVariant.withAlpha(60)),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.MailOutline,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "9. Contact Us",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "If you have any questions or concerns about this Privacy Policy, " +
                        "please contact us:",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { ApkManager.openUrl(context, "mailto:Sajedurzero@gmail.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Sajedurzero@gmail.com",
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        ApkManager.openUrl(
                            context,
                            "https://play.google.com/store/apps/details?id=com.apkorganizer",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Store, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Dynamic System v$appVersion",
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            Text(
                "Last updated: August 2026",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant.withAlpha(150),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PolicySection(icon: ImageVector, title: String, content: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                scheme.surface.withAlpha(80),
                RoundedCornerShape(16.dp),
            )
            .border(
                BorderStroke(1.dp, scheme.outlineVariant.withAlpha(60)),
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            content,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f,
            color = scheme.onSurface,
        )
    }
}
