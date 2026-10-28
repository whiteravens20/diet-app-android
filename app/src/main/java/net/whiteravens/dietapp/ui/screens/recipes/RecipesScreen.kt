// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.ui.common.WhenShownAgain
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.difficultyLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.components.AiMark
import net.whiteravens.dietapp.ui.components.AiOffNote
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.OfflineAsOf
import net.whiteravens.dietapp.ui.components.PullToRead
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.modelPartText
import kotlin.math.roundToInt

/**
 * The recipe library: the shared recipes and the account's own, searched by words and narrowed by
 * every filter the API takes in a window of their own, scrolled without end; the account's own
 * recipes in a tab of their own, each to be suggested for the shared library or deleted; and a
 * recipe of one's own drafted by a model, which opens once it is written.
 */
@Composable
fun RecipesScreen(
    onOpenRecipe: (String) -> Unit,
    onTurnAiOn: () -> Unit,
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read again on being shown again: a recipe may have been marked, made or changed in the meantime.
    WhenShownAgain(viewModel::shownAgain)
    val query by viewModel.query.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var filtering by rememberSaveable { mutableStateOf(false) }
    var drafting by rememberSaveable { mutableStateOf(false) }
    // A recipe of one's own waiting for a yes: to be suggested for the shared library, or deleted.
    var submitting by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    // Fetch the next page when the scroll approaches the end of the list.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }

    if (filtering) {
        RecipeFiltersDialog(
            filter = state.filter,
            profiles = state.profiles,
            ingredientSearch = state.ingredientSearch,
            onSearchIngredients = viewModel::searchIngredients,
            onApply = { filter ->
                filtering = false
                viewModel.narrow(filter)
            },
            onDismiss = { filtering = false },
        )
    }
    if (drafting) {
        RecipeDraftDialog(
            onDrafted = { id ->
                drafting = false
                viewModel.retry()
                onOpenRecipe(id)
            },
            onDismiss = { drafting = false },
        )
    }
    submitting?.let { id ->
        Confirm(stringResource(R.string.recipes_submit_confirm), stringResource(R.string.recipes_submit), onYes = { viewModel.submit(id) }, onDone = { submitting = null })
    }
    deleting?.let { id ->
        Confirm(stringResource(R.string.recipes_delete_confirm), stringResource(R.string.common_delete), onYes = { viewModel.delete(id) }, onDone = { deleting = null })
    }

    val mine = state.tab == RecipesViewModel.Tab.MINE
    PullToRead(state.pulled, viewModel::pull) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.nav_recipes),
                    subtitle = stringResource(R.string.recipes_subtitle),
                )
            }
            state.readAt?.takeIf { state.offline }?.let { readAt -> item { OfflineAsOf(readAt) } }
            // A recipe drafted by a model is of a profile: there is none to draft for without one.
            if (state.profiles.isNotEmpty()) item { DraftButton(aiOn = state.aiOn, onOpen = { drafting = true }, onTurnAiOn = onTurnAiOn) }
            item {
                TabRow(selectedTabIndex = state.tab.ordinal) {
                    Tab(selected = !mine, onClick = { viewModel.selectTab(RecipesViewModel.Tab.LIBRARY) }, text = { Text(stringResource(R.string.recipes_tab_library)) })
                    Tab(selected = mine, onClick = { viewModel.selectTab(RecipesViewModel.Tab.MINE) }, text = { Text(stringResource(R.string.recipes_tab_mine)) })
                }
            }
            if (!mine) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.query.value = it },
                        placeholder = { Text(stringResource(R.string.recipes_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val atWork = state.filter.atWork
                        OutlinedButton(onClick = { filtering = true }) {
                            Text(if (atWork > 0) pluralStringResource(R.plurals.recipes_filters_on, atWork, atWork) else stringResource(R.string.recipes_filters))
                        }
                        if (state.filter.narrowed) TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.recipes_clear_filters)) }
                    }
                }
            }
            state.submitted?.let { sent ->
                item {
                    Text(
                        sent.exceptionOrNull()?.let { stringResource(R.string.recipes_submit_failed) + " " + errorMessage(it) } ?: stringResource(R.string.recipes_submit_done),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sent.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            when {
                state.loading -> items(4) { Skeleton(Modifier.fillMaxWidth().height(110.dp)) }

                state.error != null && state.items.isEmpty() -> item {
                    Column {
                        ErrorBanner(errorMessage(state.error!!))
                        Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }

                state.items.isEmpty() && mine -> item {
                    EmptyState(
                        icon = AppIcons.Restaurant,
                        title = stringResource(R.string.recipes_mine_empty_title),
                        description = stringResource(R.string.recipes_mine_empty),
                    )
                }

                state.items.isEmpty() -> item {
                    Text(
                        stringResource(
                            when {
                                state.filter.favoriteIngredients -> R.string.recipes_none_favorites
                                state.filter.narrowed || state.filter.search.isNotBlank() -> R.string.recipes_none_filtered
                                else -> R.string.recipes_none_at_all
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    state.error?.let { item { ErrorBanner(errorMessage(it)) } }
                    items(state.items, key = { it.id }) { recipe ->
                        RecipeCard(
                            recipe,
                            onClick = { onOpenRecipe(recipe.id) },
                            mine = mine,
                            busy = state.changing != null,
                            onSubmit = { submitting = recipe.id },
                            onDelete = { deleting = recipe.id },
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A recipe of the list: what it is, what a serving holds and takes, a model's part, its diets; and, of one's own, what can be done with it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit, mine: Boolean, busy: Boolean, onSubmit: () -> Unit, onDelete: () -> Unit) {
    AppCard(onClick = onClick) {
        Text(recipe.title, style = MaterialTheme.typography.titleMedium)
        if (recipe.description.isNotBlank()) {
            Text(
                recipe.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            stringResource(
                R.string.recipes_card_meta,
                formatNumber(recipe.nutritionPerServing.calories.roundToInt()),
                difficultyLabel(recipe.difficulty),
                formatNumber(recipe.totalMinutes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        FlowRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            recipe.byModel?.let { AiMark(modelPartText(it)) }
            recipe.dietTags.forEach { LabelChip(dietTypeLabel(it)) }
        }
        if (mine) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!recipe.fromSwap && !recipe.submitted) TextButton(onClick = onSubmit, enabled = !busy) { Text(stringResource(R.string.recipes_submit)) }
                TextButton(onClick = onDelete, enabled = !busy) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/**
 * The button that opens the draft of a recipe by a model. Where the account has no model to ask, it
 * stays in its place, off, over a line that says what it waits for.
 */
@Composable
private fun DraftButton(aiOn: Boolean?, onOpen: () -> Unit, onTurnAiOn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onOpen, enabled = aiOn == true) { Text(stringResource(R.string.recipes_ai_draft)) }
        if (aiOn == false) AiOffNote(onTurnAiOn)
    }
}

/** A question to say yes to before [onYes] is done; [onDone] closes it either way. */
@Composable
private fun Confirm(question: String, yes: String, onYes: () -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        text = { Text(question) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDone()
                    onYes()
                },
            ) { Text(yes) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.common_cancel)) } },
    )
}
