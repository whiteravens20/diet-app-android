// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.AccountNotification
import net.whiteravens.dietapp.domain.NotificationAbout
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatMoment
import net.whiteravens.dietapp.ui.common.notificationText
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton

/**
 * The account's notifications not read yet, the newest first, each with the profile it is for. One
 * opened is read and leads where it points: a weight reminder to its profile's weights on the
 * dashboard ([onWeight]), a review of preferences to the profiles ([onPreferences]). One put away is
 * read where it stands, and all can be read at once.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onWeight: (profileId: String) -> Unit,
    onPreferences: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = stringResource(R.string.notifications_title), onBack = onBack) }
        if (state.items.isNotEmpty()) {
            item {
                // On a line of its own: beside the title, the words would squeeze it on a narrow phone.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = viewModel::readAll, enabled = state.marking == null) {
                        Text(stringResource(R.string.notifications_read_all))
                    }
                }
            }
        }
        state.error?.let { error ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ErrorBanner(errorMessage(error))
                    // What could not be read is asked for again; a notification not marked read stays to try again.
                    if (state.items.isEmpty()) {
                        Button(onClick = viewModel::load) { Text(stringResource(R.string.common_retry)) }
                    }
                }
            }
        }
        when {
            state.loading -> items(2) { Skeleton(Modifier.fillMaxWidth().height(72.dp)) }

            state.items.isEmpty() && state.error == null -> item {
                Text(
                    stringResource(R.string.notifications_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> items(state.items, key = { it.id }) { notification ->
                val go: (() -> Unit)? = when (notification.about) {
                    is NotificationAbout.WeightReminder -> { { onWeight(notification.profileId) } }
                    NotificationAbout.PreferencesReview -> onPreferences
                    is NotificationAbout.Other -> null
                }
                NotificationCard(
                    notification,
                    profileName = state.names[notification.profileId],
                    enabled = state.marking == null,
                    onOpen = go?.let { { viewModel.open(notification, it) } },
                    onDismiss = { viewModel.dismiss(notification) },
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AccountNotification,
    profileName: String?,
    enabled: Boolean,
    onOpen: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AppCard {
        // The text has the card's whole width, which a small phone needs, and the dismissal comes under it.
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (onOpen != null) Modifier.clickable(enabled = enabled, onClick = onOpen) else Modifier),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            profileName?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(notificationText(notification.about), style = MaterialTheme.typography.bodyMedium)
            Text(
                formatMoment(notification.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.notifications_dismiss))
        }
    }
}

/** The bell in the dashboard's header, with the number of notifications not read yet; it opens them. */
@Composable
fun NotificationsBell(unread: Int, onClick: () -> Unit) {
    val said = if (unread > 0) pluralStringResource(R.plurals.notifications_unread, unread, unread) else stringResource(R.string.notifications_title)
    IconButton(onClick = onClick) {
        // The badge's number is in what the button says already.
        BadgedBox(badge = { if (unread > 0) Badge(Modifier.clearAndSetSemantics {}) { Text(if (unread > MOST_COUNTED) "$MOST_COUNTED+" else "$unread") } }) {
            Icon(Icons.Default.Notifications, contentDescription = said)
        }
    }
}

/** The most notifications the bell counts one by one, as the web's does. */
private const val MOST_COUNTED = 9
