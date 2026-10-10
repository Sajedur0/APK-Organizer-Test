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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkorganizer.data.StorageInsights
import com.apkorganizer.ui.theme.AppGradients
import com.apkorganizer.ui.theme.AppRadius

/**
 * The "Smart Insights" hero card — the centerpiece of the redesigned home
 * screen. One glance tells the user how many APK files they have, how much
 * space they use and how much could be reclaimed by removing duplicates,
 * with a one-tap cleanup action.
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
            .background(Brush.linearGradient(AppGradients.hero), AppRadius.cardShape)
            .border(
                BorderStroke(1.dp, Color.White.withAlpha(40)),
                AppRadius.cardShape,
            ),
    ) {
        // Header row
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White.withAlpha(40), RoundedCornerShape(10.dp))
                    .padding(7.dp),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Smart Insights",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        // Stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp),
        ) {
            HeroStat(
                value = "${insights.totalFiles}",
                label = "APK Files",
                modifier = Modifier.weight(1f),
            )
            HeroDivider()
            HeroStat(
                value = insights.formattedTotalSize,
                label = "Total Size",
                modifier = Modifier.weight(1.2f),
            )
            HeroDivider()
            HeroStat(
                value = "${insights.distinctApps}",
                label = "Apps",
                modifier = Modifier.weight(0.8f),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.withAlpha(40)),
        )

        if (insights.hasDuplicates) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCleanDuplicates)
                    .padding(start = 20.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.White.withAlpha(40), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${insights.duplicateFiles} duplicate file(s) found",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Free ${insights.formattedReclaimable} by removing them",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.withAlpha(180),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onCleanDuplicates,
                    enabled = !isScanning,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(AppRadius.control),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = scheme.primary,
                        disabledContainerColor = Color.White.withAlpha(140),
                        disabledContentColor = scheme.primary.withAlpha(150),
                    ),
                ) {
                    Text("Clean", fontWeight = FontWeight.W700)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
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
                    color = Color.White.withAlpha(200),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.withAlpha(180),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(34.dp)
            .background(Color.White.withAlpha(50)),
    )
}
