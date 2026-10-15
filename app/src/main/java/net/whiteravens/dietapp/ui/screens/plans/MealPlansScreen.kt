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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlanSummary
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

/** Plans per profile: profile chips, a new plan, offline-first plan list. */
@Composable
fun MealPlansScreen(
    onOpenPlan: (String) -> Unit,
    onNewPlan: (profileId: String) -> Unit,
    onCreateProfile: () -> Unit,
    viewModel: MealPlansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading -> ListSkeleton()

        state.profiles.isEmpty() && state.error == null -> EmptyState(
            icon = Icons.Default.DateRange,
            title = stringResource(R.string.plans_no_profile),
            description = stringResource(R.string.plans_no_profile_body),
            cta = {
                Button(onClick = onCreateProfile, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.plans_no_profile_cta))
                }
            },
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
                            state.syncedAt?.let(::formatSyncedAt) ?: "—",
                        ),
                    )
                }
            }
            item {
                Button(
                    onClick = { state.selectedProfileId?.let(onNewPlan) },
                    enabled = state.selectedProfileId != null,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.plans_generate), Modifier.padding(start = 8.dp))
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
            if (state.hasMore) {
                item {
                    OutlinedButton(
                        onClick = viewModel::showMore,
                        enabled = !state.loadingMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.plans_show_more)) }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: MealPlanSummary, onClick: () -> Unit) {
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
private fun ListSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.height(32.dp).fillMaxWidth(0.5f))
        Skeleton(Modifier.height(32.dp).fillMaxWidth(0.7f))
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(88.dp)) }
    }
}
