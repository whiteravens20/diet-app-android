// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import java.time.LocalDate

/**
 * The window that puts one of the profile's favourite sets on days of [plan], as the web's is: a set
 * picked, and the days, every one of them to begin with. Each meal the set names takes the place of
 * the day's meal of its kind; the day's other meals stay.
 */
@Composable
internal fun ApplySetDialog(
    plan: MealPlan,
    window: PlanDetailViewModel.SetWindow,
    onApply: (setId: String, dates: List<String>, together: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // A day that is over keeps what was on it: a set is not offered for it.
    val today = LocalDate.now().toString()
    val days = plan.days.map { it.date }.filter { it >= today }
    var only by rememberSaveable(plan.id) { mutableStateOf(false) }
    var picked by rememberSaveable(plan.id) { mutableStateOf<String?>(null) }
    var chosen by rememberSaveable(plan.id) { mutableStateOf(days) }
    // The text of the choice the window waits for, said where Apply was pressed without it.
    var missing by remember { mutableStateOf<Int?>(null) }
    val sets = window.sets
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorite_sets_apply)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    sets == null -> Skeleton(Modifier.fillMaxWidth().height(56.dp))
                    window.readError != null -> ErrorBanner(errorMessage(window.readError))
                    sets.isEmpty() -> Text(stringResource(R.string.favorite_sets_empty), style = MaterialTheme.typography.bodyMedium)
                    else -> ChoiceField(
                        label = stringResource(R.string.favorite_sets_apply_pick_set),
                        options = listOf(null) + sets.map { it.id },
                        selected = picked,
                        optionLabel = { id -> sets.firstOrNull { it.id == id }?.label ?: "—" },
                        onSelect = {
                            picked = it
                            missing = null
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.favorite_sets_apply_pick_days), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            chosen = days
                            missing = null
                        },
                    ) { Text(stringResource(R.string.favorite_sets_apply_all_days)) }
                }
                days.forEach { day ->
                    CheckRow(
                        label = formatDayDate(day),
                        checked = day in chosen,
                        enabled = !window.applying,
                        onChange = { on ->
                            chosen = if (on) days.filter { it in chosen || it == day } else chosen - day
                            missing = null
                        },
                    )
                }
                if (days.isEmpty()) Text(stringResource(R.string.favorite_sets_apply_no_days), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.favorite_sets_apply_own_meal_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (plan.cooksAhead) {
                    if (!only) {
                        Text(
                            stringResource(R.string.favorite_sets_apply_cooking_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CheckRow(label = stringResource(R.string.plans_cooking_only_chosen), checked = only, onChange = { only = it })
                }
                missing?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                window.error?.let { ErrorBanner("${stringResource(R.string.favorite_sets_err_apply)} ${errorMessage(it)}") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val set = picked
                    when {
                        set == null -> missing = R.string.favorite_sets_apply_pick_set
                        chosen.isEmpty() -> missing = R.string.favorite_sets_apply_pick_days
                        else -> onApply(set, days.filter { it in chosen }, plan.cooksAhead && !only)
                    }
                },
                enabled = !window.applying && !sets.isNullOrEmpty() && days.isNotEmpty(),
            ) { Text(stringResource(R.string.favorite_sets_apply_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !window.applying) { Text(stringResource(R.string.common_cancel)) } },
    )
}
