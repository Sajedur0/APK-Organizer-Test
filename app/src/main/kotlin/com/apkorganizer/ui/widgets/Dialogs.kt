package com.apkorganizer.ui.widgets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.apkorganizer.ui.theme.AppRadius

/**
 * Confirmation dialog matching the app's dialogs: "Cancel" text button plus a
 * filled "Confirm" button (error-colored when [danger], like the delete /
 * organize confirmations).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    danger: Boolean = false,
    onResult: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { onResult(false) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = { onResult(true) },
                colors = if (danger) {
                    ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = { onResult(false) }) { Text("Cancel") }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.dialog),
        containerColor = scheme.surfaceContainerLow,
    )
}

/** Permission rationale dialog: "Cancel" + "Grant Permission". */
@Composable
fun PermissionRationaleDialog(
    title: String,
    message: String,
    onResult: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { onResult(false) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = { onResult(true) }) { Text("Grant Permission") }
        },
        dismissButton = {
            TextButton(onClick = { onResult(false) }) { Text("Cancel") }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.dialog),
        containerColor = scheme.surfaceContainerLow,
    )
}

/**
 * Single-line text input dialog (rename APK / create folder), with autofocus
 * and submit-on-IME-action — the equivalent of the Flutter rename dialogs.
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialText: String,
    confirmLabel: String,
    hint: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                label = { Text(label) },
                placeholder = hint?.let { { Text(it) } },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.control),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outlineVariant,
                    focusedContainerColor = scheme.surfaceContainerLow,
                    unfocusedContainerColor = scheme.surfaceContainerLow,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.dialog),
        containerColor = scheme.surfaceContainerLow,
    )
}

/** Padding constant used across the detail rows (kept for symmetry). */
val DialogContentPadding = 24.dp

/** Pending confirmation request rendered by the screens. */
class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String = "Confirm",
    val danger: Boolean = false,
    val onResult: (Boolean) -> Unit,
)

/** Pending permission-rationale request rendered by the screens. */
class PermissionRationaleRequest(
    val title: String,
    val message: String,
    val onResult: (Boolean) -> Unit,
)

/** Pending directory picker request (roots + continuation). */
class DirectoryPickerRequest(
    val initialDirectories: List<com.apkorganizer.data.DirectoryEntry>,
    val onSelect: (String?) -> Unit,
)
