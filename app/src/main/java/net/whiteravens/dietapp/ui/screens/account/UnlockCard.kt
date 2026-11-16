// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.Text

/**
 * The unlock by a fingerprint: shown where the phone has a strong biometric enrolled, or where it is on already (so
 * that it can be turned off after the phone lost its fingerprints). Turning it on and turning it off each ask for one
 * unlock; cancelled, nothing changes.
 */
@Composable
internal fun UnlockCard(viewModel: UnlockViewModel) {
    val on by viewModel.on.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    // What the phone has enrolled changes in its own settings: read again whenever the screen comes back.
    var available by remember { mutableStateOf(viewModel.available()) }
    LifecycleResumeEffect(Unit) {
        available = viewModel.available()
        onPauseOrDispose {}
    }
    if (!available && on != true) return

    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.unlock_about),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        CheckRow(
            label = stringResource(R.string.unlock_switch),
            hint = stringResource(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) R.string.unlock_recents else R.string.unlock_recents_secure),
            checked = on == true,
            enabled = on != null && !busy,
            onChange = { wanted -> if (wanted) viewModel.turnOn() else viewModel.turnOff() },
        )
        if (failed) {
            Text(
                stringResource(R.string.unlock_turn_on_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
