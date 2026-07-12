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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.ui.common.categoryLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatQuantity
import net.whiteravens.dietapp.ui.common.formatSyncedAt
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineBanner
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton

/** Grocery list per plan, grouped by store section, tick-off while shopping. */
@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                            label = {
                                Text(
                                    stringResource(
                                        R.string.plans_card_title,
                                        formatDate(plan.startDate),
                                        plan.durationDays,
                                    ),
                                )
                            },
                        )
                    }
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

            state.listLoading && state.list == null -> items(2) {
                Skeleton(Modifier.fillMaxWidth().height(120.dp))
            }

            state.list == null && state.selectedPlanId != null -> item {
                EmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = stringResource(R.string.shopping_no_list),
                    description = stringResource(R.string.shopping_no_list_body),
                    cta = {
                        Button(onClick = viewModel::generate, enabled = !state.generating && !state.offline) {
                            if (state.generating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(stringResource(R.string.shopping_generate))
                            }
                        }
                    },
                )
            }

            else -> state.list?.let { list ->
                list.groups.forEach { group ->
                    item(key = "header-${group.category}") {
                        Text(
                            categoryLabel(group.category),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    item(key = "group-${group.category}") {
                        AppCard {
                            group.items.forEach { item ->
                                ShoppingItemRow(
                                    item = item,
                                    busy = item.id in state.updating,
                                    readOnly = state.offline,
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

@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    busy: Boolean,
    readOnly: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            Checkbox(checked = item.checked, onCheckedChange = onCheckedChange, enabled = !readOnly)
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (item.fromPantryQuantity > 0) {
                LabelChip(
                    stringResource(
                        R.string.shopping_from_pantry,
                        formatQuantity(item.fromPantryQuantity),
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
