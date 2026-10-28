// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.pantry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Freshness
import net.whiteravens.dietapp.domain.PantryChange
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.freshness
import net.whiteravens.dietapp.ui.common.WhenShownAgain
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.categoryLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.unitName
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.DateField
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineAsOf
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.WaitingMark
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * What a profile has at home, as the web's page keeps it: something of the catalogue put in, with
 * how much of it and until when, and each row under its category, its amount and day changed or
 * the row taken out. The profiles are [onProfiles].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantryScreen(
    onBack: () -> Unit,
    onProfiles: () -> Unit,
    viewModel: PantryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read again on being shown again: a shopping list ticked in the meantime changes the pantry too.
    WhenShownAgain(viewModel::shownAgain)

    PullToRead(state.pulled, viewModel::pull) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.pantry_title),
                    subtitle = stringResource(R.string.pantry_subhead),
                    onBack = onBack,
                )
            }
            state.readAt?.takeIf { state.offline }?.let { readAt -> item { OfflineAsOf(readAt) } }

            when {
                state.loadingProfiles -> item { Skeleton(Modifier.fillMaxWidth().height(160.dp)) }

                state.profilesError != null -> item {
                    Column {
                        ErrorBanner(errorMessage(state.profilesError!!))
                        Button(onClick = viewModel::loadProfiles, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }

                state.profiles.isEmpty() -> item {
                    EmptyState(
                        icon = AppIcons.Inventory,
                        title = stringResource(R.string.pantry_no_profile),
                        description = stringResource(R.string.pantry_no_profile_body),
                        cta = { Button(onClick = onProfiles) { Text(stringResource(R.string.pantry_no_profile_cta)) } },
                    )
                }

                else -> {
                    // The profiles to choose among, only where there are two or more.
                    if (state.profiles.size > 1) {
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.profiles.forEach { profile ->
                                    FilterChip(
                                        selected = profile.id == state.profileId,
                                        onClick = { viewModel.pickProfile(profile.id) },
                                        enabled = !state.busy,
                                        label = { Text(profile.name) },
                                    )
                                }
                            }
                        }
                    }
                    state.failed?.let { (what, error) ->
                        item {
                            val lead = stringResource(
                                when (what) {
                                    PantryViewModel.Failure.ADD -> R.string.pantry_err_add
                                    PantryViewModel.Failure.CHANGE -> R.string.pantry_err_change
                                    PantryViewModel.Failure.REMOVE -> R.string.pantry_err_remove
                                },
                            )
                            ErrorBanner("$lead ${errorMessage(error)}", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    item { AddCard(state, viewModel) }

                    when {
                        state.loading -> items(2) { Skeleton(Modifier.fillMaxWidth().height(96.dp)) }

                        state.error != null -> item {
                            Column {
                                ErrorBanner(errorMessage(state.error!!))
                                Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            }
                        }

                        state.shownRows.isEmpty() -> item {
                            EmptyState(
                                icon = AppIcons.Inventory,
                                title = stringResource(R.string.pantry_empty_title),
                                description = stringResource(R.string.pantry_empty_body),
                            )
                        }

                        else -> items(state.groups, key = { (category, _) -> "group-$category" }) { (category, rows) ->
                            AppCard {
                                Text(categoryLabel(category), style = MaterialTheme.typography.titleMedium)
                                rows.forEachIndexed { index, row ->
                                    if (index > 0) HorizontalDivider()
                                    RowItem(
                                        row = row,
                                        waiting = row.id in state.waitingRows,
                                        added = row.id in state.waitingAdditions,
                                        enabled = !state.busy,
                                        onChange = { viewModel.change(row, it) },
                                        onRemove = { viewModel.remove(row) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Something of the catalogue to put into the pantry: found by a part of its name, as many of it as
 * there are in a unit it can be kept in, and the day it is best eaten before where it has one.
 */
@Composable
private fun AddCard(state: PantryViewModel.UiState, viewModel: PantryViewModel) {
    val form = state.form
    val picked = form.picked
    val enabled = !state.adding
    AppCard {
        Text(stringResource(R.string.pantry_add_title), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (picked != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.pantry_ingredient),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(picked.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(onClick = viewModel::unpick, enabled = enabled) { Text(stringResource(R.string.pantry_change_pick)) }
                }
            } else {
                OutlinedTextField(
                    value = form.search,
                    onValueChange = viewModel::search,
                    label = { Text(stringResource(R.string.pantry_ingredient)) },
                    placeholder = { Text(stringResource(R.string.pantry_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    enabled = enabled,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (form.search.trim().length >= ProfilesViewModel.SEARCH_FROM) {
                    val offered = state.offered
                    if (offered.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                offered.forEach { ingredient ->
                                    Text(
                                        ingredient.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = enabled, role = Role.Button) { viewModel.pick(ingredient) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    } else if (form.searched) {
                        Text(
                            stringResource(R.string.pickers_no_matches),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AmountField(
                    label = stringResource(R.string.pantry_quantity),
                    value = form.quantity,
                    unit = null,
                    enabled = enabled,
                    onValue = viewModel::typeQuantity,
                    modifier = Modifier.weight(1f),
                )
                // Only what the ingredient can be kept in: stock in any other unit would count as nothing.
                ChoiceField(
                    label = stringResource(R.string.pantry_unit),
                    options = picked?.units ?: listOf("g", "ml", "piece"),
                    selected = form.unit,
                    optionLabel = { unit -> unitName(if (unit == "piece") picked?.pieceName ?: unit else unit) },
                    onSelect = viewModel::chooseUnit,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
            DayField(form.bestBefore, enabled, onDay = viewModel::chooseBestBefore)
            Button(onClick = viewModel::add, enabled = state.canAdd, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.pantry_add))
            }
        }
    }
}

/**
 * A row of the pantry: what it is, how much of it there is and until when, how near that day is,
 * and its amount and day to change, in fields that open under it, or the row to take out. A row with
 * a change [waiting] to be sent says so; one [added] and waiting is changed once the server has it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowItem(row: PantryRow, waiting: Boolean, added: Boolean, enabled: Boolean, onChange: (PantryChange) -> Unit, onRemove: () -> Unit) {
    var editing by rememberSaveable(row.id) { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                amountLabel(row.shown, row.shownUnit),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            val muted = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            // Something put in that waits to be sent is changed once the server has it.
            IconButton(onClick = { editing = !editing }, enabled = enabled && !added, colors = muted) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.pantry_change_of, row.name))
            }
            IconButton(onClick = onRemove, enabled = enabled, colors = muted) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.pantry_remove_of, row.name))
            }
        }
        // The day on a line of its own, the width of the row, so that a narrow screen does not break it.
        row.bestBefore?.let { day ->
            val near = when (freshness(day, LocalDate.now())) {
                Freshness.SOON -> R.string.pantry_expiring_soon
                Freshness.PAST -> R.string.pantry_past_date
                Freshness.FINE -> null
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.pantry_best_before_on, formatDate(day.toString())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                if (near != null) {
                    LabelChip(
                        stringResource(near),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        if (waiting) WaitingMark(Modifier.padding(top = 4.dp))
        if (editing && !added) {
            RowFields(row, enabled, onSave = { change ->
                onChange(change)
                editing = false
            }, onCancel = { editing = false })
        }
    }
}

/** A row's amount, in its own unit, and its day, as they are now until changed; saved where they change something. */
@Composable
private fun RowFields(row: PantryRow, enabled: Boolean, onSave: (PantryChange) -> Unit, onCancel: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    // What the row holds now starts the fields again whenever the row changes.
    var typed by rememberSaveable(row.id, row.shown) { mutableStateOf(fieldOf(row.shown, locale)) }
    var day by rememberSaveable(row.id, row.bestBefore) { mutableStateOf(row.bestBefore) }
    val change = PantryViewModel.changeOf(row, typed, day)
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmountField(
            label = stringResource(R.string.pantry_quantity),
            value = typed,
            unit = row.shownUnit,
            enabled = enabled,
            onValue = { typed = it },
            description = stringResource(R.string.pantry_quantity_of, row.name),
            modifier = Modifier.fillMaxWidth(),
        )
        DayField(day, enabled, onDay = { day = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            Button(onClick = { change?.let(onSave) }, enabled = enabled && change != null) { Text(stringResource(R.string.common_save)) }
        }
    }
}

/** An amount typed, [unit] beside it where it is not chosen next to the field; a number under nothing is not taken, an empty field is. */
@Composable
private fun AmountField(
    label: String,
    value: String,
    unit: String?,
    enabled: Boolean,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typedNow -> if (typedNow.isBlank() || PantryViewModel.amountOf(typedNow) != null) onValue(typedNow) },
        label = { Text(label) },
        suffix = unit?.let { { Text(unitName(it)) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    )
}

/** The day something is best eaten before, chosen in a calendar, and taken away where there is one. */
@Composable
private fun DayField(day: LocalDate?, enabled: Boolean, onDay: (LocalDate?) -> Unit) {
    val label = stringResource(R.string.pantry_best_before)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField(label, day, onPick = { if (enabled) onDay(it) }, Modifier.weight(1f))
        if (day != null) {
            TextButton(onClick = { onDay(null) }, enabled = enabled) { Text(stringResource(R.string.common_clear)) }
        }
    }
}

/** An amount as a field holds it: the number in the language's own way, without grouping, to the hundredth. */
private fun fieldOf(amount: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false; maximumFractionDigits = 2 }.format(amount)
