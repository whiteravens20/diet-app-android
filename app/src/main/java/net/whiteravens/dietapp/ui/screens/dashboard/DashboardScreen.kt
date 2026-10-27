// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.notes
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.noteText
import net.whiteravens.dietapp.ui.components.AccentChip
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.MacroLegend
import net.whiteravens.dietapp.ui.components.MacroRing
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.StatCard
import net.whiteravens.dietapp.ui.components.rememberWebPages
import net.whiteravens.dietapp.ui.screens.notifications.NotificationsBell
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Calorie target, deficit and macro split for every profile, what its plan of the moment comes to,
 * and its weights — the web dashboard, whose favourite sets are made and changed there, in the
 * browser. A plan opens on [onOpenPlan]; a plan is made for a profile on [onNewPlan]; the bell in
 * the header counts the notifications not read yet and opens them on [onOpenNotifications]. With
 * [showWeightOf], the weights of that profile are brought into view, and [onWeightShown] says so.
 */
@Composable
fun DashboardScreen(
    onCreateProfile: () -> Unit,
    onOpenPlan: (planId: String) -> Unit,
    onNewPlan: (profileId: String) -> Unit,
    onOpenNotifications: () -> Unit,
    showWeightOf: String? = null,
    onWeightShown: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read again on coming back: a profile may have been made or changed in the meantime.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val web = rememberWebPages()
    val list = rememberLazyListState()
    // The profile's card is composed first, if it is out of sight, and its weights then brought into view.
    LaunchedEffect(showWeightOf, state.profiles) {
        val at = state.profiles.indexOfFirst { it.profile.id == showWeightOf }
        if (at >= 0 && list.layoutInfo.visibleItemsInfo.none { it.key == showWeightOf }) list.scrollToItem(at + 1)
    }

    when {
        state.loading -> DashboardSkeleton()

        state.error != null -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            ErrorBanner(errorMessage(state.error!!))
            Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.common_retry))
            }
        }

        state.profiles.isEmpty() -> EmptyState(
            icon = Icons.Default.Person,
            title = stringResource(R.string.dashboard_welcome),
            description = stringResource(R.string.dashboard_no_profiles),
            cta = {
                Button(onClick = onCreateProfile, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.dashboard_create_first_profile_cta))
                }
            },
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.Top) {
                    ScreenHeader(
                        title = stringResource(R.string.nav_dashboard),
                        subtitle = pluralStringResource(
                            R.plurals.dashboard_subtitle,
                            state.profiles.size,
                            state.profiles.size,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    NotificationsBell(state.unread, onClick = onOpenNotifications)
                }
            }
            state.profiles.forEach { stats ->
                item(key = stats.profile.id) {
                    ProfileSummary(
                        stats,
                        onOpenPlan,
                        onNewPlan = { onNewPlan(stats.profile.id) },
                        weight = {
                            val inView = remember { BringIntoViewRequester() }
                            if (stats.profile.id == showWeightOf) {
                                LaunchedEffect(inView) {
                                    inView.bringIntoView()
                                    onWeightShown()
                                }
                            }
                            Box(Modifier.bringIntoViewRequester(inView)) {
                                WeightCard(
                                    stats,
                                    unit = state.unit,
                                    log = state.weightLogs[stats.profile.id] ?: DashboardViewModel.WeightLog(),
                                    onType = { viewModel.typeWeight(stats.profile.id, it) },
                                    onLog = { viewModel.logWeight(stats.profile.id) },
                                    onRemoveLatest = { viewModel.removeLatestWeight(stats.profile.id) },
                                )
                            }
                        },
                    ) {
                        FavoriteSetsInTheBrowser(onOpen = { web.open("dashboard") })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSummary(
    stats: DashboardViewModel.ProfileStats,
    onOpenPlan: (planId: String) -> Unit,
    onNewPlan: () -> Unit,
    weight: @Composable () -> Unit,
    sets: @Composable () -> Unit,
) {
    val profile = stats.profile
    val target = stats.target
    val kcal = stringResource(R.string.unit_kcal)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            AccentChip(dietTypeLabel(profile.dietType))
            OutlinedButton(onClick = onNewPlan) { Text(stringResource(R.string.dashboard_generate_plan)) }
        }

        // As tall as the taller of the two: one has a hint under its value, the other none.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                label = stringResource(R.string.dashboard_daily_target),
                value = target?.let { "${it.dailyTarget.roundToInt()} $kcal" } ?: "—",
                icon = AppIcons.Target,
                hint = target?.let {
                    stringResource(
                        if (it.source == "manual_override") R.string.dashboard_manual_hint
                        else R.string.dashboard_calculated_hint,
                    )
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCard(
                label = stringResource(R.string.dashboard_maintenance),
                value = target?.let { "${it.maintenance.roundToInt()} $kcal" } ?: "—",
                icon = AppIcons.Flame,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        // As tall as the taller of the two: one has a hint under its value, the other none.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Under maintenance a deficit, above it a surplus: the label says which, the number how much.
            StatCard(
                label = stringResource(if (target != null && target.dailyDeficit < 0) R.string.dashboard_daily_surplus else R.string.dashboard_daily_deficit),
                value = target?.let { "${abs(it.dailyDeficit).roundToInt()} $kcal" } ?: "—",
                icon = AppIcons.Schedule,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCard(
                label = stringResource(R.string.dashboard_meals_per_day),
                value = profile.mealCount.toString(),
                icon = AppIcons.Restaurant,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        stats.targetError?.let { ErrorBanner(errorMessage(it)) }

        val notes = target?.notes().orEmpty()
        if (notes.isNotEmpty()) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    notes.forEach { note ->
                        Text(
                            noteText(note),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (note.strong) FontWeight.Medium else null,
                            color = if (note.strong) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        PlanOverviewCard(stats.overview, stats.overviewError, onOpen = onOpenPlan, onNewPlan = onNewPlan)

        if (target != null) {
            AppCard {
                Text(
                    stringResource(R.string.dashboard_macro_split),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MacroRing(
                        nutrition = target.targetMacros,
                        centerLabel = stringResource(R.string.dashboard_kcal_per_day),
                    )
                    MacroLegend(
                        labels = listOf(
                            stringResource(R.string.macro_protein),
                            stringResource(R.string.macro_fat),
                            stringResource(R.string.macro_carbs),
                        ),
                        grams = listOf(
                            target.targetMacros.protein,
                            target.targetMacros.fat,
                            target.targetMacros.carbs,
                        ),
                    )
                }
            }
        }

        weight()
        sets()
    }
}

/** A profile's favourite sets are made and changed in the web application; on the phone they go on a plan's days from its menu. */
@Composable
private fun FavoriteSetsInTheBrowser(onOpen: () -> Unit) {
    AppCard {
        Text(stringResource(R.string.favorite_sets_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.favorite_sets_in_the_browser),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedButton(onClick = onOpen, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.web_open))
        }
    }
}

@Composable
private fun DashboardSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Skeleton(Modifier.height(32.dp).fillMaxWidth(0.5f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.weight(1f).height(96.dp))
            Skeleton(Modifier.weight(1f).height(96.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.weight(1f).height(96.dp))
            Skeleton(Modifier.weight(1f).height(96.dp))
        }
        Skeleton(Modifier.fillMaxWidth().height(220.dp))
    }
}
