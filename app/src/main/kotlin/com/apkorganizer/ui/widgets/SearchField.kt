package com.apkorganizer.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The rounded pill search field used in the app bars and the filter sheet —
 * a port of the Flutter 44dp-high filled search field (999 radius, filled
 * with surfaceContainerHighest@120, search/clear icons at 150 alpha).
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    autofocus: Boolean = false,
    onSearchSubmitted: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autofocus) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(44.dp)
            .background(
                scheme.surfaceContainerHighest.withAlpha(120),
                CircleShape,
            )
            .focusRequester(focusRequester),
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 14.sp,
            color = scheme.onSurface,
        ),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                onSearchSubmitted()
            },
        ),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp),
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant.withAlpha(150),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            hint,
                            color = scheme.onSurfaceVariant.withAlpha(150),
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant.withAlpha(150),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    )
}

/** The 40x4 drag handle shown at the top of every bottom sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 12.dp)
            .width(40.dp)
            .height(4.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(77),
                shape = CircleShape,
            ),
    )
}
