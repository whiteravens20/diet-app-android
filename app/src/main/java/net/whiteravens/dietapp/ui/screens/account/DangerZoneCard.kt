// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.ui.common.errorMessage

/**
 * The account deleted, as the web's settings delete it: everything it holds goes, which cannot be
 * undone, so its address is typed again, and its password given, before the button does anything.
 */
@Composable
internal fun DangerZoneCard(
    address: String,
    state: AccountDeletionViewModel.UiState,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val danger = MaterialTheme.colorScheme.error
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, danger.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.account_danger), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.account_danger_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val deleteColours = ButtonDefaults.buttonColors(containerColor = danger, contentColor = MaterialTheme.colorScheme.onError)
            if (!state.confirming) {
                Button(onClick = onOpen, colors = deleteColours, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.account_delete))
                }
                return@Column
            }
            Text(stringResource(R.string.account_delete_confirm), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmail,
                label = { Text(stringResource(R.string.account_delete_email)) },
                placeholder = { Text(address) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                state.password,
                onPassword,
                stringResource(R.string.account_delete_password),
                isError = (state.error as? ApiException)?.code == "INVALID_PASSWORD",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onDelete,
                    enabled = !state.deleting && state.confirms(address),
                    colors = deleteColours,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(if (state.deleting) R.string.account_deleting else R.string.account_delete_forever))
                }
                TextButton(onClick = onCancel, enabled = !state.deleting) { Text(stringResource(R.string.common_cancel)) }
            }
            state.error?.let {
                Text(errorMessage(it), style = MaterialTheme.typography.bodySmall, color = danger)
            }
        }
    }
}
