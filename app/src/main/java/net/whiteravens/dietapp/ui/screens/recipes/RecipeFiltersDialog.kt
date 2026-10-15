// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.MealSlots
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.RECIPE_DIET_TAGS
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.ui.common.cuisineLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.difficultyLabel
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.screens.profiles.AllergenChoice
import net.whiteravens.dietapp.ui.screens.profiles.ProfileBounds

/**
 * Every way the library can be narrowed, as the web's page has them: the diet, the meal, the
 * difficulty, the energy and the time of a serving; behind "More filters" the cuisine, what a
 * serving holds, the ingredients every recipe holds and the allergens none holds; and what a
 * profile marked and favours. Nothing is asked of the API until the filters are applied.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecipeFiltersDialog(
    filter: RecipeFilter,
    profiles: List<Profile>,
    ingredientSearch: RecipesViewModel.IngredientSearch,
    onSearchIngredients: (String) -> Unit,
    onApply: (RecipeFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filter) { mutableStateOf(filter) }
    // The figures as typed, read when the filters are applied.
    var calories by rememberSaveable { mutableStateOf(filter.maxCalories?.toString().orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(filter.maxPrepMinutes?.toString().orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(filter.minProtein?.let(::plain).orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(filter.maxFat?.let(::plain).orEmpty()) }
    var carbs by rememberSaveable { mutableStateOf(filter.maxCarbs?.let(::plain).orEmpty()) }
    var more by rememberSaveable { mutableStateOf(filter.more > 0) }
    fun applied() = draft.copy(
        maxCalories = calories.trim().toIntOrNull()?.takeIf { it > 0 },
        maxPrepMinutes = minutes.trim().toIntOrNull()?.takeIf { it > 0 },
        minProtein = decimal(protein),
        maxFat = decimal(fat),
        maxCarbs = decimal(carbs),
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recipes_filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
                Choice(stringResource(R.string.recipes_diet_type), RECIPE_DIET_TAGS.toList(), draft.dietType, { dietTypeLabel(it) }) { draft = draft.copy(dietType = it) }
                Choice(stringResource(R.string.recipes_meal_type), MealSlots.BY_COUNT.getValue(5), draft.mealType, { mealTypeLabel(it) }) { draft = draft.copy(mealType = it) }
                Choice(stringResource(R.string.recipes_difficulty), DIFFICULTIES, draft.difficulty, { difficultyLabel(it) }) { draft = draft.copy(difficulty = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Figure(stringResource(R.string.recipes_max_calories), calories, whole = true, Modifier.weight(1f)) { calories = it }
                    Figure(stringResource(R.string.recipes_max_prep), minutes, whole = true, Modifier.weight(1f)) { minutes = it }
                }

                // The narrower ways to look stay folded until they are wanted; the count says when one of them is at work.
                val narrower = applied().more
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { more = !more }
                        .semantics { if (more) collapse { more = false; true } else expand { more = true; true } }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        if (narrower > 0) pluralStringResource(R.plurals.recipes_more_filters_on, narrower, narrower) else stringResource(R.string.recipes_more_filters),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(if (more) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                if (more) {
                    Choice(stringResource(R.string.recipes_cuisine), ProfileBounds.CUISINES, draft.cuisine, { cuisineLabel(it) }) { draft = draft.copy(cuisine = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Figure(stringResource(R.string.recipes_min_protein), protein, whole = false, Modifier.weight(1f)) { protein = it }
                        Figure(stringResource(R.string.recipes_max_fat), fat, whole = false, Modifier.weight(1f)) { fat = it }
                        Figure(stringResource(R.string.recipes_max_carbs), carbs, whole = false, Modifier.weight(1f)) { carbs = it }
                    }
                    Text(stringResource(R.string.recipes_per_serving), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IngredientChooser(
                        chosen = draft.ingredients,
                        search = ingredientSearch,
                        onSearch = onSearchIngredients,
                        onAdd = { draft = draft.copy(ingredients = (draft.ingredients + it).take(RecipeFilter.MAX_INGREDIENTS)) },
                        onRemove = { id -> draft = draft.copy(ingredients = draft.ingredients.filterNot { it.id == id }) },
                    )
                    AllergenChoice(
                        selected = draft.withoutAllergens,
                        onChange = { draft = draft.copy(withoutAllergens = it) },
                        title = stringResource(R.string.recipes_without_allergens),
                        hint = stringResource(R.string.recipes_without_allergens_hint),
                    )
                }

                // What a profile marked and favours, for the profile chosen where there is more than one.
                if (profiles.isNotEmpty()) {
                    if (profiles.size > 1) {
                        Choice(stringResource(R.string.recipes_favorites_profile), profiles.map { it.id }, draft.profileId, { id -> profiles.first { it.id == id }.name }, any = false) {
                            draft = draft.copy(profileId = it)
                        }
                    }
                    CheckRow(stringResource(R.string.recipes_uses_favorites), checked = draft.favoriteIngredients) { draft = draft.copy(favoriteIngredients = it) }
                    Choice(stringResource(R.string.recipes_marked), MARKS, draft.marked, { markLabel(it) }, anyLabel = stringResource(R.string.recipes_marked_any)) { draft = draft.copy(marked = it) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            draft = draft.cleared()
                            calories = ""
                            minutes = ""
                            protein = ""
                            fat = ""
                            carbs = ""
                        },
                    ) { Text(stringResource(R.string.recipes_clear_filters)) }
                    Button(onClick = { onApply(applied()) }) { Text(stringResource(R.string.recipes_filters_apply)) }
                }
            }
        }
    }
}

private val DIFFICULTIES = listOf("easy", "medium", "hard")
private val MARKS = listOf("marked", "often", "avoid")

@Composable
private fun markLabel(mark: String): String = when (mark) {
    "often" -> stringResource(R.string.recipes_marked_often)
    "avoid" -> stringResource(R.string.recipes_marked_avoid)
    else -> stringResource(R.string.recipes_marked_marked)
}

/** One of [options] under [title], or none ("Any", where [any]), each a chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(
    title: String,
    options: List<String>,
    selected: String?,
    label: @Composable (String) -> String,
    any: Boolean = true,
    anyLabel: String = stringResource(R.string.recipes_any),
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (any) FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(anyLabel) })
            options.forEach { option ->
                FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

/** A figure typed in: a whole number, or one with decimals; a sign under nothing is not taken. */
@Composable
private fun Figure(label: String, value: String, whole: Boolean, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> if (typed.isEmpty() || typed.all { it.isDigit() || (!whole && (it == '.' || it == ',')) }) onValue(typed) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier,
    )
}

/**
 * The ingredients every recipe is to hold: those chosen, each to take away, and a search of the
 * catalogue that suggests others once two letters are typed, up to [RecipeFilter.MAX_INGREDIENTS].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientChooser(
    chosen: List<IngredientChoice>,
    search: RecipesViewModel.IngredientSearch,
    onSearch: (String) -> Unit,
    onAdd: (IngredientChoice) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.recipes_with_ingredients), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.recipes_with_ingredients_hint, RecipeFilter.MAX_INGREDIENTS.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (chosen.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chosen.forEach { ingredient ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(ingredient.id) },
                        label = { Text(ingredient.name) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.pickers_remove, ingredient.name), modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
        if (chosen.size < RecipeFilter.MAX_INGREDIENTS) {
            OutlinedTextField(
                value = search.text,
                onValueChange = onSearch,
                placeholder = { Text(stringResource(R.string.pickers_ingredient_search)) },
                label = { Text(stringResource(R.string.recipes_with_ingredients)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            if (search.text.trim().length >= RecipesViewModel.SEARCH_FROM) {
                val available = search.found.filter { found -> chosen.none { it.id == found.id } }.take(20)
                if (available.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            available.forEach { ingredient ->
                                Text(
                                    ingredient.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAdd(ingredient) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                } else if (search.searched) {
                    Text(stringResource(R.string.pickers_no_matches), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A number typed with a comma or a point; nothing where it is not one, or not above nothing. */
private fun decimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

/** A figure as it is typed back into its field: `30`, `12.5`. */
private fun plain(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
