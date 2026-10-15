// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Reroll
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import java.time.LocalDate

/** What the window a plan is made again in opens on: how much of the plan, which day for one day, and whether every limit is to hold. */
data class RerollStart(val scope: RerollScope, val day: String? = null, val holdLimits: Boolean = false)

/**
 * The window a plan is made again in, as the web's: how much of it, what of it may be replaced that
 * is otherwise left alone, whether every limit holds this time, and, before anything is asked of
 * the API, what would be replaced and what kept.
 */
@Composable
internal fun RerollDialog(plan: MealPlan, start: RerollStart, onConfirm: (Reroll) -> Unit, onDismiss: () -> Unit) {
    // The user's own day, as the API is told it with the request.
    val today = LocalDate.now().toString()
    val days = plan.days
    val first = days.first().date
    val last = days.last().date
    val notFromToday = Rerolls.fromTodayRefused(days, today)

    var scope by rememberSaveable { mutableStateOf(start.scope) }
    var from by rememberSaveable { mutableStateOf(if (today > first && today <= last) today else first) }
    var to by rememberSaveable { mutableStateOf(last) }
    var day by rememberSaveable {
        mutableStateOf(start.day ?: (days.firstOrNull { !it.skipped && it.date >= today } ?: days.firstOrNull { !it.skipped } ?: days.first()).date)
    }
    var includePast by rememberSaveable { mutableStateOf(false) }
    var replaceEaten by rememberSaveable { mutableStateOf(false) }
    var replaceCustom by rememberSaveable { mutableStateOf(false) }
    var holdLimits by rememberSaveable { mutableStateOf(start.holdLimits) }

    // What is asked about is what the days chosen hold: a tick for something they do not hold says nothing.
    val nothingMore = RerollChoice(scope, from, to, day)
    val past = Rerolls.whatCouldGo(days, nothingMore, today).past
    val withPast = nothingMore.copy(includePast = past && includePast)
    val couldGo = Rerolls.whatCouldGo(days, withPast, today)
    val choice = withPast.copy(replaceEaten = couldGo.eaten && replaceEaten, replaceCustom = couldGo.custom && replaceCustom)
    val outcome = Rerolls.outcome(days, choice, today)
    val mayGiveWay = plan.mayGiveWay

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.plans_reroll_title))
                Text(planSummary(plan.durationDays, plan.dietType), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plans_reroll_scope), style = MaterialTheme.typography.titleSmall)
                Scope(stringResource(R.string.plans_reroll_whole), stringResource(R.string.plans_reroll_whole_hint), scope == RerollScope.WHOLE) { scope = RerollScope.WHOLE }
                Scope(
                    stringResource(R.string.plans_reroll_from_today),
                    stringResource(
                        when (notFromToday) {
                            FromTodayRefusal.NOT_BEGUN -> R.string.plans_reroll_from_today_not_begun
                            FromTodayRefusal.OVER -> R.string.plans_reroll_from_today_over
                            null -> R.string.plans_reroll_part_hint
                        },
                    ),
                    scope == RerollScope.FROM_TODAY,
                    enabled = notFromToday == null,
                ) { scope = RerollScope.FROM_TODAY }
                if (days.size > 1) {
                    Scope(stringResource(R.string.plans_reroll_range), stringResource(R.string.plans_reroll_part_hint), scope == RerollScope.RANGE) { scope = RerollScope.RANGE }
                    if (scope == RerollScope.RANGE) {
                        ChoiceField(
                            label = stringResource(R.string.plans_reroll_range_from),
                            options = days.map { it.date },
                            selected = from,
                            optionLabel = { formatDayDate(it) },
                            onSelect = { chosen ->
                                from = chosen
                                // The last day is never before the first.
                                if (chosen > to) to = chosen
                            },
                        )
                        ChoiceField(
                            label = stringResource(R.string.plans_reroll_range_to),
                            options = days.map { it.date }.filter { it >= from },
                            selected = to,
                            optionLabel = { formatDayDate(it) },
                            onSelect = { to = it },
                        )
                    }
                }
                Scope(stringResource(R.string.plans_reroll_day), stringResource(R.string.plans_reroll_part_hint), scope == RerollScope.DAY) { scope = RerollScope.DAY }
                if (scope == RerollScope.DAY) {
                    // A day the user skips has nothing to make again: it is named as skipped, and not offered.
                    ChoiceField(
                        label = stringResource(R.string.plans_reroll_day_which),
                        options = days.filter { !it.skipped || it.date == day }.map { it.date },
                        selected = day,
                        optionLabel = { date ->
                            if (days.first { it.date == date }.skipped) stringResource(R.string.plans_reroll_day_skipped, formatDayDate(date))
                            else formatDayDate(date)
                        },
                        onSelect = { day = it },
                    )
                }

                if (past || couldGo.eaten || couldGo.custom) {
                    Text(stringResource(R.string.plans_reroll_also), style = MaterialTheme.typography.titleSmall)
                    if (past) CheckRow(label = stringResource(R.string.plans_reroll_include_past), checked = includePast) { includePast = it }
                    if (couldGo.eaten) CheckRow(label = stringResource(R.string.plans_reroll_replace_eaten), checked = replaceEaten) { replaceEaten = it }
                    if (couldGo.custom) CheckRow(label = stringResource(R.string.plans_reroll_replace_custom), checked = replaceCustom) { replaceCustom = it }
                }
                if (mayGiveWay.isNotEmpty()) {
                    CheckRow(label = stringResource(R.string.plans_reroll_hold_limits, limitNames(mayGiveWay)), checked = holdLimits) { holdLimits = it }
                }

                // Said before anything is asked of the API, and again whenever the choice changes.
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (outcome.days == 0) {
                        Text(
                            stringResource(if (outcome.keptPast > 0) R.string.plans_reroll_nothing_past else R.string.plans_reroll_nothing_kept),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        val meals = if (outcome.replaced == 0) stringResource(R.string.plans_reroll_no_meal)
                        else pluralStringResource(R.plurals.plans_reroll_meals, outcome.replaced, outcome.replaced)
                        Text(
                            stringResource(R.string.plans_reroll_replaced, meals, pluralStringResource(R.plurals.plans_reroll_days, outcome.days, outcome.days)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val kept = listOf(
                        outcome.keptOutside to R.plurals.plans_reroll_kept_outside,
                        outcome.keptPast to R.plurals.plans_reroll_kept_past,
                        outcome.keptSkipped to R.plurals.plans_reroll_kept_skipped,
                        outcome.keptEaten to R.plurals.plans_reroll_kept_eaten,
                        outcome.keptCustom to R.plurals.plans_reroll_kept_custom,
                        outcome.keptLocked to R.plurals.plans_reroll_kept_locked,
                    ).filter { (count, _) -> count > 0 }
                    if (kept.isEmpty()) {
                        Text(stringResource(R.string.plans_reroll_kept_nothing), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(stringResource(R.string.plans_reroll_kept), style = MaterialTheme.typography.bodySmall)
                        kept.forEach { (count, words) ->
                            Text("• ${pluralStringResource(words, count, count)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        Reroll(
                            dayId = days.firstOrNull { it.date == day }?.id.takeIf { scope == RerollScope.DAY },
                            from = when (scope) {
                                RerollScope.RANGE -> from
                                RerollScope.FROM_TODAY -> today
                                else -> null
                            },
                            to = to.takeIf { scope == RerollScope.RANGE },
                            includePast = choice.includePast,
                            replaceEaten = choice.replaceEaten,
                            replaceCustom = choice.replaceCustom,
                            allowRelaxations = emptyList<String>().takeIf { holdLimits && mayGiveWay.isNotEmpty() },
                        ),
                    )
                },
                enabled = outcome.days > 0,
            ) { Text(stringResource(R.string.plans_reroll_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** One choice of how much of the plan is made again, with what it means under it. */
@Composable
private fun Scope(label: String, hint: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
