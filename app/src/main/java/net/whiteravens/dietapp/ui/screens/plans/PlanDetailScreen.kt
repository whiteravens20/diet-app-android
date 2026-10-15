// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.PlanPacks
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.SwapOffer
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatList
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.formatSigned
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AccentChip
import net.whiteravens.dietapp.ui.components.AiMark
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineBanner
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.modelPartText
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One plan: what it is and what a day of it holds on average, its days, and the selected day's
 * meals with what each says of itself; a meal opens as planned, and can be swapped.
 */
@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan = state.plan
    var openMealId by rememberSaveable { mutableStateOf<String?>(null) }
    // The day a meal of one's own is being added to, while its form is open.
    var addingTo by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val readOnly = state.offline
    val busy = state.changing || state.taking

    addingTo?.let { date ->
        CustomMealDialog(
            date = date,
            onAdd = { meal ->
                addingTo = null
                viewModel.addCustomMeal(date, meal)
            },
            onDismiss = { addingTo = null },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            text = { Text(stringResource(R.string.plans_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    plan?.days?.flatMap { it.meals }?.firstOrNull { it.id == openMealId }?.let { meal ->
        MealDialog(
            meal = meal,
            dietType = plan.dietType,
            onOpenRecipe = meal.recipeId?.let { id -> { openMealId = null; onOpenRecipe(id) } },
            onDismiss = { openMealId = null },
        )
    }

    state.offer?.let { offer ->
        SwapOfferDialog(
            offer = offer,
            busy = state.taking || state.offering != null,
            onTake = viewModel::takeOffer,
            onAnother = viewModel::anotherOffer,
            onDismiss = viewModel::dismissOffer,
        )
    }

    // The card of a rebalance stands over the end of the list: the list ends that much higher, so
    // that nothing of it stays under the card.
    var cardHeight by remember { mutableIntStateOf(0) }
    val underCard = if (state.rebalance != null) with(LocalDensity.current) { cardHeight.toDp() + 16.dp } else 0.dp
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + underCard),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(plan, onBack, onDelete = { confirmingDelete = true }.takeUnless { readOnly || busy }) }

            state.error?.let {
                item {
                    Column {
                        ErrorBanner(errorMessage(it))
                        if (plan == null) {
                            Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                    }
                }
            }
            if (state.offline) item { OfflineBanner(stringResource(R.string.offline_readonly)) }

            if (plan == null) {
                if (state.loading) {
                    item { Skeleton(Modifier.fillMaxWidth().height(40.dp)) }
                    items(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp)) }
                }
                return@LazyColumn
            }

            item { PlanFacts(plan) }
            if (plan.conflicts > 0) {
                item { ErrorBanner(pluralStringResource(R.plurals.plans_conflicts_notice, plan.conflicts, plan.conflicts)) }
            }

            item {
                val chips = rememberLazyListState()
                // The day the plan opened at is in sight, however far into the plan it lies.
                LaunchedEffect(plan.id) { chips.scrollToItem(state.selectedDay) }
                LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(plan.days, key = { _, day -> day.id }) { index, day ->
                        FilterChip(
                            selected = index == state.selectedDay,
                            onClick = { viewModel.selectDay(index) },
                            label = { Text(formatDayDate(day.date)) },
                        )
                    }
                }
            }

            val day = plan.days.getOrNull(state.selectedDay) ?: return@LazyColumn
            item(key = "summary-${day.id}") { DaySummary(day) }
            itemsIndexed(day.meals, key = { _, meal -> meal.id }) { _, meal ->
                MealCard(
                    meal = meal,
                    swapping = meal.id == state.offering,
                    readOnly = readOnly,
                    busy = busy,
                    onEaten = { eaten -> viewModel.setEaten(meal.id, eaten) },
                    onSwap = { viewModel.offerSwap(meal.id) },
                    onOpen = { openMealId = meal.id },
                )
            }
            if (!readOnly) {
                item(key = "add-${day.id}") {
                    TextButton(onClick = { addingTo = day.date }, enabled = !busy) { Text(stringResource(R.string.plans_add_custom_meal)) }
                }
            }
        }
        state.rebalance?.let { rebalance ->
            RebalanceCard(
                rebalance = rebalance,
                busy = busy,
                onUndo = viewModel::undoRebalance,
                onWeek = viewModel::rebalanceWeek,
                onDismiss = viewModel::dismissRebalance,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).onSizeChanged { cardHeight = it.height },
            )
        }
    }
}

/** What the plan is, when it begins, what a day of it holds on average, and whether a model had a hand in it. */
@Composable
private fun Header(plan: MealPlan?, onBack: () -> Unit, onDelete: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
        }
        Column(Modifier.padding(start = 4.dp).weight(1f)) {
            Text(
                if (plan != null) planSummary(plan.durationDays, plan.dietType) else stringResource(R.string.nav_meal_plans),
                style = MaterialTheme.typography.titleLarge,
            )
            if (plan != null) {
                Text(
                    planMeta(plan.startDate, plan.averageDailyNutrition.calories, plan.ingredientReuse),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (plan.byModel) AiMark(stringResource(R.string.ai_mark_plan), Modifier.padding(top = 6.dp), spelledOut = true)
            }
        }
        if (plan != null) {
            IconButton(onClick = { onDelete?.invoke() }, enabled = onDelete != null) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
            }
        }
    }
}

/**
 * What a day of the plan holds against its targets, the packs its menu opens and does not finish,
 * the limits it was let go outside of, and what the pantry had to do with it.
 */
@Composable
private fun PlanFacts(plan: MealPlan) {
    val average = plan.averageDailyNutrition
    val target = plan.targetMacros
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Fact(
                stringResource(
                    R.string.plans_plan_macros,
                    formatNumber(average.protein.roundToInt()),
                    formatNumber(target.protein.roundToInt()),
                    formatNumber(average.fat.roundToInt()),
                    formatNumber(target.fat.roundToInt()),
                    formatNumber(average.carbs.roundToInt()),
                    formatNumber(target.carbs.roundToInt()),
                ),
            )
            if (plan.packs.bought > 0) PacksLine(plan.packs)
            if (plan.mayGiveWay.isNotEmpty()) Fact(stringResource(R.string.plans_may_give_way, limitNames(plan.mayGiveWay)))
            when (plan.pantry) {
                "counted" -> Fact(stringResource(R.string.plans_pantry_counted))
                "left_out" -> Fact(stringResource(R.string.plans_pantry_left_out))
                "unused" -> Fact(stringResource(R.string.plans_pantry_unused))
            }
        }
    }
}

@Composable
private fun Fact(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** What the menu uses of the packs that do not keep once opened, unfolding into the packs it does not finish. */
@Composable
private fun PacksLine(packs: PlanPacks) {
    var open by rememberSaveable { mutableStateOf(false) }
    val percent = formatNumber((packs.used / packs.bought * 100).roundToInt())
    val wasted = packs.open.count { it.wasted }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .semantics { if (open) collapse { open = false; true } else expand { open = true; true } },
        ) {
            Fact(
                if (wasted == 0) stringResource(R.string.plans_packs_line, percent)
                else pluralStringResource(R.plurals.plans_packs_line_wasted, wasted, wasted, percent),
                Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (packs.open.isEmpty()) Fact(stringResource(R.string.plans_packs_all_finished))
                packs.open.forEach { pack ->
                    val left = amountLabel(pack.left.toDouble(), pack.unit)
                    val size = amountLabel(pack.packSize, pack.unit)
                    if (pack.wasted) Fact(stringResource(R.string.plans_packs_left_much, pack.name, left, size), color = MaterialTheme.colorScheme.tertiary)
                    else Fact(stringResource(R.string.plans_packs_left, pack.name, left, size))
                }
                Fact(stringResource(R.string.plans_packs_explain))
            }
        }
    }
}

/** A day against its target: what it holds in kcal and by how much it misses, its macros against theirs, and where it stands. */
@Composable
private fun DaySummary(day: PlanDay) {
    AppCard {
        if (day.skipped) {
            // A day the user skips is no shortfall, and there is nothing in it to change.
            Text(stringResource(R.string.plans_day_skipped), style = MaterialTheme.typography.titleMedium)
            return@AppCard
        }
        Text(
            stringResource(
                R.string.plans_calorie_line,
                formatNumber(day.nutrition.calories.roundToInt()),
                formatNumber(day.calorieTarget.roundToInt()),
                formatSigned(day.calorieDelta),
            ),
            style = MaterialTheme.typography.titleMedium,
            color = if (abs(day.calorieDelta) <= 50) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(
                R.string.plans_day_macros_of,
                formatNumber(day.nutrition.protein.roundToInt()),
                formatNumber(day.targetMacros.protein.roundToInt()),
                formatNumber(day.nutrition.fat.roundToInt()),
                formatNumber(day.targetMacros.fat.roundToInt()),
                formatNumber(day.nutrition.carbs.roundToInt()),
                formatNumber(day.targetMacros.carbs.roundToInt()),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        val standing = day.offTarget
        if (standing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                CautionChip(stringResource(if (standing == "over") R.string.plans_day_over else R.string.plans_day_under))
            }
            Text(
                stringResource(if (standing == "over") R.string.plans_day_over_hint else R.string.plans_day_under_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (day.belowSafetyFloor) {
            Text(
                stringResource(R.string.plans_day_below_floor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A meal's macros as a sentence: `Protein 25 g · Fat 10 g · Carbs 40 g · 420 kcal`. */
@Composable
private fun macrosLine(nutrition: Nutrition): String =
    "${stringResource(R.string.macros_protein)} ${formatNumber(nutrition.protein.roundToInt())} g · " +
        "${stringResource(R.string.macros_fat)} ${formatNumber(nutrition.fat.roundToInt())} g · " +
        "${stringResource(R.string.macros_carbs)} ${formatNumber(nutrition.carbs.roundToInt())} g · " +
        "${formatNumber(nutrition.calories.roundToInt())} kcal"

/** A meal's name: its recipe's, the name its user gave it, or what a meal of one's own is called. */
@Composable
private fun mealName(meal: Meal): String = meal.name.ifEmpty { stringResource(R.string.plans_custom_meal) }

/**
 * A meal of the day: its time of day and the chips that say at a glance what there is to say of it,
 * its name and macros; one that is neither eaten nor the user's own can be swapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealCard(
    meal: Meal,
    swapping: Boolean,
    readOnly: Boolean,
    busy: Boolean,
    onEaten: (Boolean) -> Unit,
    onSwap: () -> Unit,
    onOpen: () -> Unit,
) {
    val markEaten = stringResource(R.string.plans_action_for, stringResource(R.string.plans_mark_eaten), mealName(meal))
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = meal.eaten,
                onCheckedChange = onEaten,
                enabled = !readOnly && !busy,
                modifier = Modifier.semantics { contentDescription = markEaten },
            )
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabelChip(mealTypeLabel(meal.slot))
                    meal.byModel?.let { AiMark(modelPartText(it)) }
                    if (meal.custom) LabelChip(stringResource(R.string.plans_custom_chip))
                    if (meal.offDiet) CautionChip(stringResource(R.string.plans_off_diet))
                    meal.conflicts.forEach { conflict -> WarningChip(conflictLabel(conflict)) }
                    if (meal.disliked.isNotEmpty()) LabelChip(stringResource(R.string.plans_disliked))
                    if (meal.outsideLimits.isNotEmpty()) CautionChip(stringResource(R.string.plans_outside_limit_chip))
                    if (meal.onlyRecipe) LabelChip(stringResource(R.string.plans_only_recipe_chip))
                    if (meal.quantityScale != 1.0) {
                        PlainChip(stringResource(R.string.plans_auto_balanced_chip, formatNumber(meal.quantityScale, fractionDigits = 2)))
                    }
                    meal.cookAhead?.let { PlainChip(stringResource(if (it == "cook") R.string.plans_cook_once_chip else R.string.plans_cooked_chip)) }
                    if (meal.eaten) AccentChip(stringResource(R.string.plans_eaten))
                }
                Text(
                    mealName(meal),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    macrosLine(meal.nutrition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (swapping) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (!readOnly && !meal.eaten && !meal.custom) {
                IconButton(onClick = onSwap, enabled = !busy) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.plans_swap),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * What the last change rebalanced: the day's kcal before and after and how many meals were scaled,
 * whether the day is still off its target and what the week could not take back; the way back, the
 * rest of the week, and away. It goes by itself after a while, later where the phone is set to give
 * more time to act.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RebalanceCard(
    rebalance: Rebalance,
    busy: Boolean,
    onUndo: () -> Unit,
    onWeek: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    val timeout = LocalAccessibilityManager.current
        ?.calculateRecommendedTimeoutMillis(REBALANCE_SHOWN_MILLIS, containsIcons = false, containsText = true, containsControls = true)
        ?: REBALANCE_SHOWN_MILLIS
    LaunchedEffect(rebalance) {
        delay(timeout)
        dismiss()
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.plans_rebalance_title), style = MaterialTheme.typography.titleSmall)
            Hint(
                stringResource(
                    R.string.plans_rebalance_summary,
                    formatNumber(rebalance.caloriesBefore.roundToInt()),
                    formatNumber(rebalance.caloriesAfter.roundToInt()),
                    formatNumber(rebalance.changes.size),
                ),
            )
            if (rebalance.bestEffort) Hint(stringResource(R.string.plans_rebalance_best_effort), MaterialTheme.colorScheme.tertiary)
            if (rebalance.unabsorbedKcal != 0.0) {
                Hint(
                    stringResource(
                        if (rebalance.unabsorbedKcal > 0) R.string.plans_rebalance_left_over else R.string.plans_rebalance_left_under,
                        formatNumber(abs(rebalance.unabsorbedKcal).roundToInt()),
                    ),
                    MaterialTheme.colorScheme.tertiary,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = onUndo, enabled = !busy) { Text(stringResource(R.string.plans_rebalance_undo)) }
                if (rebalance.scope == "day") {
                    OutlinedButton(onClick = onWeek, enabled = !busy) { Text(stringResource(R.string.plans_rebalance_week)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.plans_rebalance_dismiss)) }
            }
        }
    }
}

/** How long the card of a rebalance stays, where the phone asks for no longer. */
private const val REBALANCE_SHOWN_MILLIS = 10_000L

/** The times of day a meal of one's own can be at, in the order the web application offers them. */
private val CUSTOM_MEAL_SLOTS = listOf("breakfast", "lunch", "dinner", "snack", "second_breakfast")

private val CustomMealFormSaver = listSaver<CustomMealForm, String>(
    save = { listOf(it.name, it.slot, it.calories, it.protein, it.fat, it.carbs, it.servings) },
    restore = { CustomMealForm(it[0], it[1], it[2], it[3], it[4], it[5], it[6]) },
)

/**
 * A meal of the user's own added to a day: its name, its time of day, what one serving holds and
 * how many servings it is. What that adds to the day is shown before anything is sent, so that
 * nobody types the whole meal as one serving of several.
 */
@Composable
private fun CustomMealDialog(date: String, onAdd: (CustomMeal) -> Unit, onDismiss: () -> Unit) {
    var form by rememberSaveable(stateSaver = CustomMealFormSaver) { mutableStateOf(CustomMealForm()) }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    val problems = if (showProblems) form.problems() else emptySet()
    @Composable
    fun between(range: ClosedFloatingPointRange<Double>): String =
        stringResource(R.string.profile_field_between, formatNumber(range.start, fractionDigits = 2), formatNumber(range.endInclusive, fractionDigits = 2))

    @Composable
    fun Amount(value: String, label: String, field: CustomMealField, range: ClosedFloatingPointRange<Double>, change: (CustomMealForm, String) -> CustomMealForm, hint: String? = null) {
        FormField(
            value = value,
            onValueChange = { typed -> form = change(form, typed) },
            label = label,
            hint = hint,
            problem = if (field in problems) between(range) else null,
            keyboardType = KeyboardType.Decimal,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.plans_custom_modal_title))
                Text(formatDayDate(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = form.name,
                    onValueChange = { typed -> form = form.copy(name = typed) },
                    label = stringResource(R.string.plans_custom_name),
                    problem = if (CustomMealField.NAME in problems) stringResource(R.string.plans_custom_name_problem, CustomMealBounds.NAME_LENGTH) else null,
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences,
                )
                ChoiceField(
                    label = stringResource(R.string.plans_custom_meal_type),
                    options = CUSTOM_MEAL_SLOTS,
                    selected = form.slot,
                    optionLabel = { mealTypeLabel(it) },
                    onSelect = { slot -> form = form.copy(slot = slot) },
                )
                Text(stringResource(R.string.plans_custom_per_serving), style = MaterialTheme.typography.titleSmall)
                Amount(form.calories, stringResource(R.string.plans_custom_calories), CustomMealField.CALORIES, CustomMealBounds.CALORIES, { f, v -> f.copy(calories = v) })
                Amount(form.protein, stringResource(R.string.plans_custom_protein), CustomMealField.PROTEIN, CustomMealBounds.PROTEIN, { f, v -> f.copy(protein = v) })
                Amount(form.fat, stringResource(R.string.plans_custom_fat), CustomMealField.FAT, CustomMealBounds.FAT, { f, v -> f.copy(fat = v) })
                Amount(form.carbs, stringResource(R.string.plans_custom_carbs), CustomMealField.CARBS, CustomMealBounds.CARBS, { f, v -> f.copy(carbs = v) })
                Amount(
                    form.servings,
                    stringResource(R.string.plans_custom_servings),
                    CustomMealField.SERVINGS,
                    CustomMealBounds.SERVINGS,
                    { f, v -> f.copy(servings = v) },
                    hint = stringResource(R.string.plans_custom_servings_hint),
                )
                val adds = form.adds()
                Text(
                    if (adds != null) {
                        stringResource(
                            R.string.plans_custom_total,
                            formatNumber(adds.calories),
                            formatNumber(adds.protein),
                            formatNumber(adds.fat),
                            formatNumber(adds.carbs),
                        )
                    } else {
                        stringResource(R.string.plans_custom_total_pending)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val meal = form.meal()
                    if (meal != null) onAdd(meal) else showProblems = true
                },
            ) { Text(stringResource(R.string.plans_custom_submit)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The recipe offered in the place of a meal, and what taking it does to the meal and to its day. */
@Composable
private fun SwapOfferDialog(
    offer: SwapOffer,
    busy: Boolean,
    onTake: () -> Unit,
    onAnother: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plans_swap_offer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(offer.recipeTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.plans_swap_servings, formatServings(offer.servings)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        R.string.plans_swap_meal_kcal,
                        offer.before.calories.roundToInt(),
                        offer.after.calories.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        R.string.plans_swap_day_kcal,
                        offer.dayBefore.calories.roundToInt(),
                        offer.dayAfter.calories.roundToInt(),
                        offer.calorieTarget.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onTake, enabled = !busy) { Text(stringResource(R.string.plans_swap_take)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onAnother, enabled = !busy) { Text(stringResource(R.string.plans_swap_another)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/** `1.0` → `1`, `1.5` → `1.5`: servings as a person writes them. */
private fun formatServings(servings: Double): String =
    if (servings % 1.0 == 0.0) servings.toLong().toString() else "%.1f".format(Locale.getDefault(), servings)

/**
 * A meal as planned: its own lines, in the amounts the plan gives it, what stands against it, and
 * what each of its chips stands for, in words.
 */
@Composable
private fun MealDialog(meal: Meal, dietType: String, onOpenRecipe: (() -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mealName(meal)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                meal.byModel?.let { AiMark(modelPartText(it), spelledOut = true) }
                Text(
                    macrosLine(meal.nutrition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (meal.custom) Hint(stringResource(R.string.plans_custom_macros_note))
                meal.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(line.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            amountLabel(line.quantity, line.unit),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                meal.conflicts.forEach { conflict ->
                    Text(
                        conflictHint(conflict),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (meal.offDiet) Hint(stringResource(R.string.plans_diet_override_hint, dietTypeLabel(dietType)), MaterialTheme.colorScheme.tertiary)
                if (meal.disliked.isNotEmpty()) Hint(stringResource(R.string.plans_disliked_hint, formatList(meal.disliked)))
                if (meal.outsideLimits.isNotEmpty()) Hint(stringResource(R.string.plans_outside_limit_hint, limitNames(meal.outsideLimits)))
                if (meal.onlyRecipe) Hint(stringResource(R.string.plans_only_recipe_hint))
                meal.cookAhead?.let { Hint(stringResource(if (it == "cook") R.string.plans_cook_once_hint else R.string.plans_cooked_hint)) }
            }
        },
        confirmButton = {
            if (onOpenRecipe != null) {
                Button(onClick = onOpenRecipe) { Text(stringResource(R.string.plans_open_recipe)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** A chip for what is to be borne in mind: a day off its target, a recipe off the diet, a meal outside a limit. */
@Composable
private fun CautionChip(text: String) {
    LabelChip(text = text, container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
}

/** A chip for what a meal is, without anything to mind in it: balanced by a factor, cooked ahead. */
@Composable
private fun PlainChip(text: String) {
    LabelChip(text = text, container = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), content = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun WarningChip(text: String) {
    LabelChip(
        text = text,
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun conflictLabel(conflict: String): String = stringResource(
    when (conflict) {
        "excluded_ingredient" -> R.string.plans_conflict_excluded_ingredient
        "avoided" -> R.string.plans_conflict_avoided
        else -> R.string.plans_conflict_allergen
    },
)

@Composable
private fun conflictHint(conflict: String): String = stringResource(
    when (conflict) {
        "excluded_ingredient" -> R.string.plans_conflict_excluded_ingredient_hint
        "avoided" -> R.string.plans_conflict_avoided_hint
        else -> R.string.plans_conflict_allergen_hint
    },
)
