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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.DaySettingsPreview
import net.whiteravens.dietapp.domain.DaySettingsWeek
import net.whiteravens.dietapp.domain.MealFate
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.PlanWeek
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatList
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.numberProblem
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The window in which the settings of a day of a running plan are changed, as the web's: the day's
 * kind, its number of meals, a target of its own, a limit on cooking time and whether it uses up
 * what the pantry has to use up. What a change would do is said before anything is saved, as the
 * API works it out, with what it does to the average of the day's [week]: a change that leaves the
 * week off the plan's target is saved alone or with the week evened out, and a week that stands off
 * it can be evened out as it is. The plan with the settings saved goes to [onSaved]; a refusal that
 * may come of a plan changed under the window goes to [onStale].
 */
@Composable
fun DaySettingsDialog(
    planId: String,
    day: PlanDay,
    week: PlanWeek?,
    onSaved: (MealPlan) -> Unit,
    onStale: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: DaySettingsViewModel = hiltViewModel(key = "settings-${day.id}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(day.id) { viewModel.open(planId, day, week) }
    // The window's model outlives it: what the model holds now is read, so that a save of an
    // earlier opening on the same day is not handed back before the state on the screen catches up.
    LaunchedEffect(state.saved) { viewModel.state.value.saved?.let(onSaved) }
    LaunchedEffect(state.stale) { if (viewModel.state.value.stale) onStale() }
    val skipped = state.kind == DaySettingsViewModel.SKIP

    ScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.plans_settings_title), style = MaterialTheme.typography.titleLarge)
                    Text(formatDayDate(day.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField(
                    label = stringResource(R.string.plans_settings_kind),
                    options = DaySettingsViewModel.KINDS,
                    selected = state.kind,
                    optionLabel = { kindLabel(it) },
                    onSelect = viewModel::setKind,
                    hint = stringResource(R.string.plans_settings_kind_hint),
                )
                if (!skipped) {
                    ChoiceField(
                        label = stringResource(R.string.plans_settings_meal_count),
                        options = DaySettingsViewModel.MEAL_COUNTS,
                        selected = state.mealCount,
                        optionLabel = { pluralStringResource(R.plurals.plans_settings_meal_count_option, it, it) },
                        onSelect = viewModel::setMealCount,
                    )
                    FormField(
                        value = state.target,
                        onValueChange = viewModel::setTarget,
                        label = stringResource(R.string.plans_settings_own_target),
                        hint = stringResource(R.string.plans_settings_own_target_hint),
                        problem = numberProblem(state.targetNotWhole, state.refused["calorieTarget"]),
                        keyboardType = KeyboardType.Number,
                    )
                    FormField(
                        value = state.cookTime,
                        onValueChange = viewModel::setCookTime,
                        label = stringResource(R.string.plans_settings_cook_time),
                        hint = stringResource(R.string.plans_settings_cook_time_hint),
                        problem = numberProblem(state.cookTimeNotWhole, state.refused["cookTimeBudgetMinutes"]),
                        keyboardType = KeyboardType.Number,
                    )
                    CheckRow(
                        label = stringResource(R.string.plans_settings_use_up),
                        hint = stringResource(R.string.plans_settings_use_up_hint),
                        checked = state.useUpBy,
                        onChange = viewModel::setUseUpBy,
                    )
                }
                // What the change would do, said before it is made and read out as it changes.
                Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val preview = state.preview
                    when {
                        state.settings == null || state.refused.isNotEmpty() -> Note(stringResource(R.string.plans_settings_fix_fields))
                        preview == null && state.previewError != null -> ErrorBanner(errorMessage(state.previewError!!))
                        preview == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        else -> Column(Modifier.alpha(if (state.current) 1f else 0.6f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Outcome(preview, skipped, state.week?.takeIf { it.uneven }, state.unit)
                            if (!preview.unchanged) CookingNote(preview.cooking, state.only, onOnly = viewModel::setOnly)
                        }
                    }
                }
                state.inTheWay?.let { needed -> InTheWayBox(needed, busy = state.saving, onLetGiveWay = viewModel::letGiveWay, onCancel = onDismiss) }
                state.error?.let { ErrorBanner(errorMessage(it)) }
            }
            val cancel: @Composable (Modifier) -> Unit = { modifier ->
                TextButton(onClick = onDismiss, modifier = modifier) { Text(stringResource(R.string.common_cancel)) }
            }
            val save: @Composable (Modifier) -> Unit = { modifier ->
                Button(
                    onClick = { viewModel.save() },
                    modifier = modifier,
                    enabled = !state.saving && state.current && state.preview?.unchanged == false,
                ) {
                    Text(stringResource(if (state.twoWays) R.string.plans_settings_save_day else R.string.plans_settings_save))
                }
            }
            if (state.evenAlone || state.twoWays) {
                // Three buttons do not fit a phone's width in a row: they stand one under another, saving on top.
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    save(Modifier.fillMaxWidth())
                    if (state.evenAlone) {
                        OutlinedButton(onClick = viewModel::evenWeek, modifier = Modifier.fillMaxWidth(), enabled = !state.saving) {
                            Text(stringResource(R.string.plans_settings_even_week))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.save(evenWeek = true) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.saving,
                        ) {
                            Text(stringResource(R.string.plans_settings_save_and_even))
                        }
                    }
                    cancel(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cancel(Modifier)
                    save(Modifier)
                }
            }
        }
    }
}

/** What a change of a day's settings would do, line by line; [weekOff], the day's week where it stands off the plan's target. */
@Composable
private fun Outcome(preview: DaySettingsPreview, skipped: Boolean, weekOff: PlanWeek?, unit: WeightUnit) {
    if (preview.unchanged) {
        Note(stringResource(R.string.plans_settings_unchanged))
        val average = weekOff?.average
        if (weekOff != null && average != null) {
            Note(stringResource(R.string.plans_settings_even_week_note, formatNumber(average.roundToInt()), formatNumber(weekOff.target.roundToInt())))
        }
        return
    }
    if (skipped) {
        Note(stringResource(R.string.plans_settings_skip_note), MaterialTheme.colorScheme.onSurface)
    } else {
        val now = formatNumber(preview.targetNow.roundToInt())
        val then = formatNumber(preview.targetThen.roundToInt())
        Text(
            if (now == then) stringResource(R.string.plans_settings_target_same, now) else stringResource(R.string.plans_settings_target_change, now, then),
            style = MaterialTheme.typography.titleSmall,
        )
        Note(
            stringResource(
                R.string.plans_settings_target_macros,
                formatNumber(preview.macrosThen.protein.roundToInt()),
                formatNumber(preview.macrosThen.fat.roundToInt()),
                formatNumber(preview.macrosThen.carbs.roundToInt()),
            ),
        )
        if (preview.belowSafetyFloor) Note(stringResource(R.string.plans_settings_below_floor), MaterialTheme.colorScheme.error)
    }
    preview.meals.forEach { meal -> Note("${mealTypeLabel(meal.slot)}: ${fateText(meal)}", MaterialTheme.colorScheme.onSurface) }
    if (preview.added.isNotEmpty()) {
        Note(stringResource(R.string.plans_settings_added, formatList(preview.added.map { mealTypeLabel(it) })), MaterialTheme.colorScheme.onSurface)
    }
    if (preview.added.isNotEmpty() || preview.meals.any { it.fate == "redrawn" }) Note(stringResource(R.string.plans_settings_drawn_note))
    val kcal = preview.dayCalories
    if (kcal != null && !skipped) {
        val shown = formatNumber(kcal.roundToInt())
        if (preview.fits == true) Note(stringResource(R.string.plans_settings_day_calories, shown))
        else Note(stringResource(R.string.plans_settings_day_calories_off, shown), MaterialTheme.colorScheme.tertiary)
    }
    if (preview.listOutOfDate) Note(stringResource(R.string.plans_settings_list_out_of_date))
    preview.week?.let { WeekOutcome(it, unit) }
    if (preview.week?.unevenAlone != true) Note(stringResource(R.string.plans_settings_other_days))
}

/** What the change does to the week of the day: its average against the plan's target and the pace that comes to, and what evening the week out would do. */
@Composable
private fun WeekOutcome(week: DaySettingsWeek, unit: WeightUnit) {
    val now = formatNumber(week.averageNow.roundToInt())
    val alone = formatNumber(week.averageAlone.roundToInt())
    val target = formatNumber(week.target.roundToInt())
    val average = if (now == alone) stringResource(R.string.plans_settings_week_average, now, target)
    else stringResource(R.string.plans_settings_week_average_change, now, alone, target)
    val pace = week.paceAlone
    val weight = "${formatNumber(abs(unit.shown(pace)), 2)} ${unit.code}"
    val paced = when {
        abs(pace) < 0.05 -> stringResource(R.string.plans_settings_week_pace_keep)
        pace > 0 -> stringResource(R.string.plans_settings_week_pace_loss, weight)
        else -> stringResource(R.string.plans_settings_week_pace_gain, weight)
    }
    Note("$average $paced", MaterialTheme.colorScheme.onSurface)
    if (week.unevenAlone) {
        Note(
            pluralStringResource(
                R.plurals.plans_settings_week_even_note,
                week.otherDays,
                formatNumber(week.evenedTarget.roundToInt()),
                week.otherDays,
                formatNumber(week.averageEvened.roundToInt()),
            ),
        )
    }
}

/** What becomes of a meal, in words: its fate, and why where there is more to it than the day's new target. */
@Composable
private fun fateText(meal: MealFate): String = stringResource(
    when (meal.fate) {
        "stays" -> when (meal.because) {
            "eaten" -> R.string.plans_fate_stays_eaten
            "own" -> R.string.plans_fate_stays_own
            else -> R.string.plans_fate_stays
        }
        "resized" -> R.string.plans_fate_resized
        "redrawn" -> if (meal.because == "use_up") R.string.plans_fate_redrawn_use_up else R.string.plans_fate_redrawn_cook_time
        else -> if (meal.because == "skipped") R.string.plans_fate_removed_skipped else R.string.plans_fate_removed_fewer
    },
)

/** The kind of a day as the window names it: `normal` | `training` | `rest` | `skip`. */
@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (kind) {
        "training" -> R.string.plans_day_kind_training
        "rest" -> R.string.plans_day_kind_rest
        DaySettingsViewModel.SKIP -> R.string.plans_day_kind_skip
        else -> R.string.plans_day_kind_normal
    },
)


@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
