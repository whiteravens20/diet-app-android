// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

@file:OptIn(ExperimentalMaterial3Api::class)

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.MealSlots
import net.whiteravens.dietapp.domain.PLAN_RELAXATIONS
import net.whiteravens.dietapp.domain.SupplyLevel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.DateField
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import net.whiteravens.dietapp.ui.screens.profiles.ProfileBounds
import net.whiteravens.dietapp.ui.screens.profiles.SupplyNote
import net.whiteravens.dietapp.ui.screens.profiles.WarningBox
import java.time.LocalDate

/** How many of the meals that stand in the way of a plan are named one by one. */
private const val MEALS_NAMED = 6

/**
 * A new plan for a profile: from a day, for some days, with what it is made for where that differs
 * from the profile, the preferences it applies, and in the advanced options each day's own. A plan
 * that could only be filled outside its limits names what stands in the way, and is made once the
 * user lets those limits give way.
 */
@Composable
fun NewPlanScreen(onBack: () -> Unit, onMade: (String) -> Unit, viewModel: NewPlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    LaunchedEffect(state.made) { state.made?.let(onMade) }
    val profile = state.profile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = profile?.let { stringResource(R.string.plans_generate_for, it.figures.name) } ?: stringResource(R.string.plans_generate),
            onBack = onBack,
        )
        when {
            state.loading -> Skeleton(Modifier.fillMaxWidth().height(320.dp))

            profile == null -> {
                state.error?.let { ErrorBanner(errorMessage(it)) }
                Button(onClick = viewModel::load) { Text(stringResource(R.string.common_retry)) }
            }

            else -> {
                val form = state.form
                val problems = if (state.showProblems) form.problems() else emptySet()
                val dayProblems = if (state.showProblems) form.dayProblems() else emptyMap()
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField(stringResource(R.string.plans_start_date), form.startDate, onPick = { day -> viewModel.edit { it.copy(startDate = day) } })
                        FormField(
                            value = form.durationDays,
                            onValueChange = { typed -> viewModel.edit { it.copy(durationDays = typed) } },
                            label = stringResource(R.string.plans_duration_days),
                            problem = if (PlanField.DURATION in problems) between(PlanBounds.DURATION_DAYS) else null,
                        )
                        CheckRow(
                            label = stringResource(R.string.plans_meal_prep),
                            hint = stringResource(R.string.plans_meal_prep_hint),
                            checked = form.mealPrepFriendly,
                        ) { ticked -> viewModel.edit { it.copy(mealPrepFriendly = ticked) } }

                        val custom = profile.figures.customMacroSplit
                        ChoiceField(
                            label = stringResource(R.string.plans_diet),
                            // The custom diet is the profile's own split: there is one to plan to, or the diet is not on offer.
                            options = listOf<String?>(null) + ProfileBounds.DIETS.filter { it != "custom" || custom != null },
                            selected = form.dietType,
                            optionLabel = { diet ->
                                when {
                                    diet == null -> stringResource(R.string.plans_as_in_profile, dietTypeLabel(profile.figures.dietType))
                                    diet == "custom" && custom != null -> stringResource(R.string.plans_diet_custom, custom.protein.toString(), custom.fat.toString(), custom.carbs.toString())
                                    else -> when (rules?.supplyOf(diet, state.planMealCount)?.level) {
                                        SupplyLevel.NONE -> stringResource(R.string.catalogue_diet_none, dietTypeLabel(diet))
                                        SupplyLevel.THIN -> stringResource(R.string.catalogue_diet_thin, dietTypeLabel(diet))
                                        else -> dietTypeLabel(diet)
                                    }
                                }
                            },
                            onSelect = { chosen -> viewModel.edit { it.copy(dietType = chosen) } },
                        )
                        ChoiceField(
                            label = stringResource(R.string.plans_meals_a_day),
                            options = listOf<Int?>(null) + PlanBounds.MEAL_COUNTS.toList(),
                            selected = form.mealCount,
                            optionLabel = { count ->
                                count?.toString() ?: stringResource(R.string.plans_as_in_profile, profile.figures.mealCount.toString())
                            },
                            onSelect = { chosen -> viewModel.edit { it.copy(mealCount = chosen) } },
                        )
                        FormField(
                            value = form.calorieTarget,
                            onValueChange = { typed -> viewModel.edit { it.copy(calorieTarget = typed) } },
                            label = stringResource(R.string.plans_calorie_target),
                            placeholder = state.profileTarget?.toString(),
                            problem = if (PlanField.CALORIE_TARGET in problems) between(PlanBounds.CALORIE_TARGET) else null,
                        )
                        Text(
                            stringResource(R.string.plans_choices_hint) +
                                (state.profileTarget?.let { " " + stringResource(R.string.plans_calorie_target_hint, formatNumber(it)) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(stringResource(R.string.plans_apply_prefs), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            stringResource(R.string.plans_apply_prefs_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CheckRow(stringResource(R.string.plans_honour_avoid), checked = form.respectExclusions) { ticked ->
                            viewModel.edit { it.copy(respectExclusions = ticked) }
                        }
                        CheckRow(stringResource(R.string.plans_prefer_favorites), checked = form.respectFavorites) { ticked ->
                            viewModel.edit { it.copy(respectFavorites = ticked) }
                        }
                        CheckRow(stringResource(R.string.plans_prefer_inventory), checked = form.respectInventory) { ticked ->
                            viewModel.edit { it.copy(respectInventory = ticked) }
                        }

                        AdvancedOptions(
                            form = form,
                            problems = problems,
                            dayProblems = dayProblems,
                            liked = state.liked,
                            sets = state.sets,
                            defaultMaxRepeats = rules?.maxRepeatsPerRecipe,
                            planMealCount = state.planMealCount,
                            onEdit = viewModel::edit,
                            onEditDay = viewModel::editDay,
                        )
                        SupplyNote(rules, state.planDiet, state.planMealCount)
                        state.error?.let { ErrorBanner(errorMessage(it)) }
                        Button(
                            onClick = viewModel::generate,
                            enabled = !state.generating,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(if (state.generating) R.string.plans_generating else R.string.plans_generate_button))
                        }
                    }
                }
                state.inTheWay?.let { inTheWay ->
                    InTheWayBox(inTheWay, busy = state.generating, onLetGiveWay = viewModel::letGiveWay, onCancel = viewModel::dismissInTheWay)
                }
            }
        }
    }
}

@Composable
private fun between(range: IntRange): String =
    stringResource(R.string.profile_field_between, formatNumber(range.first), formatNumber(range.last))

/** A box to tick, with what it does said beside it. */
@Composable
private fun CheckRow(label: String, hint: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * What stands in the way of a plan that could only be filled outside its limits: how many meals
 * would go outside and which limits, the first few meals by day, and the plan made once those
 * limits may give way.
 */
@Composable
private fun InTheWayBox(inTheWay: NewPlanViewModel.InTheWay, busy: Boolean, onLetGiveWay: () -> Unit, onCancel: () -> Unit) {
    val needed = inTheWay.needed
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

/**
 * The options of a plan beyond the usual ones, folded away until asked for: the most a recipe
 * repeats, the limits that may give way, and each day's own kind, meals, calories, cooking time,
 * food to use up, favourite set and locked meal.
 */
@Composable
private fun AdvancedOptions(
    form: NewPlanForm,
    problems: Set<PlanField>,
    dayProblems: Map<LocalDate, Set<DayField>>,
    liked: List<MarkedRecipe>,
    sets: List<FavoriteSet>,
    defaultMaxRepeats: Int?,
    planMealCount: Int,
    onEdit: ((NewPlanForm) -> NewPlanForm) -> Unit,
    onEditDay: (LocalDate, (DayDraft) -> DayDraft) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.plans_advanced_toggle), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            if (!open) return@Column
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plans_advanced_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.plans_advanced_legend_title), style = MaterialTheme.typography.labelMedium)
                        listOf(
                            R.string.plans_advanced_legend_day_kind,
                            R.string.plans_advanced_legend_meals,
                            R.string.plans_advanced_legend_calories,
                            R.string.plans_advanced_legend_cook_time,
                            R.string.plans_advanced_legend_use_up_by,
                            R.string.plans_advanced_legend_set,
                            R.string.plans_advanced_legend_lock,
                        ).forEach { line -> Legend(stringResource(line)) }
                        Legend(
                            defaultMaxRepeats?.let { stringResource(R.string.plans_advanced_legend_variety_most, it.toString()) }
                                ?: stringResource(R.string.plans_advanced_legend_variety_none),
                        )
                    }
                }
                FormField(
                    value = form.maxRepeats,
                    onValueChange = { typed -> onEdit { it.copy(maxRepeats = typed) } },
                    label = stringResource(R.string.plans_advanced_variety_floor),
                    placeholder = defaultMaxRepeats?.toString(),
                    hint = defaultMaxRepeats?.let { stringResource(R.string.plans_advanced_variety_floor_hint_most, it.toString()) }
                        ?: stringResource(R.string.plans_advanced_variety_floor_hint_none),
                    problem = if (PlanField.MAX_REPEATS in problems) between(PlanBounds.MAX_REPEATS) else null,
                )
                Text(stringResource(R.string.plans_advanced_may_give_way), style = MaterialTheme.typography.titleSmall)
                PLAN_RELAXATIONS.forEach { limit ->
                    CheckRow(limitName(limit), checked = limit in form.mayGiveWay) { ticked ->
                        onEdit { it.copy(mayGiveWay = PLAN_RELAXATIONS.filter { other -> if (other == limit) ticked else other in it.mayGiveWay }) }
                    }
                }
                Text(stringResource(R.string.plans_advanced_may_give_way_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.plans_advanced_per_day), style = MaterialTheme.typography.titleSmall)
                form.dates.forEach { date ->
                    DayOptions(date, form.day(date), dayProblems[date].orEmpty(), liked, sets, planMealCount) { change -> onEditDay(date, change) }
                }
            }
        }
    }
}

@Composable
private fun Legend(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun dayKindLabel(kind: String): String = when (kind) {
    "rest" -> stringResource(R.string.plans_advanced_kind_rest)
    "training" -> stringResource(R.string.plans_advanced_kind_training)
    "skip" -> stringResource(R.string.plans_advanced_kind_skip)
    else -> stringResource(R.string.plans_advanced_kind_normal)
}

/** One day's own options, folded under its date; everything but its kind is meaningless on a skipped day. */
@Composable
private fun DayOptions(
    date: LocalDate,
    day: DayDraft,
    problems: Set<DayField>,
    liked: List<MarkedRecipe>,
    sets: List<FavoriteSet>,
    planMealCount: Int,
    onEdit: ((DayDraft) -> DayDraft) -> Unit,
) {
    var open by rememberSaveable(date.toString()) { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(formatDayDate(date.toString()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (day != DayDraft()) {
                    Text(dayKindLabel(day.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(if (open || problems.isNotEmpty()) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            if (!open && problems.isEmpty()) return@Column
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceField(
                    label = stringResource(R.string.plans_advanced_day_kind),
                    options = PlanBounds.DAY_KINDS,
                    selected = day.kind,
                    optionLabel = { dayKindLabel(it) },
                    onSelect = { kind -> onEdit { it.copy(kind = kind) } },
                )
                if (day.skipped) return@Column
                ChoiceField(
                    label = stringResource(R.string.plans_advanced_meal_count),
                    options = listOf<Int?>(null) + PlanBounds.MEAL_COUNTS.toList(),
                    selected = day.mealCount,
                    optionLabel = { it?.toString() ?: stringResource(R.string.plans_advanced_use_default) },
                    // The meals the day has change, and with them the meal a recipe was locked to.
                    onSelect = { count -> onEdit { it.copy(mealCount = count, lockSlot = null, lockRecipeId = null) } },
                )
                FormField(
                    value = day.calorieTarget,
                    onValueChange = { typed -> onEdit { it.copy(calorieTarget = typed) } },
                    label = stringResource(R.string.plans_advanced_calories),
                    problem = if (DayField.CALORIE_TARGET in problems) between(PlanBounds.CALORIE_TARGET) else null,
                )
                FormField(
                    value = day.cookTime,
                    onValueChange = { typed -> onEdit { it.copy(cookTime = typed) } },
                    label = stringResource(R.string.plans_advanced_cook_time),
                    problem = if (DayField.COOK_TIME in problems) between(PlanBounds.COOK_TIME_MINUTES) else null,
                )
                CheckRow(stringResource(R.string.plans_advanced_use_up_by), checked = day.useUpBy) { ticked -> onEdit { it.copy(useUpBy = ticked) } }
                if (sets.isNotEmpty()) {
                    ChoiceField(
                        label = stringResource(R.string.plans_advanced_favorite_set),
                        options = listOf<String?>(null) + sets.map { it.id },
                        selected = day.setId,
                        optionLabel = { id -> sets.find { it.id == id }?.label ?: stringResource(R.string.plans_advanced_favorite_set_none) },
                        onSelect = { id -> onEdit { it.copy(setId = id) } },
                    )
                }
                val slots = MealSlots.BY_COUNT[day.mealCount ?: planMealCount].orEmpty()
                ChoiceField(
                    label = stringResource(R.string.plans_advanced_lock_slot),
                    options = listOf<String?>(null) + slots,
                    selected = day.lockSlot,
                    optionLabel = { slot -> slot?.let { mealTypeLabel(it) } ?: stringResource(R.string.plans_advanced_lock_none) },
                    onSelect = { slot -> onEdit { it.copy(lockSlot = slot, lockRecipeId = null) } },
                )
                val slot = day.lockSlot
                if (slot != null) {
                    val forSlot = liked.filter { slot in it.mealTypes }
                    if (forSlot.isEmpty()) {
                        Text(stringResource(R.string.plans_advanced_no_favorites_for_lock), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        ChoiceField(
                            label = stringResource(R.string.plans_advanced_lock_recipe),
                            options = listOf<String?>(null) + forSlot.map { it.recipeId },
                            selected = day.lockRecipeId,
                            optionLabel = { id -> forSlot.find { it.recipeId == id }?.title ?: stringResource(R.string.plans_advanced_lock_recipe_placeholder) },
                            onSelect = { id -> onEdit { it.copy(lockRecipeId = id) } },
                        )
                    }
                }
            }
        }
    }
}
