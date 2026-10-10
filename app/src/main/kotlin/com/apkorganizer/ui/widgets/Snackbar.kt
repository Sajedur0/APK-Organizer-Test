package com.apkorganizer.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkorganizer.ui.theme.AppRadius
import com.apkorganizer.ui.theme.FrostedSurface
import com.apkorganizer.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Floating snackbar controller — a faithful port of the Flutter snackbar
 * behaviour used across the app:
 *  - a new snackbar immediately replaces the previous one,
 *  - optional action label with a callback,
 *  - exact durations (4s default, 6s for undo-able actions),
 *  - error snackbars use the error color.
 */
class SnackbarController(private val scope: CoroutineScope) {

    class SnackData(
        val message: String,
        val isError: Boolean,
        val actionLabel: String?,
        val onAction: (() -> Unit)?,
    )

    var current by mutableStateOf<SnackData?>(null)
        private set

    private var job: Job? = null

    fun show(
        message: String,
        isError: Boolean = false,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMs: Long = 4000,
    ) {
        job?.cancel()
        current = SnackData(message, isError, actionLabel, onAction)
        job = scope.launch {
            delay(durationMs)
            current = null
        }
    }

    fun dismiss() {
        job?.cancel()
        current = null
    }

    fun performAction() {
        val action = current?.onAction
        dismiss()
        action?.invoke()
    }
}

/** The floating snackbar itself, styled exactly like the Flutter theme's. */
@Composable
fun ApkSnackbarHost(
    controller: SnackbarController,
    modifier: Modifier = Modifier,
) {
    val data = controller.current ?: return
    ApkSnackbar(
        data = data,
        onAction = { controller.performAction() },
        modifier = modifier,
    )
}

@Composable
fun ApkSnackbar(
    data: SnackbarController.SnackData,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalIsDarkTheme.current
    val containerColor =
        if (data.isError) scheme.error
        else if (isDark) scheme.surfaceContainerHighest else scheme.inverseSurface
    val contentColor =
        if (isDark) scheme.onSurface else scheme.inverseOnSurface
    val actionColor = if (data.isError) scheme.onError else scheme.inversePrimary

    FrostedSurface(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.control),
        tint = Color.Transparent,
        border = null,
    ) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.control),
        color = containerColor,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = data.message,
                    color = contentColor,
                    fontWeight = FontWeight.W600,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (data.actionLabel != null) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onAction) {
                    Text(
                        data.actionLabel,
                        color = actionColor,
                        fontWeight = FontWeight.W600,
                    )
                }
            }
        }
    }
    }
}

/** Auto-dismiss helper for simple inline snackbars. */
@Composable
fun AutoDismissSnackbar(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(message) {
        delay(4000)
        onDismiss()
    }
    ApkSnackbar(
        data = SnackbarController.SnackData(message, isError, null, null),
        onAction = {},
    )
}

/** Background helper used by the banner rows (kept for symmetry). */
@Composable
fun BannerSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 210f / 255f),
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.Center) { content() }
    }
}
