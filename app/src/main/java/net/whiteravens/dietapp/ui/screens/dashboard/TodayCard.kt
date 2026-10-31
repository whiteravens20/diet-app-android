// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.screens.diary.EatenAgainstTarget
import java.time.LocalDate

/**
 * What a profile ate today against the day's target, as the server worked it out, with how many
 * changes that wait to be sent it leaves out, the way to the diary ([onOpen]) and to its window that
 * adds an entry ([onAdd]). Without a network, a day not kept on the phone says so; adding works all
 * the same.
 */
@Composable
internal fun TodayCard(
    stats: DashboardViewModel.ProfileStats,
    leftOut: Int,
    offline: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    // A day read before midnight is not today's, however long the application stays open.
    val day = stats.today?.takeIf { it.date == LocalDate.now().toString() }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.diary_today_title), style = MaterialTheme.typography.titleMedium)
            when {
                day != null -> EatenAgainstTarget(day, leftOut)
                stats.todayError != null -> Text(
                    errorMessage(stats.todayError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                offline || stats.keptOnly -> Text(
                    stringResource(R.string.diary_not_on_the_phone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text(stringResource(R.string.diary_add)) }
                OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.diary_open)) }
            }
        }
    }
}
