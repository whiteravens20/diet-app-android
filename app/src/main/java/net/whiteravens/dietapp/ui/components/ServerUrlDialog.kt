// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.config.AddressProblem
import net.whiteravens.dietapp.data.config.LOCAL_NETWORK_PERMISSION
import net.whiteravens.dietapp.domain.ServerProbe
import net.whiteravens.dietapp.ui.components.ServerAddressViewModel.State

/**
 * Edit the server's address. What is typed is read into the base of the server's API and asked
 * what answers there ([ServerAddressViewModel]); an address where a Diet App API answers goes to
 * [onSave], one where none does is named and goes there only when asked again. Shows [warning]
 * above the field when the caller needs a consequence spelled out (e.g. "this signs you out").
 */
@Composable
fun ServerUrlDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    warning: String? = null,
    model: ServerAddressViewModel = hiltViewModel(),
) {
    var value by rememberSaveable { mutableStateOf(current) }
    val state by model.state.collectAsState()

    // A dialog opened again starts from what is typed in it.
    DisposableEffect(model) { onDispose { model.typing() } }
    val askLocalNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), model::localNetworkAnswered)
    LaunchedEffect(state) {
        if (state is State.NeedsLocalNetwork) askLocalNetwork.launch(LOCAL_NETWORK_PERMISSION)
    }
    val context = LocalContext.current
    val answers = state as? State.Answers
    LaunchedEffect(answers) {
        if (answers != null) {
            onSave(answers.url)
            onDismiss()
        }
    }

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
                val note = addressNote(state)
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        model.typing()
                    },
                    placeholder = { Text(stringResource(R.string.server_placeholder)) },
                    singleLine = true,
                    enabled = state !is State.Checking,
                    isError = state is State.Wrong || state is State.Doubtful || state is State.LocalNetworkRefused,
                    supportingText = note?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val doubtful = state as? State.Doubtful
            if (state is State.LocalNetworkRefused) {
                Button(onClick = { context.startActivity(appSettings(context)) }) {
                    Text(stringResource(R.string.server_open_settings))
                }
            } else if (doubtful != null) {
                Button(
                    onClick = {
                        onSave(doubtful.url)
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.server_save_anyway)) }
            } else {
                Button(onClick = { model.check(value) }, enabled = state !is State.Checking && state !is State.NeedsLocalNetwork) {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** What the dialog says under the address, or nothing. */
@Composable
private fun addressNote(state: State): String? = when (state) {
    State.Typing, is State.Answers -> null
    State.Checking, is State.NeedsLocalNetwork -> stringResource(R.string.server_checking)
    is State.LocalNetworkRefused -> stringResource(R.string.server_local_network_refused)
    is State.Wrong -> stringResource(
        when (state.problem) {
            AddressProblem.NOT_AN_ADDRESS -> R.string.server_not_an_address
            AddressProblem.PLAIN_HTTP -> R.string.server_plain_http
            AddressProblem.CREDENTIALS -> R.string.server_credentials
            AddressProblem.QUERY_OR_FRAGMENT -> R.string.server_query_or_fragment
            AddressProblem.PAST_THE_API -> R.string.server_past_the_api
        },
    )
    is State.Doubtful -> stringResource(
        when (state.probe) {
            ServerProbe.Answers, ServerProbe.NotTheApi -> R.string.server_not_the_api
            ServerProbe.DatabaseDown -> R.string.server_database_down
            ServerProbe.UnknownHost -> R.string.server_unknown_host
            ServerProbe.NoAnswer -> R.string.server_no_answer
            ServerProbe.Untrusted -> R.string.server_untrusted
        },
    )
}

/** The page of the phone's settings where the app's permissions are given. */
fun appSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
