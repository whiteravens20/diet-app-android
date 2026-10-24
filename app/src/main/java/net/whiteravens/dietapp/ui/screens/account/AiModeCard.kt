// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.AdminAccess
import net.whiteravens.dietapp.domain.AiModes
import net.whiteravens.dietapp.domain.AiStatus
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatMoment
import net.whiteravens.dietapp.ui.components.AppCard

/**
 * What AI the account has, as the web's settings show it: the mode it chose among those the
 * instance and its providers allow, why the instance's provider is not offered where it is not, or
 * is off for this account and what it takes, with whom to ask ([contact]) where the operator
 * approves accounts; that a model can err once one is on; and, with the instance's provider, how
 * many of the month's requests were used, whether the instance has used all of its own, until when
 * the account is new, and when a request comes free again.
 */
@Composable
internal fun AiModeCard(state: AiViewModel.UiState, contact: String?, onChoose: (String) -> Unit, onRetry: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_ai), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.account_ai_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (state.statusFailed != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.account_ai_status_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
            }
        }
        val status = state.status ?: return@AppCard
        val mode = state.mode
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.account_ai_mode), style = MaterialTheme.typography.titleSmall)
            state.modes.forEach { option ->
                ModeRow(
                    option,
                    status,
                    chosen = option == mode,
                    enabled = state.canChoose(option),
                    ownMissing = option == AiModes.OWN && state.providers != null && !state.ownProvider,
                    onChoose = { onChoose(option) },
                )
            }
            if (!status.shared) {
                Text(
                    stringResource(if (status.adminProviderConfigured) R.string.account_ai_mode_admin_disabled else R.string.account_ai_mode_admin_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!status.granted) {
                AccessNote(status.adminAccess, contact)
            }
        }
        if (mode != null && mode != AiModes.NONE) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            ) {
                Text(stringResource(R.string.account_ai_disclaimer), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }
        if (mode == AiModes.INSTANCE) Allowance(status)
        when {
            state.choosing != null -> Note(stringResource(R.string.account_ai_mode_saving))
            state.modeSaved -> Note(stringResource(R.string.account_ai_mode_saved), MaterialTheme.colorScheme.primary)
        }
        state.modeError?.let {
            Note("${stringResource(R.string.account_ai_mode_failed)} ${errorMessage(it)}", MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * One mode, with what it means: the instance's provider says how many requests a month it gives,
 * and the account's own, where it has no provider yet, that one has to be added first.
 */
@Composable
private fun ModeRow(mode: String, status: AiStatus, chosen: Boolean, enabled: Boolean, ownMissing: Boolean, onChoose: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        color = if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = onChoose),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp).alpha(if (enabled || chosen) 1f else 0.6f), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = chosen, onClick = null, enabled = enabled)
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    stringResource(
                        when (mode) {
                            AiModes.NONE -> R.string.account_ai_mode_none
                            AiModes.INSTANCE -> R.string.account_ai_mode_admin
                            else -> R.string.account_ai_mode_byok
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    when (mode) {
                        AiModes.NONE -> stringResource(R.string.account_ai_mode_none_hint)
                        AiModes.INSTANCE -> pluralStringResource(R.plurals.account_ai_mode_admin_hint, status.limit, status.limit)
                        else -> stringResource(R.string.account_ai_mode_byok_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ownMissing) {
                    Text(
                        stringResource(R.string.account_ai_mode_byok_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The instance's allowance as the account has used it: the month's requests used of those it may
 * make, that the instance has used all of its own where it has, and when a request comes free again,
 * or that none was used yet.
 */
@Composable
private fun Allowance(status: AiStatus) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.account_ai_allowance), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(pluralStringResource(R.plurals.account_ai_allowance_used, status.limit, status.used, status.limit), style = MaterialTheme.typography.bodyMedium)
            if (status.instanceLimitReached) {
                Text(stringResource(R.string.account_ai_instance_used_up), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                when {
                    !status.granted -> stringResource(R.string.account_ai_no_access)
                    status.resetAt != null -> stringResource(R.string.account_ai_allowance_frees, formatMoment(status.resetAt))
                    else -> stringResource(R.string.account_ai_allowance_unused)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.granted && status.newUntil != null) {
                Text(
                    stringResource(
                        if (status.newAccountsLimitReached) R.string.account_ai_new_accounts_used_up else R.string.account_ai_new_account,
                        formatMoment(status.newUntil),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Why the instance's provider is off for this account, and what it takes: a confirmed address, for
 * which the notice over every screen sends a new link, or the operator's approval, asked for at the
 * address the operator published where there is one.
 */
@Composable
private fun AccessNote(access: String, contact: String?) {
    val uris = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(if (access == AdminAccess.EMAIL_UNCONFIRMED) R.string.account_ai_mode_admin_confirm_email else R.string.account_ai_mode_admin_not_approved),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (access != AdminAccess.EMAIL_UNCONFIRMED) {
            if (contact != null) {
                TextButton(onClick = { uris.openUri("mailto:$contact") }) { Text(contact) }
            } else {
                Text(
                    stringResource(R.string.account_ai_mode_admin_no_contact),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 8.dp))
}
