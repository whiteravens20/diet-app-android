// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatSyncedAt
import net.whiteravens.dietapp.ui.components.AiMark
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.OfflineBanner
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import java.time.LocalDate
import java.time.YearMonth

/** Plans per profile: profile chips, a new plan, the profile's plans as a calendar, and the offline-first list of them. */
@Composable
fun MealPlansScreen(
    onOpenPlan: (planId: String, day: String?) -> Unit,
    onNewPlan: (profileId: String, startDate: String?) -> Unit,
    onCreateProfile: () -> Unit,
    viewModel: MealPlansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read again on coming back: a plan may have been made, changed or deleted in the meantime.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val today = remember { LocalDate.now() }

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
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            val profileId = state.selectedProfileId
            item {
                Button(
                    onClick = { profileId?.let { onNewPlan(it, null) } },
                    enabled = profileId != null,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.plans_generate), Modifier.padding(start = 8.dp))
                }
            }
            if (profileId != null) {
                item {
                    PlanCalendar(
                        calendar = state.calendar,
                        today = today,
                        onLeaf = viewModel::leaf,
                        onThisMonth = { viewModel.showMonth(YearMonth.from(today)) },
                        onRetry = viewModel::retryCalendar,
                        onOpen = { planId, date -> onOpenPlan(planId, date.toString()) },
                        // A day without a plan is where the next one can begin: the form starts on it.
                        onStart = { date -> onNewPlan(profileId, date.toString()) },
                    )
                }
                item {
                    Text(
                        state.profiles.find { it.id == profileId }?.let { stringResource(R.string.plans_for, it.name) } ?: stringResource(R.string.nav_meal_plans),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
            }
            if (state.plans.isEmpty() && !state.refreshing && profileId != null) {
                item {
                    EmptyState(
                        icon = Icons.Default.DateRange,
                        title = stringResource(R.string.plans_empty),
                        description = stringResource(R.string.plans_empty_body),
                    )
                }
            }
            items(state.plans, key = { it.id }) { plan ->
                PlanCard(plan, onClick = { onOpenPlan(plan.id, null) })
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

/** A plan in the list: what it is, when it begins, what a day of it holds on average, and a model's hand in it. */
@Composable
private fun PlanCard(plan: MealPlanSummary, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Text(planSummary(plan.durationDays, plan.dietType), style = MaterialTheme.typography.titleMedium)
        Text(
            planMeta(plan.startDate, plan.averageDailyNutrition.calories, plan.ingredientReuse),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (plan.byModel) AiMark(stringResource(R.string.ai_mark_plan), Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ListSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Skeleton(Modifier.height(32.dp).fillMaxWidth(0.5f))
        Skeleton(Modifier.height(32.dp).fillMaxWidth(0.7f))
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(88.dp)) }
    }
}
