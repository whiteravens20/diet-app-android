package net.whiteravens.dietapp.ui.screens.recipes

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.ui.common.difficultyLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.AppIcons
import net.whiteravens.dietapp.ui.components.EmptyState
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import kotlin.math.roundToInt

/** Searchable, endlessly scrolling recipe catalogue. */
@Composable
fun RecipesScreen(
    onOpenRecipe: (String) -> Unit,
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_recipes),
                subtitle = stringResource(R.string.recipes_subtitle),
            )
        }
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

        when {
            state.loading -> items(4) { Skeleton(Modifier.fillMaxWidth().height(110.dp)) }

            state.error != null -> item {
                Column {
                    ErrorBanner(errorMessage(state.error!!))
                    Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }

            state.items.isEmpty() -> item {
                EmptyState(
                    icon = AppIcons.Restaurant,
                    title = stringResource(R.string.recipes_empty),
                    description = stringResource(R.string.recipes_empty_body),
                )
            }

            else -> {
                items(state.items, key = { it.id }) { recipe ->
                    RecipeCard(recipe, onClick = { onOpenRecipe(recipe.id) })
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

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
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
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelChip(
                stringResource(
                    R.string.recipes_kcal_per_serving,
                    recipe.nutritionPerServing.calories.roundToInt(),
                ),
            )
            LabelChip(stringResource(R.string.recipes_minutes, recipe.totalMinutes))
            LabelChip(difficultyLabel(recipe.difficulty))
            recipe.mealTypes.firstOrNull()?.let { LabelChip(mealTypeLabel(it)) }
        }
    }
}
