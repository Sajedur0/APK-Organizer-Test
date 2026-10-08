package com.apkorganizer.ui.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

/**
 * Seven pulsing dots (six on a hexagon orbit + one in the center) — a direct
 * port of the Flutter `HexagonDotsLoading` custom painter.
 */
@Composable
fun HexagonDotsLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    minRadius: Dp = 12.dp,
    wavePeriodMs: Int = 1200,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hexDots")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(wavePeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "hexProgress",
    )

    Box(modifier = modifier.size(minRadius * 5), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSizeOfCanvas()) {
            val orbitRadius = size.minDimension * 0.32f
            val minDotRadius = size.minDimension * 0.075f
            val maxDotRadius = size.minDimension * 0.13f
            val twoPi = (PI * 2).toFloat()
            val outerDotCount = 6

            for (i in 0 until outerDotCount) {
                val angle = (-PI / 2 + i * 2 * PI / outerDotCount).toFloat()
                val offset = Offset(
                    cos(angle) * orbitRadius,
                    kotlin.math.sin(angle) * orbitRadius,
                )
                val pulse = pulseFor(progress, i, outerDotCount)
                val radius = minDotRadius + (maxDotRadius - minDotRadius) * pulse
                val alpha = ((100 + 155 * pulse).toInt()).coerceIn(0, 255)
                drawCircle(
                    color = color.copy(alpha = alpha / 255f),
                    radius = radius,
                    center = center + offset,
                )
            }

            val centerPulse = pulseFor(progress, outerDotCount, outerDotCount + 1)
            drawCircle(
                color = color.copy(alpha = ((120 + 135 * centerPulse).toInt().coerceIn(0, 255)) / 255f),
                radius = minDotRadius + (maxDotRadius - minDotRadius) * centerPulse,
                center = center,
            )
        }
    }
}

/** Euclidean modulo — matches Dart's `%` operator for negative operands. */
private fun pulseFor(progress: Float, index: Int, total: Int): Float {
    val shifted = (((progress - index.toFloat() / total) % 1f) + 1f) % 1f
    val twoPi = (PI * 2).toFloat()
    return (cos(shifted * twoPi - PI.toFloat()) + 1f) / 2f
}
