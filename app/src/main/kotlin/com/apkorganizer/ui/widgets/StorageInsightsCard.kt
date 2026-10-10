package com.apkorganizer.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkorganizer.data.StorageInsights
import com.apkorganizer.ui.theme.AppRadius

/**
 * The "Smart Insights" card shown above the APK list: one glance tells the
 * user how many files they have, how much space they use and how much could
 * be reclaimed by removing duplicates — with a one-tap cleanup action.
 */
@Composable
fun StorageInsightsCard(
    insights: StorageInsights,
    isScanning: Boolean,
    onCleanDuplicates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppRadius.cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        scheme.surfaceContainerLow,
                        scheme.primaryContainer.withAlpha(40),
                    ),
                ),
                AppRadius.cardShape,
            )
            .border(
                BorderStroke(1.dp, scheme.outlineVariant.withAlpha(120)),
                AppRadius.cardShape,
            ),
    ) {
        // Header label
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(scheme.primary.withAlpha(30), RoundedCornerShape(8.dp))
                    .padding(6.dp),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Smart Insights",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W700,
            )
        }

        // Stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
        ) {
            InsightStat(
                value = "${insights.totalFiles}",
                label = "APK Files",
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            InsightStat(
                value = insights.formattedTotalSize,
                label = "Total Size",
                modifier = Modifier.weight(1.2f),
            )
            StatDivider()
            InsightStat(
                value = "${insights.distinctApps}",
                label = "Apps",
                modifier = Modifier.weight(0.8f),
            )
        }

        HorizontalDivider(color = scheme.outlineVariant.withAlpha(100), thickness = 1.dp)

        if (insights.hasDuplicates) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCleanDuplicates)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(scheme.tertiaryContainer, RoundedCornerShape(10.dp))
                        .padding(7.dp),
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = scheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${insights.duplicateFiles} duplicate file(s) found",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.W600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Free ${insights.formattedReclaimable} by removing them",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onCleanDuplicates,
                    enabled = !isScanning,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(AppRadius.control),
                ) {
                    Text("Clean")
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (insights.totalFiles == 0) {
                        "Scan your storage to see insights here"
                    } else {
                        "No duplicates — your APK collection looks tidy"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InsightStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.withAlpha(100)),
    )
}
