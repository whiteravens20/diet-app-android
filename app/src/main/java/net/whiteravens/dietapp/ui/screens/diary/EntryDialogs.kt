// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.DiaryIngredient
import net.whiteravens.dietapp.domain.MEAL_TYPES
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.ui.common.Bound
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.common.numberProblem
import net.whiteravens.dietapp.ui.common.unitName
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import java.io.IOException
import kotlin.math.roundToInt

/** What a field of the open window says: what it holds that the API would refuse, once it was sent, or why the API did. */
@Composable
private fun fieldProblem(
    field: EntryField,
    problems: Map<EntryField, EntryProblem>,
    state: DiaryViewModel.UiState,
    bound: Bound = Bound.Number(decimals = 2),
): String? {
    val problem = problems[field].takeIf { state.showProblems }
    val refused = (problem as? EntryProblem.OutOfBounds)?.refusal ?: state.refused[field.path]
    return numberProblem(problem == EntryProblem.NotANumber, refused, bound, whole = false)
}

/**
 * The window an entry is added to a day of the diary in, as the web's: a recipe in servings, an
 * ingredient in an amount of a unit it can be given in, or a meal of one's own by its figures, those
 * of the macronutrients left empty estimated by the API. What the entry adds to the day is said before
 * it is added where the window knows it. What cannot be looked for without a network says so, and
 * leads to what can be written without one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddEntryDialog(form: EntryForm, state: DiaryViewModel.UiState, viewModel: DiaryViewModel) {
    val problems = form.problems()
    // A window over the whole screen: the form is long, and an open keyboard leaves room for little of it.
    ScreenDialog(viewModel::closeWindow) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(stringResource(R.string.diary_add_title), style = MaterialTheme.typography.titleLarge)
                Text(formatDayDate(state.date.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip(EntryKind.RECIPE, R.string.diary_kind_recipe, form, viewModel)
                    KindChip(EntryKind.INGREDIENT, R.string.diary_kind_ingredient, form, viewModel)
                    KindChip(EntryKind.MEAL, R.string.diary_kind_meal, form, viewModel)
                }
                ChoiceField(
                    label = stringResource(R.string.diary_meal_label),
                    options = MEAL_TYPES,
                    selected = form.slot,
                    optionLabel = { mealTypeLabel(it) },
                    onSelect = viewModel::chooseSlot,
                )
                when (form.kind) {
                    EntryKind.RECIPE -> {
                        val recipe = form.recipe
                        if (recipe == null) {
                            Search(stringResource(R.string.diary_search_recipe), form, viewModel) {
                                form.recipes.forEach { found -> Found(found.title, perServing(found)) { viewModel.pickRecipe(found) } }
                            }
                        } else {
                            Picked(recipe.title, perServing(recipe), viewModel::unpick)
                            ServingsField(form, problems, state, viewModel)
                        }
                    }
                    EntryKind.INGREDIENT -> {
                        val ingredient = form.ingredient
                        if (ingredient == null) {
                            Search(stringResource(R.string.diary_search_ingredient), form, viewModel) {
                                form.ingredients.forEach { found -> Found(found.name, per100(found)) { viewModel.pickIngredient(found) } }
                            }
                        } else {
                            Picked(ingredient.name, per100(ingredient), viewModel::unpick)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                FormField(
                                    value = form.quantity,
                                    onValueChange = { typed -> viewModel.typeEntry(EntryField.QUANTITY) { it.copy(quantity = typed) } },
                                    label = stringResource(R.string.diary_quantity),
                                    problem = fieldProblem(EntryField.QUANTITY, problems, state),
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f),
                                )
                                // Only what the ingredient converts from: the API refuses any other unit.
                                ChoiceField(
                                    label = stringResource(R.string.diary_unit),
                                    options = ingredient.units,
                                    selected = form.unit,
                                    optionLabel = { unit -> unitName(if (unit == "piece") ingredient.pieceName else unit) },
                                    onSelect = { unit -> viewModel.typeEntry(EntryField.QUANTITY) { it.copy(unit = unit) } },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    EntryKind.MEAL -> MealFields(form, problems, state, viewModel)
                }
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    form.adds()?.let { Text(stringResource(R.string.diary_adds_line, formatNumber(it)), style = MaterialTheme.typography.bodySmall) }
                }
                state.windowError?.let { ErrorBanner(errorMessage(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val missing = when {
                    form.kind == EntryKind.RECIPE && form.recipe == null -> R.string.diary_pick_recipe_first
                    form.kind == EntryKind.INGREDIENT && form.ingredient == null -> R.string.diary_pick_ingredient_first
                    else -> null
                }
                Column(Modifier.weight(1f)) { missing?.let { Note(stringResource(it)) } }
                TextButton(onClick = viewModel::closeWindow) { Text(stringResource(R.string.common_cancel)) }
                Button(onClick = viewModel::addEntry, enabled = !state.sending && missing == null) { Text(stringResource(R.string.diary_submit)) }
            }
        }
    }
}

@Composable
private fun KindChip(kind: EntryKind, label: Int, form: EntryForm, viewModel: DiaryViewModel) {
    FilterChip(selected = form.kind == kind, onClick = { viewModel.chooseKind(kind) }, label = { Text(stringResource(label)) })
}

@Composable
private fun perServing(recipe: Recipe) = stringResource(R.string.diary_per_serving, formatNumber(recipe.nutritionPerServing.calories.roundToInt()))

/** What 100 of an ingredient's own unit hold, where the catalogue says; nothing where it does not. */
@Composable
private fun per100(ingredient: DiaryIngredient): String? =
    ingredient.caloriesPer100?.let { stringResource(R.string.diary_per_100, formatNumber(it.roundToInt()), unitName(ingredient.canonicalUnit)) }

@Composable
private fun ServingsField(form: EntryForm, problems: Map<EntryField, EntryProblem>, state: DiaryViewModel.UiState, viewModel: DiaryViewModel) {
    FormField(
        value = form.servings,
        onValueChange = { typed -> viewModel.typeEntry(EntryField.SERVINGS) { it.copy(servings = typed) } },
        label = stringResource(R.string.diary_servings_field),
        problem = fieldProblem(EntryField.SERVINGS, problems, state),
        keyboardType = KeyboardType.Decimal,
    )
}

/** A meal of one's own: its name, what a serving holds, those of its macronutrients that are known, and how many servings it is. */
@Composable
private fun MealFields(form: EntryForm, problems: Map<EntryField, EntryProblem>, state: DiaryViewModel.UiState, viewModel: DiaryViewModel) {
    FormField(
        value = form.name,
        onValueChange = { typed -> viewModel.typeEntry(EntryField.NAME) { it.copy(name = typed) } },
        label = stringResource(R.string.diary_meal_name),
        problem = fieldProblem(EntryField.NAME, problems, state, Bound.Characters),
        keyboardType = KeyboardType.Text,
        capitalization = KeyboardCapitalization.Sentences,
    )
    Text(stringResource(R.string.diary_one_portion), style = MaterialTheme.typography.titleSmall)
    FormField(
        value = form.calories,
        onValueChange = { typed -> viewModel.typeEntry(EntryField.CALORIES) { it.copy(calories = typed) } },
        label = stringResource(R.string.diary_calories),
        problem = fieldProblem(EntryField.CALORIES, problems, state),
        keyboardType = KeyboardType.Decimal,
    )
    FormField(
        value = form.protein,
        onValueChange = { typed -> viewModel.typeEntry(EntryField.PROTEIN) { it.copy(protein = typed) } },
        label = stringResource(R.string.diary_protein),
        problem = fieldProblem(EntryField.PROTEIN, problems, state),
        keyboardType = KeyboardType.Decimal,
    )
    FormField(
        value = form.fat,
        onValueChange = { typed -> viewModel.typeEntry(EntryField.FAT) { it.copy(fat = typed) } },
        label = stringResource(R.string.diary_fat),
        problem = fieldProblem(EntryField.FAT, problems, state),
        keyboardType = KeyboardType.Decimal,
    )
    FormField(
        value = form.carbs,
        onValueChange = { typed -> viewModel.typeEntry(EntryField.CARBS) { it.copy(carbs = typed) } },
        label = stringResource(R.string.diary_carbs),
        problem = fieldProblem(EntryField.CARBS, problems, state),
        keyboardType = KeyboardType.Decimal,
    )
    Note(stringResource(R.string.diary_macros_optional))
    ServingsField(form, problems, state, viewModel)
}

/** What was picked, and a way back to the search. */
@Composable
private fun Picked(name: String, about: String?, onAgain: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            about?.let { Note(it) }
        }
        TextButton(onClick = onAgain) { Text(stringResource(R.string.diary_change_pick)) }
    }
}

/**
 * A search of what the window offers, and what it found to pick. Only the search in the field is said
 * anything of. One that got no answer says nothing of what there is; for want of a network it says
 * what can be written without one, and leads to it.
 */
@Composable
private fun Search(label: String, form: EntryForm, viewModel: DiaryViewModel, found: @Composable () -> Unit) {
    OutlinedTextField(
        value = form.search,
        onValueChange = viewModel::search,
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.diary_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
    val searching = form.search.trim().length >= ProfilesViewModel.SEARCH_FROM
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            !searching || !form.searched -> Unit
            form.searchFailed is IOException -> {
                Note(stringResource(R.string.diary_search_offline))
                TextButton(onClick = { viewModel.chooseKind(EntryKind.MEAL) }) { Text(stringResource(R.string.diary_kind_meal)) }
            }
            form.searchFailed != null -> Text(stringResource(R.string.diary_search_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            form.recipes.isEmpty() && form.ingredients.isEmpty() -> Note(stringResource(R.string.diary_no_results))
            else -> Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column { found() }
            }
        }
    }
}

/** Something found, to be picked. */
@Composable
private fun Found(name: String, about: String?, onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        about?.let { Note(it) }
    }
}

/**
 * The window an entry of the diary is changed in: the meal it is under, and how much of it there was,
 * in servings or in the amount of its ingredient as it was given. The API changes its figures in
 * proportion. The meal's own figures are written anew: an entry is taken out and written again.
 */
@Composable
internal fun EditEntryDialog(edit: EntryEdit, state: DiaryViewModel.UiState, viewModel: DiaryViewModel) {
    val problems = edit.problems()
    ScreenDialog(viewModel::closeWindow) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(stringResource(R.string.diary_edit_title), style = MaterialTheme.typography.titleLarge)
                Text(edit.entry.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField(
                    label = stringResource(R.string.diary_meal_label),
                    options = MEAL_TYPES,
                    selected = edit.slot,
                    optionLabel = { mealTypeLabel(it) },
                    onSelect = { slot -> viewModel.typeEdit { it.copy(slot = slot) } },
                )
                val amount = edit.entry.amount
                if (amount != null) {
                    // The amount as it was given, in the unit it was given in.
                    FormField(
                        value = edit.quantity,
                        onValueChange = { typed -> viewModel.typeEdit { it.copy(quantity = typed) } },
                        label = "${stringResource(R.string.diary_quantity)} (${unitName(amount.shownUnit)})",
                        problem = fieldProblem(EntryField.QUANTITY, problems, state),
                        keyboardType = KeyboardType.Decimal,
                    )
                } else {
                    FormField(
                        value = edit.servings,
                        onValueChange = { typed -> viewModel.typeEdit { it.copy(servings = typed) } },
                        label = stringResource(R.string.diary_servings_field),
                        problem = fieldProblem(EntryField.SERVINGS, problems, state),
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                state.windowError?.let { ErrorBanner(errorMessage(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::closeWindow) { Text(stringResource(R.string.common_cancel)) }
                Button(onClick = viewModel::saveEdit, enabled = !state.sending) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}
