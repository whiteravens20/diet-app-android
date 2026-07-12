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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                swapping = meal.id in state.swapping,
                readOnly = state.offline,
                onSwap = { viewModel.swapMeal(meal.id) },
                onOpen = meal.recipeId?.let { id -> { onOpenRecipe(id) } },
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
    onOpen: (() -> Unit)?,
) {
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LabelChip(mealTypeLabel(meal.slot))
                    if (meal.offDiet) AccentChip(stringResource(R.string.plans_off_diet))
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
