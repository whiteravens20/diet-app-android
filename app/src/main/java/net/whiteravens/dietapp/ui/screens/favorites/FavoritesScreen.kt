// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.favorites

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.OfflineAsOf
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.WaitingMark
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField

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
    // Read again on being shown again: a recipe opened from the list may have been marked otherwise in its view.
    WhenShownAgain(viewModel::shownAgain)

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
                    item {
                        Filters(
                            query = query,
                            onQuery = { viewModel.query.value = it.take(FavoriteFilter.SEARCH_LENGTH) },
                            filter = state.filter,
                            tags = state.tags,
                            onNarrow = viewModel::narrow,
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.favorites_legend),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

                        state.shownItems.isEmpty() && state.filter.narrowed -> item {
                            Text(
                                stringResource(R.string.favorites_none_filtered),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        state.shownItems.isEmpty() -> item {
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
                                    pluralStringResource(R.plurals.favorites_count, state.shownItems.size, state.shownItems.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(state.shownItems, key = { it.recipeId }) { kept ->
                                MarkCard(
                                    item = state.shown(kept),
                                    waiting = kept.recipeId in state.waitingRecipes,
                                    busy = state.busy,
                                    onOpen = { onOpenRecipe(kept.recipeId) },
                                    onSentiment = { viewModel.setSentiment(kept, it) },
                                    onAddTag = { viewModel.addTag(kept, it) },
                                    onRemoveTag = { viewModel.removeTag(kept, it) },
                                    onRemove = { viewModel.remove(kept) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The words of a title, the meal, how a recipe is marked, a tag of the profile's and the order, as the web's fields are. */
@Composable
private fun Filters(query: String, onQuery: (String) -> Unit, filter: FavoriteFilter, tags: List<String>, onNarrow: (FavoriteFilter) -> Unit) {
    val any = stringResource(R.string.favorites_any)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.favorites_search)) },
            placeholder = { Text(stringResource(R.string.favorites_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        ChoiceField(
            label = stringResource(R.string.favorites_meal_type),
            options = listOf(null) + MEAL_TYPES,
            selected = filter.mealType,
            optionLabel = { meal -> meal?.let { mealTypeLabel(it) } ?: any },
            onSelect = { onNarrow(filter.copy(mealType = it)) },
        )
        ChoiceField(
            label = stringResource(R.string.favorites_marked),
            options = listOf(null) + FavoriteFilter.SENTIMENTS,
            selected = filter.sentiment,
            optionLabel = { sentiment -> sentiment?.let { sentimentLabel(it) } ?: any },
            onSelect = { onNarrow(filter.copy(sentiment = it)) },
        )
        ChoiceField(
            label = stringResource(R.string.favorites_tag),
            options = listOf(null) + tags,
            selected = filter.tag,
            optionLabel = { tag -> tag ?: any },
            onSelect = { onNarrow(filter.copy(tag = it)) },
        )
        ChoiceField(
            label = stringResource(R.string.favorites_sort),
            options = FavoriteFilter.SORTS,
            selected = filter.sort,
            optionLabel = { sortLabel(it) },
            onSelect = { onNarrow(filter.copy(sort = it)) },
        )
    }
}

/**
 * A marked recipe: what it is, how it is marked and under which tags, each to change, and its mark
 * to take off; a change of it [waiting] to be sent says so.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkCard(
    item: MarkedRecipe,
    waiting: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onSentiment: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onRemove: () -> Unit,
) {
    AppCard {
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
        )
        Text(
            stringResource(R.string.favorites_facts, formatNumber(item.calories), formatList(item.mealTypes.map { mealTypeLabel(it) })),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (waiting) WaitingMark(Modifier.padding(top = 4.dp))
        val removeLabel = stringResource(R.string.favorites_remove_of, item.title)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            ChoiceField(
                label = stringResource(R.string.favorites_marked),
                options = FavoriteFilter.SENTIMENTS,
                selected = item.sentiment,
                optionLabel = { sentimentLabel(it) },
                onSelect = onSentiment,
                enabled = !busy,
                description = stringResource(R.string.favorites_marked_as, item.title),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove, enabled = !busy, modifier = Modifier.semantics { contentDescription = removeLabel }) {
                Text(stringResource(R.string.favorites_remove), color = MaterialTheme.colorScheme.error)
            }
        }
        Tags(item, busy, onAddTag, onRemoveTag)
    }
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
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it.take(FavoriteFilter.TAG_LENGTH) },
            label = { Text(stringResource(R.string.favorites_new_tag)) },
            singleLine = true,
            enabled = !busy && !full,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) add() }),
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
