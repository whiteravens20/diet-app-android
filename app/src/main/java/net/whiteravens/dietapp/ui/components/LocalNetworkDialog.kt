// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.config.LOCAL_NETWORK_PERMISSION

/**
 * The server the app keeps is on the local network, which Android 17 lets it reach only with the
 * permission for nearby devices. Asks for it; once refused, points to the phone's settings, where
 * it is given. [onAnswered] is told after each answer.
 */
@Composable
fun LocalNetworkDialog(onAnswered: () -> Unit) {
    val context = LocalContext.current
    var shown by rememberSaveable { mutableStateOf(true) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refused = !granted
        onAnswered()
    }
    if (!shown) return

    AlertDialog(
        onDismissRequest = { shown = false },
        title = { Text(stringResource(R.string.local_network_title)) },
        text = { Text(stringResource(if (refused) R.string.local_network_refused else R.string.local_network_body)) },
        confirmButton = {
            if (refused) {
                Button(onClick = { context.startActivity(appSettings(context)) }) {
                    Text(stringResource(R.string.server_open_settings))
                }
            } else {
                Button(onClick = { ask.launch(LOCAL_NETWORK_PERMISSION) }) { Text(stringResource(R.string.local_network_allow)) }
            }
        },
        dismissButton = { TextButton(onClick = { shown = false }) { Text(stringResource(R.string.common_not_now)) } },
    )
}
