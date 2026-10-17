// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.components.AppCard

/**
 * The password changed, as the web's settings change it: the one the account has and a new one,
 * after which every session of the account ends and the phone goes back to the sign-in.
 */
@Composable
internal fun PasswordCard(
    state: PasswordViewModel.UiState,
    onCurrent: (String) -> Unit,
    onNew: (String) -> Unit,
    onChange: () -> Unit,
) {
    val refusal = state.error as? ApiException
    val refused = refusal?.refusedFields.orEmpty()
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_password), style = MaterialTheme.typography.titleMedium)
        PasswordField(
            state.current,
            onCurrent,
            stringResource(R.string.account_password_current),
            isError = "currentPassword" in refused || refusal?.code == "INVALID_PASSWORD",
            modifier = Modifier.padding(top = 8.dp),
        )
        PasswordField(
            state.new,
            onNew,
            stringResource(R.string.account_password_new),
            isError = "newPassword" in refused,
            hint = stringResource(R.string.auth_password_rules),
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onChange,
            enabled = !state.changing && !state.changed && state.current.isNotEmpty() && state.new.isNotEmpty(),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(stringResource(if (state.changing) R.string.account_password_changing else R.string.account_password_change))
        }
        if (state.changed) {
            Text(
                stringResource(R.string.account_password_changed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
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
