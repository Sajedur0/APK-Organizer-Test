package com.apkorganizer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.widgets.withAlpha

/** Result summary dialog used after "Smart Organize". */
@Composable
fun SummaryDialog(
    title: String,
    stats: List<Pair<String, Int>>,
    details: List<String>,
    errors: List<String>,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AppRadius.dialog),
            color = scheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 550.dp)
                .widthIn(max = 400.dp),
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            scheme.primaryContainer,
                            RoundedCornerShape(
                                topStart = AppRadius.sheet,
                                topEnd = AppRadius.sheet,
                            ),
                        )
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(scheme.primary.withAlpha(30), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircleOutline,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    stats.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Box(
                                modifier = Modifier
                                    .background(scheme.primaryContainer, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "$value",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    if (details.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.W600,
                        )
                        Spacer(Modifier.height(8.dp))
                        details.forEach { detail ->
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }

                    if (errors.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Errors",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.W600,
                            color = scheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        errors.forEach { error ->
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        "Done",
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}
