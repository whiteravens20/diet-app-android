package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.errorMessage
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
import kotlin.math.roundToInt

/** Calorie target, deficit and macro split for every profile — the web dashboard. */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.nav_dashboard),
                    subtitle = pluralStringResource(
                        R.plurals.dashboard_subtitle,
                        state.profiles.size,
                        state.profiles.size,
                    ),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            state.profiles.forEach { stats ->
                item(key = stats.profile.id) { ProfileSummary(stats) }
            }
        }
    }
}

@Composable
private fun ProfileSummary(stats: DashboardViewModel.ProfileStats) {
    val profile = stats.profile
    val target = stats.target
    val kcal = stringResource(R.string.unit_kcal)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            AccentChip(dietTypeLabel(profile.dietType))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.dashboard_maintenance),
                value = target?.let { "${it.maintenance.roundToInt()} $kcal" } ?: "—",
                icon = AppIcons.Flame,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = stringResource(R.string.dashboard_daily_deficit),
                value = target?.let { "${it.dailyDeficit.roundToInt()} $kcal" } ?: "—",
                icon = AppIcons.Schedule,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.dashboard_meals_per_day),
                value = profile.mealCount.toString(),
                icon = AppIcons.Restaurant,
                modifier = Modifier.weight(1f),
            )
        }

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
    }
}

@Composable
private fun DashboardSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.height(32.dp).fillMaxWidth(0.5f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.weight(1f).height(96.dp))
            Skeleton(Modifier.weight(1f).height(96.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.weight(1f).height(96.dp))
            Skeleton(Modifier.weight(1f).height(96.dp))
        }
        Skeleton(Modifier.fillMaxWidth().height(220.dp))
    }
}
