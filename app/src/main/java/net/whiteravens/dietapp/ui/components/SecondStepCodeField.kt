// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import net.whiteravens.dietapp.R

/** The most characters a code of the second step may have, as the API takes it. */
const val SECOND_STEP_CODE_MAX = 40

/**
 * The code of the second step: the six digits of the authenticator app, typed on a number pad, or a
 * recovery code of letters and digits, which the button under the field turns it into, and back. The
 * other kind gets a field of its own, with the keyboard it needs and nothing typed for the first.
 */
@Composable
fun SecondStepCodeField(value: String, onValueChange: (String) -> Unit, isError: Boolean = false) {
    var recovery by rememberSaveable { mutableStateOf(false) }
    var switched by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    Column {
        key(recovery) {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= SECOND_STEP_CODE_MAX) onValueChange(it) },
                label = { Text(stringResource(if (recovery) R.string.account_second_factor_recovery_code else R.string.account_second_factor_code)) },
                isError = isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (recovery) KeyboardType.Ascii else KeyboardType.NumberPassword,
                    capitalization = if (recovery) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        TextButton(
            onClick = {
                recovery = !recovery
                switched = true
                onValueChange("")
            },
        ) {
            Text(stringResource(if (recovery) R.string.account_second_factor_use_app_code else R.string.account_second_factor_use_recovery))
        }
    }
    // The field of the kind just chosen takes the keyboard at once.
    LaunchedEffect(recovery) { if (switched) focus.requestFocus() }
}
