// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.DiaryDay
import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.DiaryPlanMeal
import net.whiteravens.dietapp.domain.FIRST_DIARY_DAY
import net.whiteravens.dietapp.domain.LAST_DIARY_DAY
import net.whiteravens.dietapp.domain.MOST_DIARY_ENTRIES
import net.whiteravens.dietapp.ui.common.WhenShownAgain
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.DateField
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineAsOf
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.WaitingMark
import net.whiteravens.dietapp.ui.screens.plans.RebalanceCard
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The diary, as the web's page keeps it: of a profile and a day, what was eaten against the day's
 * target, the meals of the day with what its plan has for them and what was written for them, and
 * another day to copy. Each entry is changed and taken out where it is shown. The profiles are
 * [onProfiles]; the plan that holds the day opens on [onOpenPlan].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryScreen(
    onBack: () -> Unit,
    onProfiles: () -> Unit,
    onOpenPlan: (planId: String, day: String) -> Unit,
    viewModel: DiaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read again on being shown again: a plan's page and a recipe change what a day shows.
    WhenShownAgain(viewModel::shownAgain)

    state.form?.let { form -> AddEntryDialog(form, state, viewModel) }
    state.edit?.let { edit -> EditEntryDialog(edit, state, viewModel) }

    // The card of a rebalance stands over the end of the list: the list ends that much higher, so
    // that nothing of it stays under the card.
    var cardHeight by remember { mutableIntStateOf(0) }
    val underCard = if (state.rebalance != null) with(LocalDensity.current) { cardHeight.toDp() + 16.dp } else 0.dp
    Box(Modifier.fillMaxSize()) {
        PullToRead(state.pulled, viewModel::pull) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + underCard),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ScreenHeader(title = stringResource(R.string.diary_title), subtitle = stringResource(R.string.diary_subhead), onBack = onBack)
                }
                if (state.offline) item { OfflineAsOf(state.readAt, says = R.string.diary_offline) }

                when {
                    state.loadingProfiles -> item { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }

                    state.profilesError != null -> item {
                        Column {
                            ErrorBanner(errorMessage(state.profilesError!!))
                            Button(onClick = viewModel::loadProfiles, modifier = Modifier.padding(top = 12.dp)) {
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                    }

                    state.profiles.isEmpty() -> item {
                        EmptyState(
                            icon = AppIcons.Diary,
                            title = stringResource(R.string.diary_title),
                            description = stringResource(R.string.diary_no_profile),
                            cta = { Button(onClick = onProfiles) { Text(stringResource(R.string.diary_no_profile_cta)) } },
                        )
                    }

                    else -> {
                        // The profiles to choose among, only where there are two or more.
                        if (state.profiles.size > 1) {
                            item {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.profiles.forEach { profile ->
                                        FilterChip(
                                            selected = profile.id == state.profileId,
                                            onClick = { viewModel.pickProfile(profile.id) },
                                            label = { Text(profile.name) },
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "day") { DayPicker(state.date, onPick = viewModel::pickDay) }
                        state.failed?.let { (what, error) ->
                            item {
                                val lead = stringResource(
                                    when (what) {
                                        DiaryViewModel.Failure.REMOVE -> R.string.diary_err_remove
                                        DiaryViewModel.Failure.EATEN -> R.string.diary_err_eaten
                                        DiaryViewModel.Failure.REBALANCE -> R.string.diary_err_rebalance
                                        DiaryViewModel.Failure.COPY -> R.string.diary_err_copy
                                    },
                                )
                                ErrorBanner("$lead ${errorMessage(error)}", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                        }
                        when {
                            state.loading -> item { Skeleton(Modifier.fillMaxWidth().height(220.dp)) }

                            state.error != null -> item {
                                Column {
                                    ErrorBanner(errorMessage(state.error!!))
                                    Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                                        Text(stringResource(R.string.common_retry))
                                    }
                                }
                            }

                            else -> day(state, viewModel, onOpenPlan)
                        }
                    }
                }
            }
        }
        state.rebalance?.let { rebalance ->
            RebalanceCard(
                rebalance = rebalance,
                notice = null,
                busy = state.changing,
                onUndo = viewModel::undoRebalance,
                onRest = viewModel::spread,
                onDismiss = viewModel::dismissRebalance,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).onSizeChanged { cardHeight = it.height },
            )
        }
    }
}

/** A day: what was eaten, how something is added or why it is not, its meals, and another day to copy. */
private fun LazyListScope.day(
    state: DiaryViewModel.UiState,
    viewModel: DiaryViewModel,
    onOpenPlan: (planId: String, day: String) -> Unit,
) {
    item(key = "summary") {
        val day = state.day
        if (day != null) {
            DaySummary(state, day, onResize = viewModel::resize, onOpenPlan = onOpenPlan)
        } else if (state.offline) {
            // Without a network, a day not read on this phone before: what is written waits, and is said to.
            AppCard {
                Text(
                    stringResource(R.string.diary_not_on_the_phone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item(key = "add") {
        when {
            state.ahead -> Note(stringResource(R.string.diary_ahead_note))
            state.tooOld -> Note(stringResource(R.string.diary_too_old_note))
            state.full -> Note(stringResource(R.string.diary_full_note, formatNumber(MOST_DIARY_ENTRIES)))
            else -> Button(onClick = viewModel::openWindowNow) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.diary_add_title))
            }
        }
    }
    if (state.day != null && state.slots.isEmpty() && !state.ahead && !state.tooOld) {
        item(key = "empty") {
            EmptyState(icon = AppIcons.Diary, title = stringResource(R.string.diary_empty_title), description = stringResource(R.string.diary_empty_text))
        }
    }
    items(state.slots, key = { "meal-$it" }) { slot ->
        MealSection(
            slot = slot,
            meals = state.planMeals.filter { it.slot == slot },
            entries = state.entries.filter { it.slot == slot },
            state = state,
            onAdd = { viewModel.openWindow(slot) },
            onEaten = viewModel::setEaten,
            onEdit = viewModel::openEdit,
            onRemove = viewModel::remove,
        )
    }
    if (!state.ahead && !state.tooOld) {
        item(key = "copy") {
            CopyDay(state, onChoose = viewModel::chooseCopyFrom, onCopy = viewModel::copy)
        }
    }
}

/** The day shown, the days on either side of it, and today. */
@Composable
private fun DayPicker(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPick(date.minusDays(1)) }, enabled = date > FIRST_DIARY_DAY) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.diary_previous_day))
            }
            DateField(
                label = stringResource(R.string.diary_day_label),
                day = date,
                onPick = onPick,
                modifier = Modifier.weight(1f),
                years = FIRST_DIARY_DAY.year..LAST_DIARY_DAY.year,
            )
            IconButton(onClick = { onPick(date.plusDays(1)) }, enabled = date < LAST_DIARY_DAY) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.diary_next_day))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDayDate(date.toString()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 12.dp))
            TextButton(onClick = { onPick(today) }, enabled = date != today) { Text(stringResource(R.string.diary_today)) }
        }
    }
}

/**
 * What was eaten of a day against its target, in figures and as a bar, with how many changes that
 * wait to be sent the figures, which are the server's, leave out.
 */
@Composable
internal fun EatenAgainstTarget(day: DiaryDay, leftOut: Int) {
    val share = if (day.target.calories > 0) (day.eaten.calories / day.target.calories).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val line = stringResource(R.string.diary_eaten_line, formatNumber(day.eaten.calories.roundToInt()), formatNumber(day.target.calories.roundToInt()))
        Text(if (day.estimated) "≈ $line" else line, style = MaterialTheme.typography.headlineSmall)
        // The line above says it in figures.
        LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
        Note(
            stringResource(
                R.string.diary_eaten_macros,
                formatNumber(day.eaten.protein.roundToInt()),
                formatNumber(day.target.protein.roundToInt()),
                formatNumber(day.eaten.fat.roundToInt()),
                formatNumber(day.target.fat.roundToInt()),
                formatNumber(day.eaten.carbs.roundToInt()),
                formatNumber(day.target.carbs.roundToInt()),
            ),
        )
        if (leftOut > 0) Note(pluralStringResource(R.plurals.diary_sum_leaves_out, leftOut, leftOut))
    }
}

/** What was eaten against the day's target, whose target it is, and where the plan's day stands with the diary counted. */
@Composable
private fun DaySummary(
    state: DiaryViewModel.UiState,
    day: DiaryDay,
    onResize: () -> Unit,
    onOpenPlan: (planId: String, day: String) -> Unit,
) {
    val plan = day.plan
    // Where nothing is written, the plan's day stands as its own meals put it.
    val written = day.entries.isNotEmpty()
    // A meal from a recipe that is not eaten yet is all a rebalance moves.
    val resizable = plan?.meals?.any { !it.eaten && it.recipeId != null } == true
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.diary_eaten_title), style = MaterialTheme.typography.titleMedium)
            EatenAgainstTarget(day, state.leftOut)
            Note(
                stringResource(
                    when {
                        plan?.skipped == true -> R.string.diary_target_from_skipped
                        day.targetFromPlan -> R.string.diary_target_from_plan
                        else -> R.string.diary_target_from_profile
                    },
                ),
            )
            if (day.estimated) Note(stringResource(R.string.diary_estimated_note))
            if (plan != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                if (!plan.skipped) {
                    val kcal = formatNumber(abs(plan.calorieDelta).roundToInt())
                    when (plan.offTarget) {
                        null -> Note(stringResource(if (written) R.string.diary_plan_on_target else R.string.diary_plan_on_target_plain))
                        else -> {
                            val over = plan.offTarget == "over"
                            LabelChip(
                                stringResource(if (over) R.string.diary_plan_over else R.string.diary_plan_under),
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Note(
                                stringResource(
                                    when {
                                        over && written -> R.string.diary_plan_over_note
                                        over -> R.string.diary_plan_over_plain
                                        written -> R.string.diary_plan_under_note
                                        else -> R.string.diary_plan_under_plain
                                    },
                                    kcal,
                                ),
                            )
                            when {
                                state.date < state.today -> Note(stringResource(R.string.diary_plan_past_note))
                                // What resizing does is said beside the button, before it is pressed.
                                resizable -> {
                                    OutlinedButton(onClick = onResize, enabled = !state.changing && !state.offline) { Text(stringResource(R.string.diary_rebalance)) }
                                    Note(stringResource(R.string.diary_rebalance_note))
                                }
                            }
                        }
                    }
                }
                // A press that changes nothing says so, or it looks as if it had not been heard.
                state.resizeLeft?.let { left ->
                    Text(
                        when {
                            left == 0.0 -> stringResource(R.string.plans_resize_nothing)
                            left > 0 -> stringResource(R.string.plans_rebalance_left_over, formatNumber(left.roundToInt()))
                            else -> stringResource(R.string.plans_rebalance_left_under, formatNumber(abs(left).roundToInt()))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                TextButton(onClick = { onOpenPlan(plan.id, day.date) }) { Text(stringResource(R.string.diary_open_plan)) }
            }
        }
    }
}

/** A meal of the day: what its plan has for it, and what was written for it. */
@Composable
private fun MealSection(
    slot: String,
    meals: List<DiaryPlanMeal>,
    entries: List<DiaryEntry>,
    state: DiaryViewModel.UiState,
    onAdd: () -> Unit,
    onEaten: (DiaryPlanMeal) -> Unit,
    onEdit: (DiaryEntry) -> Unit,
    onRemove: (DiaryEntry) -> Unit,
) {
    val name = mealTypeLabel(slot)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.canAdd) {
                val addTo = stringResource(R.string.diary_add_to, name)
                TextButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = addTo }) {
                    Text(stringResource(R.string.diary_add))
                }
            }
        }
        AppCard {
            meals.forEachIndexed { index, meal ->
                if (index > 0) HorizontalDivider()
                PlanMealRow(meal, waiting = meal.id in state.waitingMeals, enabled = !state.changing && !state.ahead && meal.id !in state.waitingOwnMeals, onEaten = { onEaten(meal) })
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0 || meals.isNotEmpty()) HorizontalDivider()
                EntryRow(
                    entry,
                    waiting = entry.id in state.waitingEntries,
                    added = entry.id in state.waitingAdditions,
                    enabled = !state.changing,
                    onEdit = { onEdit(entry) },
                    onRemove = { onRemove(entry) },
                )
            }
        }
    }
}

/** A meal of the plan: its name, that it is the plan's, what it comes to, and whether it was eaten. */
@Composable
private fun PlanMealRow(meal: DiaryPlanMeal, waiting: Boolean, enabled: Boolean, onEaten: () -> Unit) {
    val eaten = stringResource(R.string.diary_eaten_of, meal.name)
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(meal.name, style = MaterialTheme.typography.bodyMedium)
            val kcal = meal.calories?.let { stringResource(R.string.diary_meal_kcal, formatNumber(it.roundToInt())) }
            Note(listOfNotNull(stringResource(R.string.diary_from_plan), kcal).joinToString(" · "))
            if (waiting) WaitingMark(Modifier.padding(top = 4.dp))
        }
        Checkbox(checked = meal.eaten, onCheckedChange = { onEaten() }, enabled = enabled, modifier = Modifier.semantics { contentDescription = eaten })
    }
}

/**
 * An entry: what it is of, how much of it there was and what it comes to, each said where it is known
 * while it waits to be sent, and the ways to change it and take it out. One [added] and waiting is
 * changed once the server has it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryRow(entry: DiaryEntry, waiting: Boolean, added: Boolean, enabled: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val muted = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onEdit, enabled = enabled && !added, colors = muted) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.diary_edit_label, entry.name))
            }
            IconButton(onClick = onRemove, enabled = enabled, colors = muted) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.diary_remove_label, entry.name))
            }
        }
        // The figures on a line of their own, the width of the row, so that a narrow screen does not break them.
        Note(entryLine(entry))
        if (waiting || entry.estimated) {
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.estimated) LabelChip("≈ ${stringResource(R.string.diary_estimated)}")
                if (waiting) WaitingMark()
            }
        }
    }
}

/** How much of an entry there was and what it comes to: what is known of it. */
@Composable
private fun entryLine(entry: DiaryEntry): String {
    val amount = entry.amount?.let { amountLabel(it.shown, it.shownUnit) } ?: servingsLabel(entry.servings ?: 1.0)
    val kcal = entry.calories?.let { stringResource(R.string.diary_meal_kcal, formatNumber(it.roundToInt())) }
    val macros = entry.macros?.let {
        stringResource(R.string.diary_entry_macros, formatNumber(it.protein.roundToInt()), formatNumber(it.fat.roundToInt()), formatNumber(it.carbs.roundToInt()))
    }
    return listOfNotNull(amount, kcal, macros).joinToString(" · ")
}

/** Servings as a person reads them: whole ones counted in the language's own forms, a part of one to the hundredth. */
@Composable
private fun servingsLabel(servings: Double): String {
    val rounded = (servings * 100).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) {
        pluralStringResource(R.plurals.diary_servings, rounded.toInt(), rounded.toInt())
    } else {
        stringResource(R.string.diary_servings_part, formatNumber(rounded, fractionDigits = 2))
    }
}

/** Copy into the day shown what was eaten on another day before it or after it, up to today. */
@Composable
private fun CopyDay(state: DiaryViewModel.UiState, onChoose: (LocalDate) -> Unit, onCopy: () -> Unit) {
    val itself = state.copyFrom == state.date
    // What came here is not copied again by a second press: a quick double press would write it twice.
    val done = state.copied?.takeIf { it.count > 0 }?.from == state.copyFrom
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.diary_copy_title), style = MaterialTheme.typography.titleMedium)
            Note(stringResource(R.string.diary_copy_note))
            DateField(
                label = stringResource(R.string.diary_copy_from),
                day = state.copyFrom,
                onPick = onChoose,
                modifier = Modifier.fillMaxWidth(),
                years = FIRST_DIARY_DAY.year..state.today.year,
                latest = state.today,
            )
            if (itself) Note(stringResource(R.string.diary_copy_same_day))
            if (done) Note(stringResource(R.string.diary_copy_done))
            OutlinedButton(onClick = onCopy, enabled = !state.copying && !itself && !done && !state.offline) {
                Text(stringResource(R.string.diary_copy_button, formatDayDate(state.date.toString())))
            }
            state.copied?.let { copied ->
                Text(
                    if (copied.count == 0) stringResource(R.string.diary_copied_nothing) else pluralStringResource(R.plurals.diary_copied, copied.count, copied.count),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** A small line of what there is to know, under what it is said of. */
@Composable
internal fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
