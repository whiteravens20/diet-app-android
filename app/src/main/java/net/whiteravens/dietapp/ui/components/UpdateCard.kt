// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.update.UpdateFailure
import net.whiteravens.dietapp.data.update.UpdateStep
import net.whiteravens.dietapp.data.update.VersionFile
import net.whiteravens.dietapp.ui.common.WhenShownAgain
import net.whiteravens.dietapp.ui.common.formatNumber

/**
 * A newer build of the application: what it is, and getting it, which downloads it, checks it and
 * hands it to the system, which asks before it installs. Until the phone lets the application install
 * applications, the card says so and opens the system's page for it. Nothing shows while no newer
 * build is out, or once the user put this one off. It stands apart from the other cards in the
 * theme's primary colour, a frame and a light tint of it, for news, not the amber of a warning.
 */
@Composable
fun UpdateCard(modifier: Modifier = Modifier, viewModel: UpdateViewModel = hiltViewModel()) {
    val version by viewModel.available.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(viewModel.allowed()) }
    // The allowance is given on the system's page: read again on coming back from it.
    WhenShownAgain { allowed = viewModel.allowed() }
    val shown = version ?: return

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) { UpdateContent(shown, step, allowed, viewModel) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateContent(shown: VersionFile, step: UpdateStep, allowed: Boolean, viewModel: UpdateViewModel) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(AppIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.update_available, shown.versionName), style = MaterialTheme.typography.titleMedium)
    }
    Text(stringResource(R.string.update_size, formatNumber(shown.size / 1_048_576.0, 1)), style = MaterialTheme.typography.bodySmall)
    when (val now = step) {
        is UpdateStep.Downloading -> {
            val share = if (now.of > 0) (now.done.toFloat() / now.of).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            Said(stringResource(R.string.update_downloading, (share * 100).toInt()))
        }
        UpdateStep.Checking -> Said(stringResource(R.string.update_checking))
        // The system's question may be left open: getting it again, or later, stays offered.
        UpdateStep.Idle, UpdateStep.Handed, is UpdateStep.Failed -> {
            if (now == UpdateStep.Handed) Said(stringResource(R.string.update_handed))
            if (now is UpdateStep.Failed) {
                Text(
                    failureText(now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!allowed) Said(stringResource(R.string.update_allow_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (allowed) {
                    Button(onClick = { viewModel.get(shown) }) { Text(stringResource(R.string.update_get)) }
                } else {
                    Button(onClick = { context.startActivity(viewModel.allowance()) }) { Text(stringResource(R.string.update_allow)) }
                }
                TextButton(onClick = { viewModel.later(shown) }) { Text(stringResource(R.string.update_later)) }
            }
        }
    }
}

@Composable
private fun Said(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun failureText(failed: UpdateStep.Failed): String = when (failed.why) {
    UpdateFailure.NOT_FROM_CHANNEL -> stringResource(R.string.update_failed_channel)
    UpdateFailure.DOWNLOAD -> stringResource(R.string.update_failed_download)
    UpdateFailure.CHECKSUM -> stringResource(R.string.update_failed_checksum)
    UpdateFailure.NOT_OURS -> stringResource(R.string.update_failed_not_ours)
    UpdateFailure.CANCELLED -> stringResource(R.string.update_failed_cancelled)
    UpdateFailure.INSTALL -> stringResource(R.string.update_failed_install, failed.detail.orEmpty())
}
