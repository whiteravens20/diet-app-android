// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.AccountSession
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatMoment
import net.whiteravens.dietapp.ui.components.AccentChip
import net.whiteravens.dietapp.ui.components.AppCard

/**
 * Where the account is signed in, as the web's settings show it: every open session with what it
 * was opened from and when it was last used, this phone's named, with when it ends; any of them
 * signed out, or all at once.
 */
@Composable
internal fun SessionsCard(
    state: SessionsViewModel.UiState,
    onEnd: (String) -> Unit,
    onEndAll: () -> Unit,
    onRetry: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_sessions), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.account_sessions_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (state.readFailed != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.account_sessions_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
            }
        }
        state.sessions?.let { open ->
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
            ) {
                open.forEachIndexed { index, session ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SessionRow(session, enabled = !state.ending, onEnd = { onEnd(session.id) })
                }
            }
        }
        state.endFailed?.let {
            Text(
                errorMessage(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        OutlinedButton(
            onClick = onEndAll,
            enabled = !state.ending,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.account_session_end_all))
        }
        Text(
            stringResource(R.string.account_session_end_all_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * One open session: its name, this phone's marked under it, when it began and was last used, and its
 * own sign-out, which says whose it is. The name has the row's width beside the sign-out to itself.
 */
@Composable
private fun SessionRow(session: AccountSession, enabled: Boolean, onEnd: () -> Unit) {
    val name = session.label ?: stringResource(R.string.account_session_unknown)
    val described = stringResource(R.string.account_session_end_named, name)
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnd, enabled = enabled, modifier = Modifier.semantics { contentDescription = described }) {
                Text(stringResource(R.string.account_session_end))
            }
        }
        if (session.current) AccentChip(stringResource(R.string.account_session_current), Modifier.padding(bottom = 4.dp))
        Text(
            stringResource(R.string.account_session_times, formatMoment(session.startedAt), formatMoment(session.lastUsedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (session.current) {
            Text(
                stringResource(R.string.account_session_ends, formatMoment(session.endsAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
