// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.ui.common.categoryLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatDates
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.formatQuantity
import net.whiteravens.dietapp.ui.common.formatSyncedAt
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.DateField
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineBanner
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The shopping lists of a plan: made for the whole plan or for some of its days, grouped by aisle,
 * ticked off while shopping; a list the plan's menu changed under is made again, and a list is
 * deleted from its menu.
 */
@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The window that makes a list for some of the plan's days, while it is open.
    var choosingDays by rememberSaveable { mutableStateOf(false) }
    // The list to delete, while that waits for a yes.
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    if (state.loading) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.height(32.dp).fillMaxWidth(0.5f))
            repeat(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp)) }
        }
        return
    }

    if (state.profiles.isEmpty() && state.error == null) {
        EmptyState(
            icon = Icons.Default.ShoppingCart,
            title = stringResource(R.string.shopping_no_profile),
            description = stringResource(R.string.shopping_no_profile_body),
        )
        return
    }

    state.plan?.takeIf { choosingDays }?.let { plan ->
        ListDaysDialog(
            plan = plan,
            onMake = { from, to ->
                choosingDays = false
                viewModel.generate(from, to)
            },
            onDismiss = { choosingDays = false },
        )
    }
    deleting?.let { listId ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.shopping_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.delete(listId)
                    },
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    val busy = state.generating || state.deleting
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_shopping),
                subtitle = stringResource(R.string.shopping_subtitle),
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
        if (state.plans.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.plans, key = { it.id }) { plan ->
                        FilterChip(
                            selected = plan.id == state.selectedPlanId,
                            onClick = { viewModel.selectPlan(plan.id) },
                            label = { Text(stringResource(R.string.plans_card_title, formatDate(plan.startDate), plan.durationDays)) },
                        )
                    }
                }
            }
        }
        // A list for some of the plan's days; the whole plan's is made where it has none.
        if (state.plan != null && !state.offline && state.lists.isNotEmpty()) {
            item {
                TextButton(onClick = { choosingDays = true }, enabled = !busy) {
                    Text(stringResource(if (state.generating) R.string.shopping_generating else R.string.shopping_generate_title))
                }
            }
        }

        state.error?.let { item { ErrorBanner(errorMessage(it)) } }
        if (state.offline) {
            item {
                OfflineBanner(
                    stringResource(
                        R.string.offline_cached,
                        state.list?.syncedAt?.let(::formatSyncedAt) ?: "—",
                    ),
                )
            }
        }

        when {
            state.plans.isEmpty() && state.selectedProfileId != null && state.error == null -> item {
                EmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = stringResource(R.string.shopping_no_plans),
                    description = stringResource(R.string.shopping_no_plans_body),
                )
            }

            state.listLoading && state.lists.isEmpty() -> items(2) {
                Skeleton(Modifier.fillMaxWidth().height(120.dp))
            }

            state.lists.isEmpty() && state.selectedPlanId != null -> item {
                EmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = stringResource(R.string.shopping_no_lists_title),
                    description = stringResource(R.string.shopping_no_lists),
                    cta = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = { viewModel.generate() }, enabled = !busy && !state.offline) {
                                if (state.generating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text(stringResource(R.string.shopping_make_list))
                                }
                            }
                            TextButton(onClick = { choosingDays = true }, enabled = !busy && !state.offline) {
                                Text(stringResource(R.string.shopping_generate_title))
                            }
                        }
                    },
                )
            }

            else -> state.list?.let { list ->
                if (state.lists.size > 1) {
                    item(key = "lists") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.lists, key = { it.id }) { other ->
                                FilterChip(
                                    selected = other.id == list.id,
                                    onClick = { viewModel.selectList(other.id) },
                                    label = { Text(formatDates(other.fromDate, other.toDate)) },
                                )
                            }
                        }
                    }
                }
                item(key = "list") { ListHeader(list, onDelete = { deleting = list.id }.takeUnless { state.offline || busy }) }
                if (list.stale) {
                    item(key = "stale") {
                        AppCard {
                            Text(stringResource(R.string.shopping_stale), style = MaterialTheme.typography.bodyMedium)
                            if (!state.offline) {
                                Text(
                                    stringResource(R.string.shopping_stale_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Button(
                                    onClick = { viewModel.update(list) },
                                    enabled = !busy,
                                    modifier = Modifier.padding(top = 8.dp),
                                ) { Text(stringResource(R.string.shopping_stale_update)) }
                            }
                        }
                    }
                }
                list.groups.forEach { group ->
                    item(key = "header-${group.category}") {
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                            Text(categoryLabel(group.category), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                stringResource(R.string.shopping_aisle_energy, formatNumber(group.estimatedCalories.roundToInt())),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item(key = "group-${group.category}") {
                        AppCard {
                            group.items.forEach { item ->
                                ShoppingItemRow(
                                    item = item,
                                    busy = item.id in state.updating,
                                    readOnly = state.offline || busy,
                                    onCheckedChange = { viewModel.setChecked(item.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A list's days and how far it is ticked off, with what can be done with the list behind one button. */
@Composable
private fun ListHeader(list: ShoppingList, onDelete: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formatDates(list.fromDate, list.toDate), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.shopping_summary, formatNumber(list.ticked), formatNumber(list.rows), formatNumber(list.totalEstimatedCalories.roundToInt())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ListActions(onDelete)
    }
}

/** What can be done with a list as a whole. */
@Composable
private fun ListActions(onDelete: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.shopping_list_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete)) },
                onClick = {
                    open = false
                    onDelete?.invoke()
                },
                enabled = onDelete != null,
            )
        }
    }
}

/**
 * The days of [plan] a list is to be made for, the whole plan to begin with. A first day put after
 * the last moves the last with it, and the other way round.
 */
@Composable
private fun ListDaysDialog(plan: MealPlanSummary, onMake: (from: String, to: String) -> Unit, onDismiss: () -> Unit) {
    val first = LocalDate.parse(plan.startDate)
    var from by rememberSaveable { mutableStateOf(first.toString()) }
    var to by rememberSaveable { mutableStateOf(first.plusDays(plan.durationDays - 1L).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_generate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(stringResource(R.string.shopping_from_date), LocalDate.parse(from), onPick = { day ->
                    from = day.toString()
                    if (from > to) to = from
                })
                DateField(stringResource(R.string.shopping_to_date), LocalDate.parse(to), onPick = { day ->
                    to = day.toString()
                    if (to < from) from = to
                })
            }
        },
        confirmButton = { TextButton(onClick = { onMake(from, to) }) { Text(stringResource(R.string.shopping_generate_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    busy: Boolean,
    readOnly: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // The whole row is ticked with a tap, and is read out as one thing: its name, its amount, its tick.
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = item.checked,
                enabled = !readOnly && !busy,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            Checkbox(checked = item.checked, onCheckedChange = null, enabled = !readOnly, modifier = Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (item.atHome > 0) {
                LabelChip(
                    stringResource(
                        R.string.shopping_from_pantry,
                        formatQuantity(item.atHome),
                        unitLabel(item.unit),
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(
            "${formatQuantity(item.toBuyQuantity)} ${unitLabel(item.unit)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun unitLabel(unit: String): String = when (unit) {
    "piece" -> stringResource(R.string.unit_piece)
    else -> unit
}
