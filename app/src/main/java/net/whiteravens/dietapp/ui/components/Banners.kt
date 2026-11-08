// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.ServerVersion
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatSyncedAt

/** Inline error strip (destructive tint), matching the web's error notices. */
@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** Muted "offline — showing cached data" strip for the offline-first screens. */
@Composable
fun OfflineBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Names a server whose version this build was not checked against, or says that its
 * version is not supported; shows nothing for the version the build was checked against.
 */
@Composable
fun ServerVersionNotice(version: ServerVersion, modifier: Modifier = Modifier) {
    val text = when (version) {
        is ServerVersion.Other -> stringResource(R.string.server_other_version, version.server, version.checkedAgainst)
        ServerVersion.Unsupported -> stringResource(R.string.server_unsupported)
        ServerVersion.Checked, ServerVersion.Unknown -> return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/**
 * Says at the bottom of every screen that the account's address is not confirmed, where it is not:
 * in one sentence, and at "Details" what confirming gives on this instance, with a button that sends the
 * link again (a link lasts an hour) and "Not now", which puts the notice aside. It is drawn in the calm
 * colours of a card, not those of a warning. Shows nothing where no address waits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfirmEmailNotice(state: ConfirmEmailViewModel.UiState, onResend: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val address = state.waiting ?: return
    var more by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                // One sentence, so that the notice takes little of the screen; the whole at "Details".
                Text(
                    if (more) {
                        stringResource(if (state.unlocksAi) R.string.confirm_email_notice_ai else R.string.confirm_email_notice_plain, address)
                    } else {
                        stringResource(if (state.unlocksAi) R.string.confirm_email_short_ai else R.string.confirm_email_short_plain)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            when {
                state.sent -> Text(
                    stringResource(R.string.confirm_email_sent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error != null -> Text(
                    errorMessage(state.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Its three buttons take a second line on a narrow phone, where a row would squeeze the last one.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResend, enabled = !state.sending, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(if (state.sending) R.string.confirm_email_sending else R.string.confirm_email_resend))
                }
                TextButton(onClick = onLater) { Text(stringResource(R.string.confirm_email_later)) }
                if (!more) TextButton(onClick = { more = true }) { Text(stringResource(R.string.confirm_email_more)) }
            }
        }
    }
}

/**
 * The screen shows what it read at [readAt] (not said where it is not known), as the server cannot
 * be reached now, in the words of [says], which take the time; and how many changes made on the
 * phone wait to be sent, where any does.
 */
@Composable
fun OfflineAsOf(readAt: Long?, modifier: Modifier = Modifier, @StringRes says: Int = R.string.offline_cached) {
    val asOf = stringResource(says, readAt?.let(::formatSyncedAt) ?: "—")
    val waiting = LocalWaitingChanges.current
    OfflineBanner(if (waiting > 0) "$asOf$DOT${pluralStringResource(R.plurals.changes_waiting, waiting, waiting)}" else asOf, modifier)
}
