// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.UpdateViewModel

/** Whether the application asks for a newer build of itself, on until turned off, and what asking sends. */
@Composable
internal fun UpdatesCard(update: UpdateViewModel) {
    val on by update.on.collectAsStateWithLifecycle()
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.updates_title), style = MaterialTheme.typography.titleMedium)
        CheckRow(
            label = stringResource(R.string.updates_ask),
            hint = stringResource(R.string.updates_ask_hint),
            checked = on,
            onChange = update::turn,
        )
    }
}
