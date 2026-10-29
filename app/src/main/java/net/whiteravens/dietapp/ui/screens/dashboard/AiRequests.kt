// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.AiModes
import net.whiteravens.dietapp.domain.AiStatus
import net.whiteravens.dietapp.ui.common.formatMoment
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.components.AppIcons

/** Why an account that uses the instance's AI has none of its requests left. */
internal enum class AiShortage { NO_ACCESS, NOT_SHARED, INSTANCE_USED_UP, NEW_ACCOUNTS_USED_UP, USED_UP }

/**
 * Why none of the instance's AI requests is left to [status], in the order the web's chip says it:
 * the account may not use the instance's AI, the instance shares none, all accounts together used
 * the instance's, new accounts used their part, or the account used its own. Null while requests
 * are left, and where the account asks a provider of its own.
 */
internal fun shortageOf(status: AiStatus): AiShortage? = when {
    status.mode == AiModes.OWN -> null
    !status.granted -> AiShortage.NO_ACCESS
    !status.shared -> AiShortage.NOT_SHARED
    (status.remaining ?: 0) > 0 -> null
    status.instanceLimitReached -> AiShortage.INSTANCE_USED_UP
    status.newUntil != null && status.newAccountsLimitReached -> AiShortage.NEW_ACCOUNTS_USED_UP
    else -> AiShortage.USED_UP
}

/**
 * The account's AI requests, where it uses AI, as the web's chip says them: how many of the
 * instance's are left this month of how many, or that it asks a provider of its own; and, where
 * none is left, why and when one comes free. A tap opens the account's settings, where AI is set.
 */
@Composable
internal fun AiRequests(status: AiStatus, onOpen: () -> Unit) {
    val shortage = shortageOf(status)
    val own = status.mode == AiModes.OWN
    val left = formatNumber(status.remaining ?: 0)
    val limit = formatNumber(status.limit)
    val description = stringResource(R.string.dashboard_ai_left_description, left, limit)
    val tint = if (shortage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AssistChip(
            onClick = onOpen,
            label = { Text(if (own) stringResource(R.string.dashboard_ai_own) else stringResource(R.string.dashboard_ai_left, left, limit)) },
            leadingIcon = { Icon(AppIcons.Sparkles, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            colors = AssistChipDefaults.assistChipColors(
                leadingIconContentColor = tint,
                labelColor = if (shortage != null) tint else MaterialTheme.colorScheme.onSurface,
            ),
            modifier = if (own) Modifier else Modifier.semantics { contentDescription = description },
        )
        shortage?.let { noteOf(it, status) }?.let { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Why no request is left, and when one comes free where the API says so. */
@Composable
private fun noteOf(shortage: AiShortage, status: AiStatus): String? {
    val frees = status.resetAt?.let { formatMoment(it) }
    return when (shortage) {
        AiShortage.NO_ACCESS -> stringResource(R.string.dashboard_ai_no_access)
        AiShortage.NOT_SHARED ->
            stringResource(if (status.adminProviderConfigured) R.string.account_ai_mode_admin_disabled else R.string.account_ai_mode_admin_unavailable)
        AiShortage.INSTANCE_USED_UP -> frees?.let { stringResource(R.string.dashboard_ai_instance_used_up, it) }
        AiShortage.NEW_ACCOUNTS_USED_UP -> frees?.let { stringResource(R.string.dashboard_ai_new_accounts_used_up, it) }
        AiShortage.USED_UP -> frees?.let { stringResource(R.string.dashboard_ai_used_up, it) }
    }
}
