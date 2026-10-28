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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.ui.common.WhenShownAgain
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.categoryLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatDates
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.unitName
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.DateField
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineAsOf
import net.whiteravens.dietapp.ui.components.WaitingMark
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.rememberWebPages
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The shopping lists of a plan: made for the whole plan or for some of its days, grouped by aisle,
 * ticked off while shopping; a list the plan's menu changed under is made again, and a list is
 * deleted from its menu. What a list could do without leads to the plan at the day it concerns,
 * which [onOpenPlan] opens.
 */
@Composable
fun ShoppingScreen(
    onOpenPlan: (planId: String, day: String?) -> Unit,
    onTurnAiOn: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read again on being shown again: a plan or a list may have been made, changed or deleted in the meantime.
    WhenShownAgain(viewModel::shownAgain)
    // The window that makes a list for some of the plan's days, while it is open.
    var choosingDays by rememberSaveable { mutableStateOf(false) }
    // The list to delete, while that waits for a yes.
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    // What somebody in a shop is looking for on the list: the entries whose name holds the words.
    var find by rememberSaveable { mutableStateOf("") }
    // The list taken out of the app as a text to send; as a table or on paper it is the web application's, opened in the browser.
    val context = LocalContext.current
    val chooser = stringResource(R.string.shopping_share_text)
    val web = rememberWebPages()

    if (state.loading) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.height(32.dp).fillMaxWidth(0.5f))
            repeat(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp)) }
        }
        return
    }

    if (state.profiles.isEmpty() && state.error == null) {
        PullToRead(state.pulled, viewModel::pull) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                EmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = stringResource(R.string.shopping_no_profile),
                    description = stringResource(R.string.shopping_no_profile_body),
                )
            }
        }
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
    PullToRead(state.pulled, viewModel::pull) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.nav_shopping),
                    subtitle = stringResource(R.string.shopping_subtitle),
                )
            }
            // The profiles to choose among, where there is more than one.
            if (state.profiles.size > 1) item {
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
            if (state.offline) item { OfflineAsOf(state.readAt) }

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

                else -> state.shown?.let { list ->
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
                    item(key = "list") {
                        val words = listWords(list)
                        ListHeader(
                            list,
                            onShare = { shareText(context, words.title, listText(list, words), chooser) },
                            onTableAndPrint = { web.open("shopping-lists") },
                            onDelete = { deleting = list.id }.takeUnless { state.offline || busy },
                        )
                    }
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
                    item(key = "proposals") {
                        ListProposalsPanel(
                            found = state.shownProposals,
                            asking = state.askingProposals,
                            aiOn = state.aiOn,
                            enabled = !state.offline && !busy,
                            listFrom = list.fromDate,
                            onAsk = viewModel::findProposals,
                            onOpenPlanAt = { day -> onOpenPlan(list.planId, day) },
                            onTurnAiOn = onTurnAiOn,
                        )
                    }
                    item(key = "find") {
                        OutlinedTextField(
                            value = find,
                            onValueChange = { find = it },
                            label = { Text(stringResource(R.string.shopping_find_entry)) },
                            placeholder = { Text(stringResource(R.string.shopping_find_entry_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val everyItem = list.groups.flatMap { it.items }
                    if (everyItem.any { it.packsStand }) {
                        item(key = "packs") {
                            Text(stringResource(R.string.shopping_packs_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val wanted = find.trim().lowercase()
                    val groups = if (wanted.isEmpty()) list.groups
                    else list.groups.map { group -> group.copy(items = group.items.filter { wanted in it.name.lowercase() }) }.filter { it.items.isNotEmpty() }
                    if (groups.isEmpty()) {
                        item(key = "none") {
                            Text(stringResource(R.string.shopping_find_entry_none, find.trim()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    groups.forEach { group ->
                        item(key = "header-${group.category}") {
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                                Text(categoryLabel(group.category), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                // What the whole aisle comes to, whatever part of it is looked for.
                                val aisle = list.groups.first { it.category == group.category }
                                Text(
                                    stringResource(R.string.shopping_aisle_energy, formatNumber(aisle.estimatedCalories.roundToInt())),
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
                                        // A row is changed without a network as well: the change waits to be sent.
                                        readOnly = busy,
                                        waiting = item.id in state.waitingItems,
                                        onChange = { change -> viewModel.change(item.id, change) },
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

/** A list's days and how far it is ticked off, with what can be done with the list behind one button. */
@Composable
private fun ListHeader(list: ShoppingList, onShare: () -> Unit, onTableAndPrint: () -> Unit, onDelete: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formatDates(list.fromDate, list.toDate), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.shopping_summary, formatNumber(list.ticked), formatNumber(list.rows), formatNumber(list.totalEstimatedCalories.roundToInt())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ListActions(onShare, onTableAndPrint, onDelete)
    }
}

/** What can be done with a list as a whole: sent as a text, as a table or printed in the web application, or deleted. */
@Composable
private fun ListActions(onShare: () -> Unit, onTableAndPrint: () -> Unit, onDelete: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.shopping_list_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(action: () -> Unit) {
                open = false
                action()
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.shopping_share_text)) }, onClick = { pick(onShare) })
            DropdownMenuItem(text = { Text(stringResource(R.string.web_table_and_print)) }, onClick = { pick(onTableAndPrint) })
            DropdownMenuItem(text = { Text(stringResource(R.string.common_delete)) }, onClick = { onDelete?.let(::pick) }, enabled = onDelete != null)
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

/**
 * A row of the list: what is still to be bought of it, ticked with a tap; the packs it takes, taken
 * for bought with a tap; and what is at home and what was bought, told in a part that opens under
 * it. The API keeps one number for everything the user has for a row; the row shows it as its two
 * parts, and sends the part that changed. What is at home starts at what the pantry had when the
 * list was made, and is the user's to correct: a pantry does not know everything in a kitchen. A
 * row whose change waits to be sent says so.
 */
@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    busy: Boolean,
    readOnly: Boolean,
    waiting: Boolean,
    onChange: (RowChange) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    // The two fields as typed, begun anew whenever the server's row changes.
    val server = listOf(item.atHome, item.purchasedQuantity, item.checked)
    var home by rememberSaveable(item.id, server) { mutableStateOf(fieldOf(item.atHome, locale)) }
    var bought by rememberSaveable(item.id, server) { mutableStateOf(fieldOf(boughtOf(item), locale)) }
    val homeNow = typed(home) ?: 0.0
    val boughtNow = typed(bought) ?: 0.0
    // What is left to buy follows the fields while they are typed in.
    val remaining = (item.totalQuantity - homeNow - boughtNow).coerceAtLeast(0.0)
    val pending = rowChange(item, homeNow, boughtNow)

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The tick, and with it what the fields hold that the server does not have yet.
            Row(
                Modifier
                    .weight(1f)
                    .toggleable(
                        value = item.checked,
                        enabled = !readOnly && !busy,
                        role = Role.Checkbox,
                        onValueChange = { checked -> onChange(pending.copy(checked = checked)) },
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
                        color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (homeNow > 0 || boughtNow > 0) {
                        Text(
                            listOfNotNull(
                                "${stringResource(R.string.shopping_at_home)} ${amountLabel(homeNow, item.displayUnit)}".takeIf { homeNow > 0 },
                                "${stringResource(R.string.shopping_bought)} ${amountLabel(boughtNow, item.displayUnit)}".takeIf { boughtNow > 0 },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.pantryBestBefore?.takeIf { item.atHome > 0 }?.let { day ->
                        LabelChip(stringResource(R.string.shopping_best_before, formatDate(day)), modifier = Modifier.padding(top = 2.dp))
                    }
                    if (waiting) WaitingMark(Modifier.padding(top = 2.dp))
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                    Text(amountLabel(remaining, item.displayUnit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (homeNow > 0 || boughtNow > 0) {
                        Text(
                            stringResource(R.string.shopping_of_total, amountLabel(item.totalQuantity, item.displayUnit)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = { editing = !editing }, enabled = !readOnly) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.shopping_amounts_of, item.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The packs are counted by the server from what it knows is at home: while that is typed anew, they are not said.
        val packs = item.packs
        if (packs != null && item.packsStand && !editing) {
            val counted = packsText(packs)
            val said = stringResource(R.string.shopping_buy_packs, counted, item.name)
            AssistChip(
                onClick = { packsBought(item)?.let(onChange) },
                label = { Text(counted) },
                enabled = !readOnly && !busy,
                modifier = Modifier.padding(start = 52.dp).semantics { contentDescription = said },
            )
        }
        if (editing) {
            Column(Modifier.padding(start = 52.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(stringResource(R.string.shopping_at_home), home, item.displayUnit, enabled = !readOnly && !busy) { home = it }
                AmountField(stringResource(R.string.shopping_bought), bought, item.displayUnit, enabled = !readOnly && !busy) { bought = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            home = fieldOf(item.atHome, locale)
                            bought = fieldOf(boughtOf(item), locale)
                            editing = false
                        },
                    ) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = {
                            onChange(pending)
                            editing = false
                        },
                        enabled = !readOnly && !busy && !pending.isEmpty,
                    ) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }
}

/** A field of a row's amount, in the row's unit; a number under nothing is not taken, an empty field is. */
@Composable
private fun AmountField(label: String, value: String, unit: String, enabled: Boolean, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { typedNow -> if (typedNow.isBlank() || typedNow.trim().replace(',', '.').toDoubleOrNull()?.let { it >= 0 } == true) onValue(typedNow) },
        label = { Text(label) },
        suffix = { Text(unitName(unit)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A field's text for an amount: empty for none, the number in the language's own way otherwise, without grouping. */
private fun fieldOf(amount: Double, locale: Locale): String =
    if (amount <= 0.0) "" else NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false; maximumFractionDigits = 2 }.format(amount)
