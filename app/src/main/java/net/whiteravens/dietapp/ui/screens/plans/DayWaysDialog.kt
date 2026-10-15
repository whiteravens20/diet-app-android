// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.DayWay
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.fallbackText
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AiMark
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import kotlin.math.roundToInt

/**
 * The window that offers other ways to fill a day of a plan, as the web's: the day as it is to hold
 * them against, up to three ways with their meals and what they come to, a model's order and its
 * sentence on each where the user asks for it, and the way taken. The plan with the way taken goes
 * to [onTaken]; a refusal that may come of a plan changed under the offer goes to [onStale].
 */
@Composable
fun DayWaysDialog(
    planId: String,
    day: PlanDay,
    onTaken: (MealPlan) -> Unit,
    onStale: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: DayWaysViewModel = hiltViewModel(key = "ways-${day.id}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(day.id) { viewModel.open(planId, day.id) }
    LaunchedEffect(state.taken) { state.taken?.let(onTaken) }
    LaunchedEffect(state.stale) { if (state.stale) onStale() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.plans_ways_title, formatDayDate(day.date)),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
                Note(stringResource(R.string.plans_ways_intro))
                if (state.loading) {
                    Note(stringResource(R.string.plans_ways_loading))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.error?.let { ErrorBanner(errorMessage(it)) }
                state.ways?.let { ways ->
                    val target = formatNumber(ways.calorieTarget.roundToInt())
                    Note(
                        stringResource(
                            R.string.plans_ways_now,
                            formatNumber(ways.now.calories.roundToInt()),
                            target,
                            if (ways.nowWasted == 0) stringResource(R.string.plans_ways_now_none)
                            else pluralStringResource(R.plurals.plans_ways_now_packs, ways.nowWasted, ways.nowWasted),
                        ),
                    )
                    if (ways.ways.isEmpty()) Text(stringResource(R.string.plans_ways_none), style = MaterialTheme.typography.bodyMedium)
                    ways.ways.forEachIndexed { index, way -> Way(index, way, target, busy = state.busy) { viewModel.take(way.offer) } }

                    // How asking a model went, said where the ways are and read out.
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        when {
                            ways.weighed -> Note(stringResource(R.string.weigh_weighed))
                            ways.fallbackReason != null -> fallbackText(ways.fallbackReason)?.let { Note(it, MaterialTheme.colorScheme.tertiary) }
                        }
                    }
                    if (ways.ways.isNotEmpty()) {
                        TextButton(onClick = viewModel::weigh, enabled = state.aiOn == true && !state.busy) {
                            Text(stringResource(if (state.weighing) R.string.weigh_asking else R.string.weigh_ask))
                        }
                        Note(stringResource(if (state.aiOn == false) R.string.ai_off_note else R.string.weigh_tooltip))
                    }
                }
            }
        }
    }
}

/** One other way to fill the day: its meals, those that stay marked, what the day would hold, the packs the plan would leave, and a model's sentence on it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Way(index: Int, way: DayWay, target: String, busy: Boolean, onTake: () -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plans_ways_way, (index + 1).toString()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Button(onClick = onTake, enabled = !busy) { Text(stringResource(R.string.plans_ways_take)) }
            }
            way.reason?.let { AiMark(stringResource(R.string.ai_mark_weighed, it), spelledOut = true) }
            way.meals.forEach { meal ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${mealTypeLabel(meal.slot)} · ${meal.title}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.plans_ways_meal_energy, formatNumber(meal.calories.roundToInt())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (meal.stays) LabelChip(stringResource(R.string.plans_ways_stays))
                }
            }
            Note(
                stringResource(
                    R.string.plans_ways_day_line,
                    formatNumber(way.nutrition.calories.roundToInt()),
                    target,
                    formatNumber(way.nutrition.protein.roundToInt()),
                    formatNumber(way.nutrition.fat.roundToInt()),
                    formatNumber(way.nutrition.carbs.roundToInt()),
                ),
            )
            Note(
                if (way.wasted == 0) stringResource(R.string.plans_ways_packs_none)
                else pluralStringResource(R.plurals.plans_ways_packs, way.wasted, way.wasted),
            )
        }
    }
}

@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
