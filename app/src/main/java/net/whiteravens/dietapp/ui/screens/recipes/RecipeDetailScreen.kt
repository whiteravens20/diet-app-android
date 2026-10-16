// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeSource
import net.whiteravens.dietapp.ui.common.MARK
import net.whiteravens.dietapp.ui.common.allergenLabel
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.cuisineLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.difficultyLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AiMark
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.LabelChip
import net.whiteravens.dietapp.ui.components.MacroLegend
import net.whiteravens.dietapp.ui.components.MacroRing
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.components.modelPartText
import net.whiteravens.dietapp.ui.screens.favorites.sentimentLabel
import kotlin.math.roundToInt

/** Full recipe: meta chips, how each profile marked it, per-serving nutrition, ingredients, steps. */
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

        item {
            RecipeBody(recipe) {
                Marks(state.marks, marking = state.marking, failed = state.markFailed, onToggle = viewModel::toggleMark)
            }
        }
    }
}

/**
 * Each profile of the account, as a chip that puts the recipe among its favourites or takes its mark
 * off, as the web's view has them; a mark other than a favourite is said by its name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Marks(
    marks: List<RecipeDetailViewModel.ProfileMark>,
    marking: String?,
    failed: RecipeDetailViewModel.MarkFailed?,
    onToggle: (profileId: String) -> Unit,
) {
    if (marks.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.recipe_save_to_favorites),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        marks.forEach { mark ->
            val sentiment = mark.sentiment
            FilterChip(
                selected = sentiment != null,
                onClick = { onToggle(mark.profile.id) },
                enabled = marking == null,
                label = { Text(if (sentiment == null || sentiment == "favorite") mark.profile.name else "${mark.profile.name} · ${sentimentLabel(sentiment)}") },
                leadingIcon = if (sentiment != null) {
                    { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
    failed?.let {
        val lead = stringResource(if (it.removing) R.string.favorites_err_remove else R.string.favorites_err_save)
        ErrorBanner("$lead ${errorMessage(it.error)}")
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.skeletonItems() {
    item { Skeleton(Modifier.fillMaxWidth().height(24.dp)) }
    item { Skeleton(Modifier.fillMaxWidth().height(180.dp)) }
    item { Skeleton(Modifier.fillMaxWidth().height(240.dp)) }
}

/**
 * The recipe as the web's view has it: a model's part in it, what it is, its difficulty, meals,
 * diets and cuisine, where it is from and under what licence, how the profiles marked it ([marks]),
 * what a serving holds, how many servings it makes and how long it takes, its lines, its method and
 * its allergens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeBody(recipe: Recipe, marks: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        recipe.byModel?.let { AiMark(modelPartText(it), spelledOut = true) }
        if (recipe.description.isNotBlank()) {
            Text(
                recipe.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabelChip(difficultyLabel(recipe.difficulty))
            recipe.mealTypes.forEach { LabelChip(mealTypeLabel(it)) }
            recipe.dietTags.forEach { LabelChip(dietTypeLabel(it)) }
            // The kitchen the recipe belongs to, for the recipes that belong to one.
            recipe.cuisine?.let { LabelChip(stringResource(R.string.recipe_cuisine, cuisineLabel(it))) }
        }
        recipe.source?.let { SourceLine(it) }
        marks()

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
            Text(
                pluralStringResource(R.plurals.recipe_serving_meta, recipe.servings, recipe.servings, formatNumber(recipe.prepMinutes), formatNumber(recipe.cookMinutes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
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
                        amountLabel(ingredient.quantity, ingredient.unit),
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

        if (recipe.allergens.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(stringResource(R.string.recipe_allergens)) }
                    append(" ")
                    append(recipe.allergens.map { allergenLabel(it) }.joinToString(", "))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Where a recipe taken in from elsewhere is from, and the licence it is passed on under: each a
 * link where it is an address of the web, which the browser opens; anything else is said as it is.
 */
@Composable
private fun SourceLine(source: RecipeSource) {
    val links = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val from = webAddress(source.url)
    val licence = webAddress(source.license)
    val fromLine = stringResource(R.string.recipe_source, MARK)
    val licenceLine = stringResource(R.string.recipe_source_licence, MARK)
    Text(
        buildAnnotatedString {
            if (from != null) {
                linked(fromLine, siteOf(from), from, links)
                append(" · ")
            }
            linked(licenceLine, licence?.let(::siteOf) ?: source.license, licence, links)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** [line] with [label] in the place of [MARK], a link to [url] where there is one. */
private fun AnnotatedString.Builder.linked(line: String, label: String, url: String?, style: TextLinkStyles) {
    val (head, tail) = line.split(MARK, limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    append(head)
    if (url != null) withLink(LinkAnnotation.Url(url, style)) { append(label) } else append(label)
    append(tail)
}
