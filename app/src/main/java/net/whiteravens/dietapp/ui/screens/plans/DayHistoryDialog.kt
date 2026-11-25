// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.DayEvent
import net.whiteravens.dietapp.domain.DayEventMeal
import net.whiteravens.dietapp.domain.DayEventMealState
import net.whiteravens.dietapp.domain.DayHistory
import net.whiteravens.dietapp.domain.PlanEffects
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatDayMonth
import net.whiteravens.dietapp.ui.common.formatDates
import net.whiteravens.dietapp.ui.common.formatList
import net.whiteravens.dietapp.ui.common.formatMoment
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.components.Text
import java.time.Instant

/**
 * The history of a day of a plan ("Historia dnia"), opened from the plan's day, from the diary of its date and from
 * the card after a change, as the web's window shows it: what holds the day (the user's own choice of its portions, and
 * what no sizing moves), what can be done (the last change taken back where the plan has not changed since, the day
 * sized again where that would move a meal ([resizes]), its portions kept as they are on a day from [today] on), and
 * the changes of the day, the latest first, each with its time, its cause and the meals it moved. The diary of the day,
 * or the day in the plan ([fromDiary]), is a link away ([onOpenOther], null where there is none). Every change made
 * here tells [onChanged], so that the screen under it reads the plan again.
 */
@Composable
fun DayHistoryDialog(
    planId: String,
    dayId: String,
    date: String,
    resizes: Boolean,
    today: String,
    fromDiary: Boolean,
    onOpenOther: (() -> Unit)?,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: DayHistoryViewModel = hiltViewModel(key = "history-$dayId"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(dayId) { viewModel.open(planId, dayId) }
    LaunchedEffect(state.changes) { if (state.changes > 0) onChanged() }

    ScreenDialog(onDismiss) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
                    Hint(formatDayDate(date))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
            if (state.loading) {
                Hint(stringResource(R.string.history_loading))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.readError?.let { ErrorBanner(stringResource(R.string.history_failed) + " " + errorMessage(it)) }
            state.history?.let { history ->
                Holds(history)
                Actions(
                    history = history,
                    resizes = resizes,
                    keepable = date >= today && history.kept?.first != "kept",
                    busy = state.acting,
                    failed = state.failed,
                    onUndo = viewModel::undo,
                    onResize = viewModel::resize,
                    onKeep = viewModel::keep,
                )
                Text(
                    stringResource(R.string.history_changes_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (history.events.isEmpty()) Hint(stringResource(R.string.history_no_changes))
                history.events.forEachIndexed { index, event ->
                    if (index > 0) HorizontalDivider()
                    Event(event)
                }
            }
            onOpenOther?.let { open ->
                TextButton(onClick = open) {
                    Text(stringResource(if (fromDiary) R.string.history_open_in_plan else R.string.plans_open_diary))
                }
            }
        }
    }
}

/** What holds the day: the user's choice of its portions, or that every change fits it; and what no sizing moves. */
@Composable
private fun Holds(history: DayHistory) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.history_holds_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        val kept = history.kept
        if (kept == null) {
            Text(stringResource(R.string.history_adjusts), style = MaterialTheme.typography.bodyMedium)
        } else {
            val at = formatMoment(Instant.parse(kept.second))
            Text(
                stringResource(
                    when (kept.first) {
                        "undo" -> R.string.history_kept_undo
                        "spread" -> R.string.history_kept_spread
                        else -> R.string.history_kept_kept
                    },
                    at,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Hint(stringResource(R.string.history_kept_note))
        }
        val holds = history.holds
        val lines = listOfNotNull(
            holds.ownMeals.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.history_own_meals, formatList(it)) },
            holds.eatenMeals.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.history_eaten_meals, formatList(it)) },
            holds.leftovers.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.history_leftovers, formatList(it)) },
            holds.rests.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.history_rests, formatList(it)) },
            holds.diaryEntries.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.history_diary_entries, it, it) },
        )
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (lines.isNotEmpty()) Hint(stringResource(R.string.history_holds_note))
    }
}

/** The last change taken back, the day sized again and its portions kept, each with what it does said under it. */
@Composable
private fun Actions(
    history: DayHistory,
    resizes: Boolean,
    keepable: Boolean,
    busy: Boolean,
    failed: Boolean,
    onUndo: () -> Unit,
    onResize: () -> Unit,
    onKeep: () -> Unit,
) {
    val undo = history.undo
    if (undo == null && !resizes && !keepable) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (undo != null) {
            OutlinedButton(onClick = onUndo, enabled = !busy) { Text(stringResource(R.string.history_undo)) }
            val also = undo.alsoDays.map { formatDayMonth(it) }
            Hint(
                listOfNotNull(
                    stringResource(R.string.history_undo_portions).takeIf { !undo.swap },
                    also.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.history_undo_also, it.size, it.size, formatList(it)) },
                ).joinToString(" "),
            )
        }
        if (resizes) {
            OutlinedButton(onClick = onResize, enabled = !busy) { Text(stringResource(R.string.plans_resize_day)) }
            Hint(
                stringResource(
                    if (history.holds.diaryEntries > 0) R.string.plans_resize_day_note else R.string.plans_resize_day_note_plain,
                ),
            )
        }
        if (keepable) {
            OutlinedButton(onClick = onKeep, enabled = !busy) { Text(stringResource(R.string.history_keep)) }
            Hint(stringResource(R.string.history_keep_note))
        }
        if (failed) Hint(stringResource(R.string.history_action_failed), MaterialTheme.colorScheme.error)
    }
}

/** A change of the day: when, what made it, which days it was of where it was not the user's, the meals it moved. */
@Composable
private fun Event(event: DayEvent) {
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Hint(formatMoment(Instant.parse(event.at)))
        Text(causeLabel(event.cause), style = MaterialTheme.typography.titleSmall)
        if (!event.own) {
            Hint(
                if (event.from.isEmpty()) stringResource(R.string.history_from_list)
                else pluralStringResource(R.plurals.history_from_days, event.from.size, event.from.size, formatList(event.from.map { formatDayMonth(it) })),
            )
        }
        event.meals.forEach { Text(mealLine(it), style = MaterialTheme.typography.bodySmall) }
        event.target?.let { (before, after) ->
            Text(stringResource(R.string.history_target, formatNumber(before), formatNumber(after)), style = MaterialTheme.typography.bodySmall)
        }
        if (event.choice?.first != null && event.choice.second == null) {
            Text(stringResource(R.string.history_choice_ended), style = MaterialTheme.typography.bodySmall)
        }
        effectLines(event.effects).forEach { Hint(it, MaterialTheme.colorScheme.tertiary) }
    }
}

/** What a change did to the lists and the freezer, in words. */
@Composable
internal fun effectLines(effects: PlanEffects): List<String> = buildList {
    effects.lists.forEach { add(stringResource(R.string.plans_effect_list, formatDates(it.fromDate, it.toDate))) }
    if (effects.thaw.isNotEmpty()) add(stringResource(R.string.plans_effect_thaw) + " " + formatList(effects.thaw.map { formatDayMonth(it) }))
    if (effects.freeze.isNotEmpty()) add(stringResource(R.string.plans_effect_freeze) + " " + formatList(effects.freeze.map { formatDayMonth(it) }))
}

/** A meal a change moved, by what happened to it: marked, put in, taken off, given another dish or another portion. */
@Composable
private fun mealLine(meal: DayEventMeal): String {
    val type = mealTypeLabel(meal.mealType)
    val before = meal.before
    val after = meal.after
    return when {
        before != null && after != null && before.eaten != after.eaten ->
            stringResource(if (after.eaten) R.string.history_meal_eaten else R.string.history_meal_not_eaten, type, nameOf(after))
        before != null && after != null && before.name != after.name ->
            stringResource(R.string.history_meal_replaced, type, nameOf(before), nameOf(after), formatNumber(after.calories))
        before != null && after != null ->
            stringResource(R.string.history_meal_changed, type, nameOf(after), formatNumber(before.calories), formatNumber(after.calories))
        after != null -> stringResource(R.string.history_meal_added, type, nameOf(after), formatNumber(after.calories))
        else -> stringResource(R.string.history_meal_removed, type, nameOf(before))
    }
}

@Composable
private fun nameOf(state: DayEventMealState?): String = state?.name ?: stringResource(R.string.history_unnamed)

/** What made a change, as the history names it; a cause of a newer server than the app, as the server names it. */
@Composable
private fun causeLabel(cause: String): String = causeText(cause)?.let { stringResource(it) } ?: cause

private fun causeText(cause: String): Int? =
    when (cause) {
        "swap" -> R.string.history_cause_swap
        "swap-undo" -> R.string.history_cause_swap_undo
        "ingredient-swap" -> R.string.history_cause_ingredient_swap
        "own-meal" -> R.string.history_cause_own_meal
        "meal-removed" -> R.string.history_cause_meal_removed
        "eaten" -> R.string.history_cause_eaten
        "not-eaten" -> R.string.history_cause_not_eaten
        "diary" -> R.string.history_cause_diary
        "eaten-elsewhere" -> R.string.history_cause_eaten_elsewhere
        "resize" -> R.string.history_cause_resize
        "undo" -> R.string.history_cause_undo
        "spread" -> R.string.history_cause_spread
        "day-settings" -> R.string.history_cause_day_settings
        "day-variant" -> R.string.history_cause_day_variant
        "leftover" -> R.string.history_cause_leftover
        "leftover-undo" -> R.string.history_cause_leftover_undo
        "favorite-set" -> R.string.history_cause_favorite_set
        "reroll" -> R.string.history_cause_reroll
        "retarget" -> R.string.history_cause_retarget
        "bought-by-weight" -> R.string.history_cause_bought_by_weight
        "kept" -> R.string.history_cause_kept
        else -> null
    }
