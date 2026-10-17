// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import net.whiteravens.dietapp.ui.components.AppCard

/**
 * The account's address changed, as the web's settings change it: a mail with a link goes to the
 * new address, and the link, opened in the web application, makes the change. Without mail on the
 * instance ([mailsSent] false) the change is not offered, and says why.
 */
@Composable
internal fun EmailCard(
    state: EmailChangeViewModel.UiState,
    mailsSent: Boolean?,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSend: () -> Unit,
) {
    val refusal = state.error as? ApiException
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_email), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.account_email_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        when {
            state.sent -> Text(
                stringResource(R.string.account_email_sent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )

            mailsSent == false -> Text(
                stringResource(R.string.account_email_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            mailsSent == null -> Unit

            state.editing -> {
                OutlinedTextField(
                    value = state.newEmail,
                    onValueChange = onEmail,
                    label = { Text(stringResource(R.string.account_email_new)) },
                    isError = "newEmail" in refusal?.refusedFields.orEmpty() || refusal?.code == "EMAIL_TAKEN",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                PasswordField(
                    state.password,
                    onPassword,
                    stringResource(R.string.account_password_current),
                    isError = refusal?.code == "INVALID_PASSWORD",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Button(
                        onClick = onSend,
                        enabled = !state.sending && state.newEmail.isNotBlank() && state.password.isNotEmpty(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(stringResource(if (state.sending) R.string.account_email_sending else R.string.account_email_submit))
                    }
                    TextButton(onClick = onCancel, enabled = !state.sending) { Text(stringResource(R.string.common_cancel)) }
                }
            }

            else -> OutlinedButton(onClick = onOpen, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.account_email_change))
            }
        }
        state.error?.let {
            Text(
                errorMessage(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
