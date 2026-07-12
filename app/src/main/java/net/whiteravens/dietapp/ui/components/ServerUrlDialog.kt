package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.config.ServerConfigStore

/**
 * Edit the API base URL. Validates to an http(s) URL and hands the normalized
 * value (trailing slash included) to [onSave]; shows [warning] above the field
 * when the caller needs a consequence spelled out (e.g. "this signs you out").
 */
@Composable
fun ServerUrlDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    warning: String? = null,
) {
    var value by rememberSaveable { mutableStateOf(current) }
    var invalid by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (warning != null) {
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; invalid = false },
                    placeholder = { Text(stringResource(R.string.server_placeholder)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.server_invalid)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalized = ServerConfigStore.normalize(value)
                    if (normalized == null) invalid = true
                    else {
                        onSave(normalized)
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
