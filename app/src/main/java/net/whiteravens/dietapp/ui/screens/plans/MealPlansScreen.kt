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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatSyncedAt
import net.whiteravens.dietapp.ui.components.AccentChip
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineBanner
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import kotlin.math.roundToInt

/** Plans per profile: profile chips, generate action, offline-first plan list. */
@Composable
fun MealPlansScreen(
    onOpenPlan: (String) -> Unit,
    viewModel: MealPlansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showGenerateDialog by rememberSaveable { mutableStateOf(false) }

    if (showGenerateDialog) {
        GeneratePlanDialog(
            defaultMealCount = viewModel.selectedProfile?.mealCount ?: 3,
            busy = state.generating,
            onConfirm = { days, meals -> viewModel.generate(days, meals) },
            onDismiss = { showGenerateDialog = false },
        )
    }

    when {
        state.loading -> ListSkeleton()

        state.profiles.isEmpty() && state.error == null -> EmptyState(
            icon = Icons.Default.DateRange,
            title = stringResource(R.string.plans_no_profile),
            description = stringResource(R.string.plans_no_profile_body),
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.nav_meal_plans),
                    subtitle = stringResource(R.string.plans_subtitle),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.profiles, key = { it.id }) { profile ->
                        FilterChip(
                            selected = profile.id == state.selectedProfileId,
                            onClick = { viewModel.selectProfile(profile.id) },
                            label = { Text(profile.name) },
                        )
                    }
                }
            }
            state.error?.let { item { ErrorBanner(errorMessage(it)) } }
            if (state.offline) {
                item {
                    OfflineBanner(
                        stringResource(
                            R.string.offline_cached,
                            state.plans.firstOrNull()?.syncedAt?.let(::formatSyncedAt) ?: "—",
                        ),
                    )
                }
            }
            item {
                Button(
                    onClick = { showGenerateDialog = true },
                    enabled = state.selectedProfileId != null && !state.generating,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.plans_generate), Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (state.plans.isEmpty() && !state.refreshing && state.selectedProfileId != null) {
                item {
                    EmptyState(
                        icon = Icons.Default.DateRange,
                        title = stringResource(R.string.plans_empty),
                        description = stringResource(R.string.plans_empty_body),
                    )
                }
            }
            items(state.plans, key = { it.id }) { plan ->
                PlanCard(plan, onClick = { onOpenPlan(plan.id) })
            }
        }
    }
}

@Composable
private fun PlanCard(plan: MealPlan, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Text(
            stringResource(R.string.plans_card_title, formatDate(plan.startDate), plan.durationDays),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

@Composable
private fun GeneratePlanDialog(
    defaultMealCount: Int,
    busy: Boolean,
    onConfirm: (durationDays: Int, mealCount: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var duration by rememberSaveable { mutableIntStateOf(7) }
    var mealCount by rememberSaveable { mutableIntStateOf(defaultMealCount.coerceIn(2, 5)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plans_generate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.plans_duration),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 7, 14).forEach { days ->
                        FilterChip(
                            selected = duration == days,
                            onClick = { duration = days },
                            label = { Text(stringResource(R.string.plans_days, days)) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.plans_meals_per_day),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { mealCount = (mealCount - 1).coerceAtLeast(2) },
                        enabled = mealCount > 2,
                    ) { Text("−") }
                    Text(mealCount.toString(), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = { mealCount = (mealCount + 1).coerceAtMost(5) },
                        enabled = mealCount < 5,
                    ) { Text("+") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(duration, mealCount); onDismiss() },
                enabled = !busy,
            ) { Text(stringResource(R.string.plans_generate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun ListSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.height(32.dp).fillMaxWidth(0.5f))
        Skeleton(Modifier.height(32.dp).fillMaxWidth(0.7f))
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(88.dp)) }
    }
}
