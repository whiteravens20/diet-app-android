// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.WeekSummary
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatDates
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A profile's week in sum, as the web's card says it: the seven full days before today, or in the
 * diary ([weeks]) a week further back or forth at a time. What the days with an entry or an eaten
 * meal ate on average against their targets, how many came within a tenth of them, what the days
 * drank, and where the weights lead. Read again when [changed] changes and when shown again.
 */
@Composable
fun WeekSummaryCard(
    profileId: String,
    weeks: Boolean = false,
    changed: Any? = null,
    viewModel: WeekSummaryViewModel = hiltViewModel(key = "week-$profileId-$weeks"),
) {
    LaunchedEffect(profileId) { viewModel.open(profileId) }
    LaunchedEffect(changed) { if (changed != null) viewModel.read() }
    LifecycleResumeEffect(viewModel) {
        viewModel.read()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    WeekSummaryContent(state, weeks, onPrevious = viewModel::previous, onNext = viewModel::next)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekSummaryContent(
    state: WeekSummaryViewModel.UiState,
    weeks: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val week = state.week
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (state.last) R.string.week_title else R.string.week_title_of), style = MaterialTheme.typography.titleMedium)
            week?.let { Text(formatDates(it.from.toString(), it.to.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when {
                week == null && state.error != null -> {
                    Text(stringResource(R.string.week_read_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Text(errorMessage(state.error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                week == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> WeekLines(week, state.unit)
            }
            if (weeks) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPrevious) { Text(stringResource(R.string.week_previous)) }
                    OutlinedButton(onClick = onNext, enabled = !state.last) { Text(stringResource(R.string.week_next)) }
                }
            }
        }
    }
}

@Composable
private fun WeekLines(week: WeekSummary, unit: WeightUnit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val eaten = week.eaten
    val target = week.target
    if (eaten != null && target != null) {
        Text(
            stringResource(R.string.week_eaten, eaten.calories.roundToInt(), target.calories.roundToInt()),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(pluralStringResource(R.plurals.week_within, week.counted, week.withinTarget, week.counted), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(
                R.string.week_macros,
                eaten.protein.roundToInt(),
                target.protein.roundToInt(),
                eaten.fat.roundToInt(),
                target.fat.roundToInt(),
                eaten.carbs.roundToInt(),
                target.carbs.roundToInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    } else {
        Text(stringResource(R.string.week_nothing), style = MaterialTheme.typography.bodyMedium, color = muted)
    }
    if (week.empty > 0 && week.counted > 0) {
        Text(pluralStringResource(R.plurals.week_empty, week.empty, week.empty), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    // Nothing for a profile that does not track water, or a week that holds none.
    val water = week.water
    water?.averageMl?.let { average ->
        val aim = water.target
        Text(
            if (aim == null) stringResource(R.string.week_water_no_target, liters(average))
            else pluralStringResource(R.plurals.week_water, water.counted, liters(average), liters(aim), water.reached, water.counted),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    val trend = week.weight
    val pace = trend.kgPerWeek
    if (pace == null) {
        Text(stringResource(R.string.week_trend_none), style = MaterialTheme.typography.bodySmall, color = muted)
    } else {
        val sign = if (pace > 0) "+" else if (pace < 0) "\u2212" else ""
        val first = stringResource(R.string.week_trend, sign + weight(abs(pace), unit))
        val aim = trend.targetKg
        val then = when {
            aim == null -> null
            trend.reachesTargetOn != null -> stringResource(R.string.week_reaches, weight(aim, unit), formatDate(trend.reachesTargetOn.toString()))
            else -> stringResource(R.string.week_leads_away, weight(aim, unit))
        }
        Text(listOfNotNull(first, then).joinToString(" "), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Millilitres in litres, kept with their unit: `1,6 l`. */
@Composable
private fun liters(ml: Int): String = held("${formatNumber(ml / 1000.0, 2)} ${stringResource(R.string.unit_liter)}")

/** Kilograms in the account's [unit], to a tenth, kept with the unit: `0,4 kg`. */
@Composable
private fun weight(kg: Double, unit: WeightUnit): String = held("${formatNumber(unit.shown(kg), 1)}\u00A0${unit.code}")
