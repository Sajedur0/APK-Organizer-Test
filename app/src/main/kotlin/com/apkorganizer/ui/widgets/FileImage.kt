package com.apkorganizer.ui.widgets

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Flutter-style `Color.withAlpha(int)` — replaces the alpha channel (0–255). */
fun Color.withAlpha(alpha255: Int): Color = copy(alpha = alpha255 / 255f)

/**
 * Displays an image file (e.g. a cached APK/app icon), decoding it on a
 * background thread. Falls back to [fallback] while loading or on error —
 * the equivalent of Flutter's `Image.file(..., errorBuilder: ...)`.
 *
 * The engine already downscales cached icons to 128px, so decoding at full
 * size is cheap and stays crisp at display sizes.
 */
@Composable
fun FileImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fallback: @Composable () -> Unit,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            if (path.isNullOrEmpty()) {
                null
            } else {
                try {
                    BitmapFactory.decodeFile(path)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    val bmp = bitmap
    if (!path.isNullOrEmpty() && bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) { fallback() }
    }
}
