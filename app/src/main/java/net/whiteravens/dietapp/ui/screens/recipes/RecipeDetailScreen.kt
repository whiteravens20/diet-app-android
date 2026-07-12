package net.whiteravens.dietapp.ui.screens.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.ui.common.difficultyLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatQuantity
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.MacroLegend
import net.whiteravens.dietapp.ui.components.MacroRing
import net.whiteravens.dietapp.ui.components.Skeleton
import kotlin.math.roundToInt

/** Full recipe: meta chips, per-serving nutrition, ingredients, steps. */
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipe = state.recipe

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Text(
                    recipe?.title ?: stringResource(R.string.nav_recipes),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        state.error?.let {
            item {
                Column {
                    ErrorBanner(errorMessage(it))
                    Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
        }

        if (recipe == null) {
            if (state.loading) skeletonItems()
            return@LazyColumn
        }

        item { RecipeBody(recipe) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.skeletonItems() {
    item { Skeleton(Modifier.fillMaxWidth().height(24.dp)) }
    item { Skeleton(Modifier.fillMaxWidth().height(180.dp)) }
    item { Skeleton(Modifier.fillMaxWidth().height(240.dp)) }
}

@Composable
private fun RecipeBody(recipe: Recipe) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recipe.description.isNotBlank()) {
            Text(
                recipe.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LabelChip(stringResource(R.string.recipes_minutes, recipe.totalMinutes))
            LabelChip(difficultyLabel(recipe.difficulty))
            LabelChip(stringResource(R.string.recipes_servings, recipe.servings))
            recipe.mealTypes.firstOrNull()?.let { LabelChip(mealTypeLabel(it)) }
        }

        AppCard {
            Text(
                stringResource(R.string.recipes_nutrition_per_serving),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MacroRing(
                    nutrition = recipe.nutritionPerServing,
                    centerLabel = stringResource(R.string.unit_kcal),
                    size = 130.dp,
                    strokeWidth = 14.dp,
                )
                MacroLegend(
                    labels = listOf(
                        stringResource(R.string.macro_protein),
                        stringResource(R.string.macro_fat),
                        stringResource(R.string.macro_carbs),
                    ),
                    grams = listOf(
                        recipe.nutritionPerServing.protein,
                        recipe.nutritionPerServing.fat,
                        recipe.nutritionPerServing.carbs,
                    ),
                )
            }
        }

        AppCard {
            Text(
                stringResource(R.string.recipes_ingredients),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            recipe.ingredients.forEachIndexed { index, ingredient ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ingredient.name, style = MaterialTheme.typography.bodyMedium)
                        ingredient.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "${formatQuantity(ingredient.quantity)} ${unitLabel(ingredient.unit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AppCard {
            Text(
                stringResource(R.string.recipes_steps),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            recipe.steps.forEachIndexed { index, step ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun unitLabel(unit: String): String = when (unit) {
    "piece" -> stringResource(R.string.unit_piece)
    else -> unit // g / ml are universal
}
