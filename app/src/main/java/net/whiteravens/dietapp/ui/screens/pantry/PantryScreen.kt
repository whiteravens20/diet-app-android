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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.icu.text.ListFormatter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
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
import net.whiteravens.dietapp.domain.FROZEN_KEEPS_DAYS
import net.whiteravens.dietapp.domain.Freshness
import net.whiteravens.dietapp.domain.MAX_PORTIONS
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
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.DateField
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineAsOf
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.Text
import net.whiteravens.dietapp.ui.components.WaitingMark
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import net.whiteravens.dietapp.ui.components.OutlinedField

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
                                    PantryViewModel.Failure.FREEZE -> R.string.pantry_err_freeze
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
                                        number = state.packageNumbers[row.id],
                                        waiting = row.id in state.waitingRows,
                                        added = row.id in state.waitingAdditions,
                                        enabled = !state.busy,
                                        onChange = { viewModel.change(row, it) },
                                        onRemove = { viewModel.remove(row) },
                                        onFreeze = { viewModel.openFreeze(row) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.freezing != null) FreezeDialog(state, viewModel)
}

/**
 * Something of the catalogue to put into the pantry: found by a part of its name, as many of it as
 * there are in a unit it can be kept in, the day it is best eaten before where it has one, and whether
 * it goes into the freezer, in how many portions there.
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
                OutlinedField(
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
            CheckRow(
                stringResource(R.string.pantry_frozen_box),
                checked = form.frozen,
                enabled = enabled,
                onChange = { viewModel.freezeIt(it, LocalDate.now()) },
            )
            if (form.frozen) {
                PortionsField(
                    value = form.portions,
                    count = form.portionCount,
                    parts = form.parts,
                    amountKnown = form.amount != null,
                    unit = form.unit,
                    shownUnit = if (form.unit == "piece") picked?.pieceName ?: "piece" else form.unit,
                    enabled = enabled,
                    onValue = viewModel::typePortions,
                )
            }
            Button(onClick = viewModel::add, enabled = state.canAdd, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.pantry_add))
            }
        }
    }
}

/**
 * A row of the pantry: what it is, how much of it there is and until when, how near that day is,
 * and its amount and day to change, in fields that open under it, the row to take out, or, where it is
 * fresh, to put into the freezer. A package in the freezer is told from the other packages of its food
 * by its [number]. A row with a change [waiting] to be sent says so; one [added] and waiting is changed
 * once the server has it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowItem(
    row: PantryRow,
    number: Int?,
    waiting: Boolean,
    added: Boolean,
    enabled: Boolean,
    onChange: (PantryChange) -> Unit,
    onRemove: () -> Unit,
    onFreeze: () -> Unit,
) {
    var editing by rememberSaveable(row.id) { mutableStateOf(false) }
    // A package in the freezer has a row of its own beside the fresh one and the other packages: each control says which it is of.
    val name = rowName(row, number)
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                amountLabel(row.shown, row.shownUnit, row.shownGrams),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            val muted = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            // Something put in that waits to be sent is changed once the server has it.
            IconButton(onClick = { editing = !editing }, enabled = enabled && !added, colors = muted) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.pantry_change_of, name))
            }
            if (!row.frozen) {
                IconButton(onClick = onFreeze, enabled = enabled && !added, colors = muted) {
                    Icon(AppIcons.Snowflake, contentDescription = stringResource(R.string.pantry_freeze_of, name))
                }
            } else {
                // The room of a fresh row's button into the freezer, so that the amounts of every row stand in one column.
                Spacer(Modifier.minimumInteractiveComponentSize())
            }
            IconButton(onClick = onRemove, enabled = enabled, colors = muted) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.pantry_remove_of, name))
            }
        }
        // The day on a line of its own, the width of the row, so that a narrow screen does not break it, after the
        // mark of what is frozen.
        if (row.bestBefore != null || row.frozen) {
            val day = row.bestBefore
            val near = when (day?.let { freshness(it, LocalDate.now()) }) {
                Freshness.SOON -> R.string.pantry_expiring_soon
                Freshness.PAST -> R.string.pantry_past_date
                Freshness.FINE, null -> null
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (row.frozen) {
                    LabelChip(
                        stringResource(R.string.pantry_frozen),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (day != null) {
                    Text(
                        stringResource(R.string.pantry_best_before_on, formatDate(day.toString())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
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
            RowFields(row, name, enabled, onSave = { change ->
                onChange(change)
                editing = false
            }, onCancel = { editing = false })
        }
    }
}

/** What a row is called where a control of it is named: its food, frozen, and the number of its package where there are more. */
@Composable
private fun rowName(row: PantryRow, number: Int?): String = when {
    !row.frozen -> row.name
    number != null -> stringResource(R.string.pantry_frozen_package_name, row.name, number)
    else -> stringResource(R.string.pantry_frozen_name, row.name)
}

/** A row's amount, in its own unit, and its day, as they are now until changed; saved where they change something. */
@Composable
private fun RowFields(row: PantryRow, name: String, enabled: Boolean, onSave: (PantryChange) -> Unit, onCancel: () -> Unit) {
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
            description = stringResource(R.string.pantry_quantity_of, name),
            modifier = Modifier.fillMaxWidth(),
        )
        DayField(day, enabled, onDay = { day = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            Button(onClick = { change?.let(onSave) }, enabled = enabled && change != null) { Text(stringResource(R.string.common_save)) }
        }
    }
}

/**
 * The window a fresh row goes into the freezer from: whole, or split into portions, each a package of its own dated as
 * the freezer keeps it. Why it could not be put in is said in the window.
 */
@Composable
private fun FreezeDialog(state: PantryViewModel.UiState, viewModel: PantryViewModel) {
    val row = state.freezing ?: return
    val sending = state.changing == row.id
    AlertDialog(
        onDismissRequest = viewModel::closeFreeze,
        title = {
            Column {
                Text(stringResource(R.string.pantry_freeze))
                Text(
                    stringResource(R.string.pantry_freeze_what, row.name, amountLabel(row.shown, row.shownUnit, row.shownGrams)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(R.plurals.pantry_freeze_intro, FROZEN_KEEPS_DAYS, FROZEN_KEEPS_DAYS),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PortionsField(
                    value = state.freezePortions,
                    count = state.freezeCount,
                    parts = state.freezeParts,
                    amountKnown = true,
                    unit = row.unit,
                    shownUnit = row.shownUnit,
                    enabled = !sending,
                    onValue = viewModel::typeFreezePortions,
                )
                state.freezeFailure?.let { error ->
                    ErrorBanner(
                        "${stringResource(R.string.pantry_err_freeze)} ${errorMessage(error)}",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.freeze(LocalDate.now()) }, enabled = !sending && state.freezeParts != null) {
                Text(stringResource(R.string.pantry_freeze))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::closeFreeze) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * How many packages something goes into the freezer in, typed, and under it what each holds, as the pantry will show
 * each: [parts] of [unit], a piece called [shownUnit]. Under it too why nothing can go so: a number the freezer does not
 * take, or parts of less than one of the unit.
 */
@Composable
private fun PortionsField(
    value: String,
    count: Int?,
    parts: List<Double>?,
    amountKnown: Boolean,
    unit: String,
    shownUnit: String,
    enabled: Boolean,
    onValue: (String) -> Unit,
) {
    val line: Pair<String, Boolean>? = when {
        count == null -> pluralStringResource(R.plurals.pantry_portions_range, MAX_PORTIONS, MAX_PORTIONS) to true
        count < 2 || !amountKnown -> null
        parts == null -> stringResource(R.string.error_code_portions_too_many) to true
        else -> {
            // Pieces to the half, the rest to the gram or millilitre, as each package will stand on the list.
            val shown = parts.map { part -> amountLabel(if (unit == "piece") Math.round(part * 2) / 2.0 else Math.round(part).toDouble(), shownUnit) }
            val locale = LocalConfiguration.current.locales[0]
            (
                if (shown.toSet().size == 1) pluralStringResource(R.plurals.pantry_portions_each, count, count, shown.first())
                else stringResource(R.string.pantry_portions_list, ListFormatter.getInstance(locale).format(shown))
                ) to false
        }
    }
    OutlinedField(
        value = value,
        onValueChange = onValue,
        accept = { typed -> typed.takeIf { it.all(Char::isDigit) && it.length <= 2 } },
        label = { Text(stringResource(R.string.pantry_portions)) },
        supportingText = line?.let { (text, _) -> { Text(text) } },
        isError = line?.second == true,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
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
    OutlinedField(
        value = value,
        onValueChange = onValue,
        accept = { typedNow -> typedNow.takeIf { it.isBlank() || PantryViewModel.amountOf(it) != null } },
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
        DateField(label, day, onPick = { if (enabled) onDay(it) }, Modifier.weight(1f), optional = true)
        if (day != null) {
            TextButton(onClick = { onDay(null) }, enabled = enabled) { Text(stringResource(R.string.common_clear)) }
        }
    }
}

/** An amount as a field holds it: the number in the language's own way, without grouping, to the hundredth. */
private fun fieldOf(amount: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false; maximumFractionDigits = 2 }.format(amount)
