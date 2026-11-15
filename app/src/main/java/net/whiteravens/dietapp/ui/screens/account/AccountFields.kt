// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.whiteravens.dietapp.ui.components.OutlinedSecretField
import net.whiteravens.dietapp.ui.components.Secret
import net.whiteravens.dietapp.ui.components.Text

/**
 * A password, or the key of a provider ([secret]), typed into one of the account's forms: shown as dots until its eye
 * is pressed, with [hint] under it where given, and dots again once the form is [sending] it.
 */
@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    hint: String? = null,
    secret: Secret = Secret.Password,
    sending: Boolean = false,
) {
    OutlinedSecretField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = hint?.let { { Text(it) } },
        secret = secret,
        sending = sending,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}
