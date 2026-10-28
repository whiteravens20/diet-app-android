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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Reroll
import net.whiteravens.dietapp.domain.RerollCouldGo
import net.whiteravens.dietapp.domain.RerollPreview
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import java.time.LocalDate

/** How much of a plan is made again: all of it, what is left of it from today, the days from one to another, or one day. */
enum class RerollScope { WHOLE, FROM_TODAY, RANGE, DAY }

/** What the window a plan is made again in opens on: how much of the plan, which day for one day, and whether every limit is to hold. */
data class RerollStart(val scope: RerollScope, val day: String? = null, val holdLimits: Boolean = false)

/**
 * The window a plan is made again in, as the web's: how much of it, what of it may be replaced that
 * is otherwise left alone, whether every limit holds this time, and, before the plan is made again,
 * what would be replaced and what kept, as the API reads the choice.
 */
@Composable
internal fun RerollDialog(
    plan: MealPlan,
    start: RerollStart,
    onConfirm: (Reroll) -> Unit,
    onDismiss: () -> Unit,
    viewModel: RerollViewModel = hiltViewModel(key = "reroll-${plan.id}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(plan.id) { viewModel.open() }
    // The user's own day, as the API is told it with the request.
    val today = LocalDate.now().toString()
    val days = plan.days
    val first = days.first().date
    val last = days.last().date

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

    // What the API is asked what a re-roll would do: the days chosen, and what of them may go as well.
    val asked = Reroll(
        dayId = days.firstOrNull { it.date == day }?.id.takeIf { scope == RerollScope.DAY },
        from = when (scope) {
            RerollScope.RANGE -> from
            RerollScope.FROM_TODAY -> today
            else -> null
        },
        to = to.takeIf { scope == RerollScope.RANGE },
        includePast = includePast,
        replaceEaten = replaceEaten,
        replaceCustom = replaceCustom,
    )
    LaunchedEffect(asked, plan.revision) { viewModel.ask(plan.id, plan.revision, asked) }
    val preview = state.preview
    // What the days chosen hold that may go as well: a tick is offered, and sent, only where it lets something go.
    val couldGo = preview?.couldGo ?: RerollCouldGo(past = false, eaten = false, custom = false)
    val notFromToday = preview?.fromToday
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
                            NOT_BEGUN -> R.string.plans_reroll_from_today_not_begun
                            OVER -> R.string.plans_reroll_from_today_over
                            else -> R.string.plans_reroll_part_hint
                        },
                    ),
                    scope == RerollScope.FROM_TODAY,
                    // Not on offer until the API has said whether it is one.
                    enabled = preview != null && notFromToday == null,
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

                if (couldGo.past || couldGo.eaten || couldGo.custom) {
                    Text(stringResource(R.string.plans_reroll_also), style = MaterialTheme.typography.titleSmall)
                    if (couldGo.past) CheckRow(label = stringResource(R.string.plans_reroll_include_past), checked = includePast) { includePast = it }
                    if (couldGo.eaten) CheckRow(label = stringResource(R.string.plans_reroll_replace_eaten), checked = replaceEaten) { replaceEaten = it }
                    if (couldGo.custom) CheckRow(label = stringResource(R.string.plans_reroll_replace_custom), checked = replaceCustom) { replaceCustom = it }
                }
                if (mayGiveWay.isNotEmpty()) {
                    CheckRow(label = stringResource(R.string.plans_reroll_hold_limits, limitNames(mayGiveWay)), checked = holdLimits) { holdLimits = it }
                }

                // Said before the plan is made again, and again whenever the choice changes: the API works it out, this says it.
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.error?.let { error ->
                        ErrorBanner("${stringResource(R.string.plans_reroll_preview_failed)} ${errorMessage(error)}")
                        TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.common_retry)) }
                    }
                    if (preview != null) {
                        // An answer to an earlier choice is shown faint while the newer one is on its way.
                        Column(Modifier.alpha(if (state.current) 1f else 0.6f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Outcome(preview)
                        }
                    } else if (state.error == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        asked.copy(
                            includePast = couldGo.past && includePast,
                            replaceEaten = couldGo.eaten && replaceEaten,
                            replaceCustom = couldGo.custom && replaceCustom,
                            allowRelaxations = emptyList<String>().takeIf { holdLimits && mayGiveWay.isNotEmpty() },
                        ),
                    )
                },
                // Only for the choice on the screen, as the API answered it, and where it changes something.
                enabled = state.current && (preview?.days ?: 0) > 0,
            ) { Text(stringResource(R.string.plans_reroll_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** What the API said a re-roll would replace, and what it would keep as it is and why. */
@Composable
private fun Outcome(preview: RerollPreview) {
    if (preview.days == 0) {
        Text(
            stringResource(if (preview.kept.past > 0) R.string.plans_reroll_nothing_past else R.string.plans_reroll_nothing_kept),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        val meals = if (preview.replaced == 0) stringResource(R.string.plans_reroll_no_meal)
        else pluralStringResource(R.plurals.plans_reroll_meals, preview.replaced, preview.replaced)
        Text(
            stringResource(R.string.plans_reroll_replaced, meals, pluralStringResource(R.plurals.plans_reroll_days, preview.days, preview.days)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    val kept = listOf(
        preview.kept.outside to R.plurals.plans_reroll_kept_outside,
        preview.kept.past to R.plurals.plans_reroll_kept_past,
        preview.kept.skipped to R.plurals.plans_reroll_kept_skipped,
        preview.kept.eaten to R.plurals.plans_reroll_kept_eaten,
        preview.kept.custom to R.plurals.plans_reroll_kept_custom,
        preview.kept.locked to R.plurals.plans_reroll_kept_locked,
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

/** Why the days from today on are no choice of their own: the plan has not begun, so that is all of it, or it is over. */
private const val NOT_BEGUN = "not_begun"
private const val OVER = "over"

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
