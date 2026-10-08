package com.apkorganizer.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact bordered action chip used in the selection bottom bars. */
@Composable
fun CompactActionChip(
    icon: ImageVector,
    label: String,
    color: Color,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val radius = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .background(color = color.withAlpha(20), shape = radius)
            .border(BorderStroke(1.dp, color.withAlpha(50)), radius)
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.height(18.dp).width(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = color,
            fontWeight = FontWeight.W600,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
