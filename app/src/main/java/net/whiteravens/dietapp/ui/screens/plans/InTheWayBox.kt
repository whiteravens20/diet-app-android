// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.RelaxationNeeded
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.screens.profiles.WarningBox

/**
 * What stands in the way of a plan, or a part of it, that could only be filled outside its limits:
 * how many meals would go outside and which limits, the first few meals by day, and the plan made
 * once those limits may give way.
 */
@Composable
internal fun InTheWayBox(needed: RelaxationNeeded, busy: Boolean, onLetGiveWay: () -> Unit, onCancel: () -> Unit) {
    WarningBox {
        Text(
            pluralStringResource(R.plurals.plans_limits_in_the_way, needed.meals.size, needed.meals.size, limitNames(needed.needs)),
            style = MaterialTheme.typography.bodyMedium,
        )
        needed.meals.take(MEALS_NAMED).forEach { meal ->
            Text(
                "• ${formatDayDate(meal.date.toString())} · ${mealTypeLabel(meal.slot)}: ${limitNames(meal.gaveWay)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val more = needed.meals.size - MEALS_NAMED
        if (more > 0) Text(pluralStringResource(R.plurals.plans_limits_in_the_way_more, more, more), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLetGiveWay, enabled = !busy, shape = MaterialTheme.shapes.medium) {
                Text(pluralStringResource(R.plurals.plans_let_give_way, needed.needs.size))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
        }
    }
}

/** How many of the meals that stand in the way of a plan are named one by one. */
private const val MEALS_NAMED = 6
