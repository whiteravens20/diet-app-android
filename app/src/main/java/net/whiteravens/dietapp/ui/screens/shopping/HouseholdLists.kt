// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.HouseholdList
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.ui.common.categoryLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatDates
import net.whiteravens.dietapp.ui.common.formatList
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.DateField
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import kotlin.math.roundToInt

/*
 * The household lists of the shopping screen, as the web's section shows them: one list from the
 * plans of several profiles for the same days, each plan's own list a part of it.
 */

/** What a household list is, the account's lists to open, and the way to make one, with its form while it is open. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HouseholdCard(state: HouseholdViewModel.UiState, profiles: List<Profile>, viewModel: HouseholdViewModel) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.household_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.household_subhead), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.lists.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.lists.forEach { list ->
                        FilterChip(
                            selected = list.id == state.openedId,
                            onClick = { viewModel.open(list.id) },
                            label = { Text(stringResource(R.string.household_pick, formatDates(list.fromDate, list.toDate), formatList(list.profiles))) },
                        )
                    }
                }
            }
            val form = state.form
            if (form == null) {
                OutlinedButton(onClick = { viewModel.startForm(profiles) }, enabled = !state.offline) { Text(stringResource(R.string.household_open)) }
            } else {
                HouseholdForm(form, state.making, viewModel)
            }
            state.error?.let { ErrorBanner(errorMessage(it)) }
        }
    }
}

/** The days, a plan of each profile that has a day of them, what making the list does, and the button that makes it. */
@Composable
private fun HouseholdForm(form: HouseholdViewModel.Form, making: Boolean, viewModel: HouseholdViewModel) {
    DateField(stringResource(R.string.shopping_from_date), form.from, onPick = viewModel::from)
    DateField(stringResource(R.string.shopping_to_date), form.to, onPick = viewModel::to)
    Text(stringResource(R.string.household_plans), style = MaterialTheme.typography.labelLarge)
    form.profiles.forEach { profile ->
        val offered = form.offered(profile.id)
        val chosen = form.choiceOf(profile.id)
        CheckRow(
            label = profile.name,
            checked = chosen != null,
            enabled = !offered.isNullOrEmpty(),
            onChange = { ticked -> viewModel.choose(profile.id, if (ticked) offered?.firstOrNull()?.id else null) },
        )
        when {
            offered == null -> Skeleton(Modifier.fillMaxWidth().height(48.dp))
            offered.isEmpty() -> Text(stringResource(R.string.household_no_plan), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> ChoiceField(
                label = stringResource(R.string.household_plan_of, profile.name),
                options = offered.map { it.id },
                selected = chosen ?: offered.first().id,
                optionLabel = { id ->
                    val plan = offered.first { it.id == id }
                    pluralStringResource(R.plurals.plans_card_title, plan.durationDays, formatDate(plan.startDate), plan.durationDays)
                },
                onSelect = { id -> viewModel.choose(profile.id, id) },
                enabled = chosen != null,
            )
        }
    }
    Text(stringResource(R.string.household_replaces), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (form.planIds.size < 2) {
        Text(stringResource(R.string.household_at_least_two), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = viewModel::make, enabled = form.planIds.size >= 2 && !making) {
            Text(stringResource(if (making) R.string.shopping_generating else R.string.household_make))
        }
        TextButton(onClick = viewModel::closeForm) { Text(stringResource(R.string.common_cancel)) }
    }
}

/**
 * The household list opened, as a plan's list is shown: for whom and for which days, what is ticked,
 * out of date with the way to make it again, and its rows by aisle, each saying what each profile
 * needs of it. Changed without a network as well: the change waits to be sent.
 */
internal fun LazyListScope.householdList(
    list: HouseholdList,
    state: HouseholdViewModel.UiState,
    onChange: (itemId: String, change: RowChange) -> Unit,
    onUpdate: () -> Unit,
    onShare: (ListWords) -> Unit,
    onTableAndPrint: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val busy = state.making || state.deleting
    item(key = "household-head") {
        val words = householdWords(list)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.household_for_whom, formatList(list.profiles)), style = MaterialTheme.typography.titleMedium)
                Text(formatDates(list.fromDate, list.toDate), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.shopping_summary, formatNumber(list.ticked), formatNumber(list.rows), formatNumber(list.totalEstimatedCalories.roundToInt())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ListActions({ onShare(words) }, onTableAndPrint, onDelete)
        }
    }
    if (list.stale) {
        item(key = "household-stale") {
            AppCard {
                Text(stringResource(R.string.household_stale), style = MaterialTheme.typography.bodyMedium)
                if (!state.offline) {
                    Text(
                        stringResource(R.string.shopping_stale_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(onClick = onUpdate, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.shopping_stale_update)) }
                }
            }
        }
    }
    if (list.groups.any { group -> group.items.any { it.packsStand } }) {
        item(key = "household-packs") {
            Text(stringResource(R.string.household_packs_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    list.groups.forEach { group ->
        item(key = "household-header-${group.category}") {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text(categoryLabel(group.category), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.shopping_aisle_energy, formatNumber(group.estimatedCalories.roundToInt())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "household-group-${group.category}") {
            AppCard {
                group.items.forEach { item ->
                    ShoppingItemRow(
                        item = item,
                        busy = item.id in state.updating,
                        readOnly = busy,
                        waiting = item.id in state.waitingItems,
                        onChange = { change -> onChange(item.id, change) },
                    )
                }
            }
        }
    }
}
