// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.SwapOffer
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AccentChip
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineBanner
import net.whiteravens.dietapp.ui.components.Skeleton
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One plan: header, day switcher, the selected day's meals with swap. */
@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan = state.plan
    var openMealId by rememberSaveable { mutableStateOf<String?>(null) }

    plan?.days?.flatMap { it.meals }?.firstOrNull { it.id == openMealId }?.let { meal ->
        MealDialog(
            meal = meal,
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Column(Modifier.padding(start = 4.dp)) {
                    Text(
                        if (plan != null) {
                            stringResource(R.string.plans_card_title, formatDate(plan.startDate), plan.durationDays)
                        } else {
                            stringResource(R.string.nav_meal_plans)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (plan != null) {
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccentChip(dietTypeLabel(plan.dietType))
                            LabelChip(
                                stringResource(
                                    R.string.plans_avg_kcal,
                                    plan.averageDailyNutrition.calories.roundToInt(),
                                ),
                            )
                        }
                    }
                }
            }
        }

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

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        if (day.skipped) {
            item { OfflineBanner(stringResource(R.string.plans_day_skipped)) }
        }
        itemsIndexed(day.meals, key = { _, meal -> meal.id }) { _, meal ->
            MealCard(
                meal = meal,
                swapping = meal.id == state.offering,
                readOnly = state.offline,
                onSwap = { viewModel.offerSwap(meal.id) },
                onOpen = { openMealId = meal.id },
            )
        }
    }
}

@Composable
private fun DaySummary(day: PlanDay) {
    AppCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    stringResource(
                        R.string.plans_day_total,
                        day.nutrition.calories.roundToInt(),
                        day.calorieTarget.roundToInt(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        R.string.plans_day_macros,
                        day.nutrition.protein.roundToInt(),
                        day.nutrition.fat.roundToInt(),
                        day.nutrition.carbs.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            DeltaChip(day.calorieDelta)
        }
        val standing = day.offTarget.takeUnless { day.skipped }
        if (standing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                LabelChip(
                    text = stringResource(if (standing == "over") R.string.plans_day_over else R.string.plans_day_under),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
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

@Composable
private fun DeltaChip(delta: Double) {
    val over = delta > 0
    LabelChip(
        text = (if (over) "+" else "−") + stringResource(R.string.plans_delta_kcal, abs(delta).roundToInt()),
        container = if (over) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
        content = if (over) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
private fun MealCard(
    meal: Meal,
    swapping: Boolean,
    readOnly: Boolean,
    onSwap: () -> Unit,
    onOpen: () -> Unit,
) {
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LabelChip(mealTypeLabel(meal.slot))
                    if (meal.offDiet) AccentChip(stringResource(R.string.plans_off_diet))
                    meal.conflicts.forEach { conflict -> WarningChip(conflictLabel(conflict)) }
                    if (meal.disliked.isNotEmpty()) WarningChip(stringResource(R.string.plans_disliked))
                    if (meal.eaten) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.plans_eaten),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    meal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(
                        R.string.plans_meal_nutrition,
                        meal.nutrition.calories.roundToInt(),
                        meal.nutrition.protein.roundToInt(),
                        meal.nutrition.fat.roundToInt(),
                        meal.nutrition.carbs.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (swapping) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (!readOnly) {
                IconButton(onClick = onSwap) {
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

/** A meal as planned: its own lines, in the amounts the plan gives it, and what stands against it. */
@Composable
private fun MealDialog(meal: Meal, onOpenRecipe: (() -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(meal.name) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(
                        R.string.plans_meal_nutrition,
                        meal.nutrition.calories.roundToInt(),
                        meal.nutrition.protein.roundToInt(),
                        meal.nutrition.fat.roundToInt(),
                        meal.nutrition.carbs.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                if (meal.disliked.isNotEmpty()) {
                    Text(
                        stringResource(R.string.plans_disliked_hint, meal.disliked.joinToString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
