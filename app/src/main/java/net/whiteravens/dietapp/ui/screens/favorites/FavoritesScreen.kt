// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.FavoriteFilter
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.MEAL_TYPES
import net.whiteravens.dietapp.ui.common.WhenShownAgain
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatList
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.DOT
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.HowItWorks
import net.whiteravens.dietapp.ui.components.OfflineAsOf
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.RemovedBar
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.Text
import net.whiteravens.dietapp.ui.components.WaitingMark
import net.whiteravens.dietapp.ui.components.undoForMillis
import net.whiteravens.dietapp.ui.components.OutlinedField
import net.whiteravens.dietapp.ui.screens.recipes.ChipChoice

/**
 * The recipes a profile marked, to look through and keep in order, as the web's page has them: each
 * says how it is marked and under which tags of the profile's own, and both can be changed where
 * they are shown. A recipe opens on [onOpenRecipe]; the library is [onRecipes], the profiles [onProfiles].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onRecipes: () -> Unit,
    onProfiles: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val removed by viewModel.removedLater.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val undoFor = undoForMillis()
    // A mark taken off is not shown while it can be taken back, nor until the server has its removal.
    val shown = state.shownItems.filter { it.recipeId !in hidden }
    // Read again on being shown again: a recipe opened from the list may have been marked otherwise in its view.
    WhenShownAgain(viewModel::shownAgain)
    var filtering by rememberSaveable { mutableStateOf(false) }
    // The recipe whose mark is being changed in its window, by its id.
    var changing by rememberSaveable { mutableStateOf<String?>(null) }
    var howItWorks by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        PullToRead(state.pulled, viewModel::pull) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ScreenHeader(
                        title = stringResource(R.string.favorites_title),
                        subtitle = stringResource(R.string.favorites_subhead),
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
                            icon = Icons.Default.Star,
                            title = stringResource(R.string.favorites_no_profile),
                            description = stringResource(R.string.favorites_no_profile_body),
                            cta = { Button(onClick = onProfiles) { Text(stringResource(R.string.favorites_no_profile_cta)) } },
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
                        // The search and the filters once there is something to narrow: over nothing marked they ask for nothing.
                        // What a mark does to the plans stands behind "How it works".
                        if (!state.nothingMarked) {
                            item {
                                SearchAndFilters(
                                    query = query,
                                    onQuery = { viewModel.query.value = it.take(FavoriteFilter.SEARCH_LENGTH) },
                                    filter = state.filter,
                                    onFilters = { filtering = true },
                                    onClear = { viewModel.narrow(state.filter.cleared()) },
                                )
                            }
                            item { HowItWorks(stringResource(R.string.favorites_legend), howItWorks) { howItWorks = it } }
                        }
                        state.failed?.let { (what, error) ->
                            item {
                                val lead = stringResource(if (what == FavoritesViewModel.Failure.SAVE) R.string.favorites_err_save else R.string.favorites_err_remove)
                                ErrorBanner("$lead ${errorMessage(error)}", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                        }

                        when {
                            state.loading -> items(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp)) }

                            state.error != null -> item {
                                Column {
                                    ErrorBanner(errorMessage(state.error!!))
                                    Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                                        Text(stringResource(R.string.common_retry))
                                    }
                                }
                            }

                            shown.isEmpty() && state.filter.narrowed -> item {
                                Text(
                                    stringResource(R.string.favorites_none_filtered),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            shown.isEmpty() -> item {
                                EmptyState(
                                    icon = Icons.Default.Star,
                                    title = stringResource(R.string.favorites_empty_title),
                                    description = stringResource(R.string.favorites_empty_body),
                                    cta = { Button(onClick = onRecipes) { Text(stringResource(R.string.favorites_empty_cta)) } },
                                )
                            }

                            else -> {
                                item {
                                    Text(
                                        pluralStringResource(R.plurals.favorites_count, shown.size, shown.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                items(shown, key = { it.recipeId }) { kept ->
                                    MarkRow(
                                        item = state.shown(kept),
                                        waiting = kept.recipeId in state.waitingRecipes,
                                        onOpen = { onOpenRecipe(kept.recipeId) },
                                        onChange = { changing = kept.recipeId },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        removed?.let { removal ->
            RemovedBar(removal.item.title, viewModel::keepRemoved, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }

    if (filtering) {
        FavoriteFiltersDialog(
            filter = state.filter,
            tags = state.tags,
            onApply = { chosen ->
                viewModel.narrow(chosen)
                filtering = false
            },
            onDismiss = { filtering = false },
        )
    }
    // The window closes with its recipe: a mark taken off, or gone from the list.
    shown.firstOrNull { it.recipeId == changing }?.let { kept ->
        MarkDialog(
            item = state.shown(kept),
            busy = state.busy,
            onSentiment = { viewModel.setSentiment(kept, it) },
            onAddTag = { viewModel.addTag(kept, it) },
            onRemoveTag = { viewModel.removeTag(kept, it) },
            onRemove = {
                changing = null
                viewModel.remove(kept, undoFor)
            },
            onDismiss = { changing = null },
        )
    }
}

/** The words of a title to look for, and the button of the window that narrows by the rest, which counts those at work. */
@Composable
private fun SearchAndFilters(query: String, onQuery: (String) -> Unit, filter: FavoriteFilter, onFilters: () -> Unit, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedField(
            value = query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.favorites_search)) },
            placeholder = { Text(stringResource(R.string.favorites_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val atWork = filter.atWork
            OutlinedButton(onClick = onFilters) {
                Text(if (atWork > 0) pluralStringResource(R.plurals.recipes_filters_on, atWork, atWork) else stringResource(R.string.recipes_filters))
            }
            if (atWork > 0) TextButton(onClick = onClear) { Text(stringResource(R.string.recipes_clear_filters)) }
        }
    }
}

/** The meal, how a recipe is marked, a tag of the profile's and the order, as chips in a window, applied together. */
@Composable
private fun FavoriteFiltersDialog(filter: FavoriteFilter, tags: List<String>, onApply: (FavoriteFilter) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(filter) { mutableStateOf(filter) }
    val any = stringResource(R.string.favorites_any)
    ScreenDialog(onDismiss) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recipes_filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
            ChipChoice(stringResource(R.string.favorites_meal_type), MEAL_TYPES, draft.mealType, { mealTypeLabel(it) }, anyLabel = any) { draft = draft.copy(mealType = it) }
            ChipChoice(stringResource(R.string.favorites_marked), FavoriteFilter.SENTIMENTS, draft.sentiment, { sentimentLabel(it) }, anyLabel = any) { draft = draft.copy(sentiment = it) }
            if (tags.isNotEmpty()) {
                ChipChoice(stringResource(R.string.favorites_tag), tags, draft.tag, { it }, anyLabel = any) { draft = draft.copy(tag = it) }
            }
            ChipChoice(stringResource(R.string.favorites_sort), FavoriteFilter.SORTS, draft.sort, { sortLabel(it) }, any = false) { chosen ->
                chosen?.let { draft = draft.copy(sort = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { draft = draft.cleared() }) { Text(stringResource(R.string.recipes_clear_filters)) }
                Button(onClick = { onApply(draft) }) { Text(stringResource(R.string.recipes_filters_apply)) }
            }
        }
    }
}

/**
 * A marked recipe as a row: what it is, how it is marked and under which tags, a change of it [waiting] to be sent; a
 * press on its name opens it, and the button beside it opens the window where its mark is changed.
 */
@Composable
private fun MarkRow(item: MarkedRecipe, waiting: Boolean, onOpen: () -> Unit, onChange: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.favorites_facts, formatNumber(item.calories), formatList(item.mealTypes.map { mealTypeLabel(it) })),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        if (item.sentiment == "avoid") AppIcons.Block else Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        listOf(sentimentLabel(item.sentiment)).joinToString(DOT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (waiting) WaitingMark(Modifier.padding(top = 2.dp))
            }
            val changeLabel = stringResource(R.string.favorites_change_of, item.title)
            TextButton(onClick = onChange, modifier = Modifier.semantics { contentDescription = changeLabel }) {
                Text(stringResource(R.string.favorites_change))
            }
        }
    }
}

/**
 * The window of a marked recipe: how it is marked, its tags, and its mark to take off. Each change is sent as it is
 * made, as the list made them; the window stays open for the next, and closes with the mark taken off.
 */
@Composable
private fun MarkDialog(
    item: MarkedRecipe,
    busy: Boolean,
    onSentiment: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val markedAs = stringResource(R.string.favorites_marked_as, item.title)
                Column(Modifier.semantics { contentDescription = markedAs }) {
                    ChipChoice(stringResource(R.string.favorites_marked), FavoriteFilter.SENTIMENTS, item.sentiment, { sentimentLabel(it) }, any = false) { chosen ->
                        if (!busy && chosen != null && chosen != item.sentiment) onSentiment(chosen)
                    }
                }
                Tags(item, busy, onAddTag, onRemoveTag)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        dismissButton = {
            val removeLabel = stringResource(R.string.favorites_remove_of, item.title)
            TextButton(onClick = onRemove, enabled = !busy, modifier = Modifier.semantics { contentDescription = removeLabel }) {
                Text(stringResource(R.string.favorites_remove), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/** The tags of a marked recipe: each can be taken off, and another put on, up to the most a recipe holds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(item: MarkedRecipe, busy: Boolean, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var typed by rememberSaveable(item.recipeId) { mutableStateOf("") }
    val full = item.tags.size >= FavoriteFilter.MOST_TAGS
    val newTagLabel = stringResource(R.string.favorites_new_tag_of, item.title)
    val add = {
        onAdd(typed)
        typed = ""
    }
    if (item.tags.isNotEmpty()) {
        val tagsLabel = stringResource(R.string.favorites_tags_of, item.title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = tagsLabel },
        ) {
            item.tags.forEach { tag ->
                val removeTag = stringResource(R.string.favorites_remove_tag, tag)
                InputChip(
                    selected = false,
                    onClick = { onRemove(tag) },
                    enabled = !busy,
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize)) },
                    modifier = Modifier.semantics { contentDescription = removeTag },
                )
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        OutlinedField(
            value = typed,
            onValueChange = { typed = it },
            accept = { it.take(FavoriteFilter.TAG_LENGTH) },
            label = { Text(stringResource(R.string.favorites_new_tag)) },
            singleLine = true,
            enabled = !busy && !full,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { if (typed.isNotBlank()) add() },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f).semantics { contentDescription = newTagLabel },
        )
        OutlinedButton(onClick = add, enabled = !busy && !full && typed.isNotBlank()) {
            Text(stringResource(R.string.favorites_add_tag))
        }
    }
    if (full) {
        Text(
            pluralStringResource(R.plurals.favorites_tags_full, FavoriteFilter.MOST_TAGS, FavoriteFilter.MOST_TAGS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** How a recipe is marked, as the web's select says it. */
@Composable
fun sentimentLabel(sentiment: String): String = when (sentiment) {
    "often" -> stringResource(R.string.favorites_sentiment_often)
    "avoid" -> stringResource(R.string.favorites_sentiment_avoid)
    else -> stringResource(R.string.favorites_sentiment_favorite)
}

@Composable
private fun sortLabel(sort: String): String = when (sort) {
    "title" -> stringResource(R.string.favorites_sort_title)
    "calories" -> stringResource(R.string.favorites_sort_calories)
    else -> stringResource(R.string.favorites_sort_newest)
}
