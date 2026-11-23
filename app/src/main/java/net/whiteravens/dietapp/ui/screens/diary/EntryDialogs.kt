// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import java.time.LocalDate
import kotlin.math.roundToInt
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.DiaryIngredient
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.MEAL_TYPES
import net.whiteravens.dietapp.domain.ProductSource
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.ui.common.Bound
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.common.numberProblem
import net.whiteravens.dietapp.ui.common.unitName
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.OutlinedField
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.components.Text
import net.whiteravens.dietapp.ui.screens.products.OwnProductDialog
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel

/** What a field of the open window says: what it holds that the API would refuse, once it was sent, or why the API did. */
@Composable
private fun fieldProblem(
    field: EntryField,
    problems: Map<EntryField, EntryProblem>,
    window: EntryWindowState,
    bound: Bound = Bound.Number(decimals = 2),
): String? {
    val problem = problems[field].takeIf { window.showProblems }
    val refused = (problem as? EntryProblem.OutOfBounds)?.refusal ?: window.refused[field.path]
    return numberProblem(problem == EntryProblem.NotANumber, refused, bound, whole = false)
}

/**
 * The window an entry is added to a day of the diary in, as the web's: a recipe in servings, an
 * ingredient in an amount of a unit it can be given in, a product with a barcode where the instance
 * offers them, or a meal of one's own by its figures, those of the macronutrients left empty estimated
 * by the API. What the entry adds to the day is said before
 * it is added where the window knows it. What cannot be looked for without a network says so, and
 * leads to what can be written without one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddEntryDialog(form: EntryForm, date: LocalDate, window: EntryWindowState, host: EntryWindow) {
    val problems = form.problems()
    val rules by host.rules.collectAsStateWithLifecycle()
    // A window over the whole screen: the form is long, and an open keyboard leaves room for little of it.
    ScreenDialog(host.close) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(stringResource(R.string.diary_add_title), style = MaterialTheme.typography.titleLarge)
                Text(formatDayDate(date.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip(EntryKind.RECIPE, R.string.diary_kind_recipe, form, host)
                    KindChip(EntryKind.INGREDIENT, R.string.diary_kind_ingredient, form, host)
                    if (rules?.productsOffered == true) KindChip(EntryKind.PRODUCT, R.string.diary_kind_product, form, host)
                    KindChip(EntryKind.MEAL, R.string.diary_kind_meal, form, host)
                }
                ChoiceField(
                    label = stringResource(R.string.diary_meal_label),
                    options = MEAL_TYPES,
                    selected = form.slot,
                    optionLabel = { mealTypeLabel(it) },
                    onSelect = host::chooseSlot,
                )
                if (form.kind == EntryKind.MEAL) MealFields(form, problems, window, host) else EntryPick(form, problems, window, host, rules)
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    form.adds()?.let { Text(stringResource(R.string.diary_adds_line, formatNumber(it)), style = MaterialTheme.typography.bodySmall) }
                }
                window.windowError?.let { ErrorBanner(errorMessage(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
            // What is missing stands over the buttons, not squeezed beside them: with a large font the buttons take
            // the width, and a note beside them broke a letter a line and pushed the whole form out of the window.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val missing = missingOf(form)
                missing?.let { Note(stringResource(it)) }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = host.close) { Text(stringResource(R.string.common_cancel)) }
                    Button(onClick = host.add, enabled = !window.sending && missing == null) { Text(stringResource(R.string.diary_submit)) }
                }
            }
        }
    }
}

/**
 * Where the window picks a recipe, an ingredient or a product, and how much of what was picked: the diary's window and
 * a plan's day's alike. A meal of one's own is its owner's to ask for.
 */
@Composable
internal fun EntryPick(form: EntryForm, problems: Map<EntryField, EntryProblem>, window: EntryWindowState, host: EntryWindow, rules: InstanceRules?) {
    when (form.kind) {
        EntryKind.RECIPE -> {
            val recipe = form.recipe
            if (recipe == null) {
                Search(stringResource(R.string.diary_search_recipe), form, host) {
                    form.recipes.forEach { found -> Found(found.title, perServing(found)) { host.pickRecipe(found) } }
                }
            } else {
                Picked(recipe.title, perServing(recipe), host::unpick)
                ServingsField(form, problems, window, host)
            }
        }
        EntryKind.INGREDIENT -> {
            val ingredient = form.ingredient
            if (ingredient == null) {
                Search(stringResource(R.string.diary_search_ingredient), form, host) {
                    form.ingredients.forEach { found -> Found(found.name, per100(found)) { host.pickIngredient(found) } }
                }
            } else {
                Picked(ingredient.name, per100(ingredient), host::unpick)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    FormField(
                        value = form.quantity,
                        onValueChange = { typed -> host.typeEntry(EntryField.QUANTITY) { it.copy(quantity = typed) } },
                        label = stringResource(R.string.diary_quantity),
                        problem = fieldProblem(EntryField.QUANTITY, problems, window),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    // Only what the ingredient converts from: the API refuses any other unit.
                    ChoiceField(
                        label = stringResource(R.string.diary_unit),
                        options = ingredient.units,
                        selected = form.unit,
                        optionLabel = { unit -> unitName(if (unit == "piece") ingredient.pieceName else unit) },
                        onSelect = { unit -> host.typeEntry(EntryField.QUANTITY) { it.copy(unit = unit) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        EntryKind.PRODUCT -> {
            val product = form.product
            if (product == null) {
                ProductSearch(form, rules?.productLookups == true, host)
            } else {
                PickedProduct(product, host)
                if (product.calories != null) ProductAmount(product, form, fieldProblem(EntryField.PRODUCT_QUANTITY, problems, window), host)
            }
        }
        EntryKind.MEAL -> Unit
    }
}

/** What the window still needs before what it holds can be added, said over its buttons; null where it needs nothing. */
internal fun missingOf(form: EntryForm): Int? = when {
    form.kind == EntryKind.RECIPE && form.recipe == null -> R.string.diary_pick_recipe_first
    form.kind == EntryKind.INGREDIENT && form.ingredient == null -> R.string.diary_pick_ingredient_first
    form.kind == EntryKind.PRODUCT && form.product == null -> R.string.diary_pick_product_first
    form.kind == EntryKind.PRODUCT && form.product?.calories == null -> R.string.diary_product_no_figures_short
    else -> null
}

@Composable
internal fun KindChip(kind: EntryKind, label: Int, form: EntryForm, host: EntryWindow) {
    FilterChip(selected = form.kind == kind, onClick = { host.chooseKind(kind) }, label = { Text(stringResource(label)) })
}

@Composable
private fun perServing(recipe: Recipe) = stringResource(R.string.diary_per_serving, formatNumber(recipe.nutritionPerServing.calories.roundToInt()))

/** What 100 of an ingredient's own unit hold, where the catalogue says; nothing where it does not. */
@Composable
private fun per100(ingredient: DiaryIngredient): String? =
    ingredient.caloriesPer100?.let { stringResource(R.string.diary_per_100, formatNumber(it.roundToInt()), unitName(ingredient.canonicalUnit)) }

@Composable
private fun ServingsField(form: EntryForm, problems: Map<EntryField, EntryProblem>, window: EntryWindowState, host: EntryWindow) {
    FormField(
        value = form.servings,
        onValueChange = { typed -> host.typeEntry(EntryField.SERVINGS) { it.copy(servings = typed) } },
        label = stringResource(R.string.diary_servings_field),
        problem = fieldProblem(EntryField.SERVINGS, problems, window),
        keyboardType = KeyboardType.Decimal,
    )
}

/**
 * A meal of one's own: its name, what a serving holds, those of its macronutrients that are known, and
 * how many servings it is: energy and servings on one row, the three macronutrients on another, each
 * row going on to the next line where a large font leaves no room.
 */
@Composable
private fun MealFields(form: EntryForm, problems: Map<EntryField, EntryProblem>, window: EntryWindowState, host: EntryWindow) {
    FormField(
        value = form.name,
        onValueChange = { typed -> host.typeEntry(EntryField.NAME) { it.copy(name = typed) } },
        label = stringResource(R.string.diary_meal_name),
        problem = fieldProblem(EntryField.NAME, problems, window, Bound.Characters),
        keyboardType = KeyboardType.Text,
        capitalization = KeyboardCapitalization.Sentences,
    )
    Text(stringResource(R.string.diary_one_portion), style = MaterialTheme.typography.titleSmall)
    FigureRows(
        calories = form.calories,
        protein = form.protein,
        fat = form.fat,
        carbs = form.carbs,
        problems = problems,
        window = window,
        onType = { field, typed ->
            host.typeEntry(field) {
                when (field) {
                    EntryField.CALORIES -> it.copy(calories = typed)
                    EntryField.PROTEIN -> it.copy(protein = typed)
                    EntryField.FAT -> it.copy(fat = typed)
                    else -> it.copy(carbs = typed)
                }
            }
        },
        servings = { modifier ->
            FormField(
                value = form.servings,
                onValueChange = { typed -> host.typeEntry(EntryField.SERVINGS) { it.copy(servings = typed) } },
                label = stringResource(R.string.diary_servings_field),
                problem = fieldProblem(EntryField.SERVINGS, problems, window),
                keyboardType = KeyboardType.Decimal,
                modifier = modifier,
            )
        },
    )
    Note(stringResource(R.string.diary_macros_optional))
}

/**
 * The figures of a serving of a meal of one's own in two columns, as the web's window has them: its
 * energy beside [servings], then its protein, fat and carbohydrate; in one column where two fields of
 * the phone's size of text do not fit side by side. A macronutrient's [estimated] value, where it was
 * estimated, is what its empty field says it would be.
 */
@Composable
private fun FigureRows(
    calories: String,
    protein: String,
    fat: String,
    carbs: String,
    problems: Map<EntryField, EntryProblem>,
    window: EntryWindowState,
    onType: (EntryField, String) -> Unit,
    servings: @Composable (Modifier) -> Unit,
    estimated: (EntryField) -> String? = { null },
) {
    val grams = stringResource(R.string.unit_g)
    val energy = stringResource(R.string.diary_calories)
    val kcal = stringResource(R.string.unit_kcal)
    val fields: List<@Composable (Modifier) -> Unit> = listOf(
        { place ->
            FormField(
                value = calories,
                onValueChange = { onType(EntryField.CALORIES, it) },
                label = energy,
                suffix = kcal,
                problem = fieldProblem(EntryField.CALORIES, problems, window),
                keyboardType = KeyboardType.Decimal,
                modifier = place,
            )
        },
        servings,
    ) + listOf(
        Triple(EntryField.PROTEIN, protein, R.string.diary_protein),
        Triple(EntryField.FAT, fat, R.string.diary_fat),
        Triple(EntryField.CARBS, carbs, R.string.diary_carbs),
    ).map { (field, value, label) ->
        { place: Modifier ->
            FormField(
                value = value,
                onValueChange = { onType(field, it) },
                label = stringResource(label),
                suffix = grams,
                placeholder = estimated(field)?.let { "≈ $it" },
                optional = true,
                problem = fieldProblem(field, problems, window),
                keyboardType = KeyboardType.Decimal,
                modifier = place,
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = figureColumns(maxWidth, LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(FIGURE_GAP)) {
                    row.forEach { field -> field(Modifier.weight(1f)) }
                    // A row with a field fewer keeps the columns of the rows above it.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The room a field of a figure needs at the phone's usual size of text: its name and "optional" above it, side by side. */
internal val FIGURE_FIELD_WIDTH = 150.dp

/** The space between two fields of a row. */
private val FIGURE_GAP = 8.dp

/** How many fields of figures stand side by side in [width] at the phone's [fontScale]: two where both fit, else one. */
internal fun figureColumns(width: Dp, fontScale: Float): Int =
    if (width >= FIGURE_FIELD_WIDTH * fontScale * 2 + FIGURE_GAP) 2 else 1

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
private fun Search(label: String, form: EntryForm, host: EntryWindow, found: @Composable () -> Unit) {
    OutlinedField(
        value = form.search,
        onValueChange = host::search,
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
                TextButton(onClick = { host.chooseKind(EntryKind.MEAL) }) { Text(stringResource(R.string.diary_kind_meal)) }
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
internal fun Found(name: String, about: String?, onPick: () -> Unit) {
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
                if (edit.own) {
                    FormField(
                        value = edit.name,
                        onValueChange = { typed -> viewModel.typeEdit { it.copy(name = typed) } },
                        label = stringResource(R.string.diary_meal_name),
                        problem = fieldProblem(EntryField.NAME, problems, state.window, Bound.Characters),
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                    )
                    Text(stringResource(R.string.diary_one_portion), style = MaterialTheme.typography.titleSmall)
                    FigureRows(
                        calories = edit.calories,
                        protein = edit.protein,
                        fat = edit.fat,
                        carbs = edit.carbs,
                        problems = problems,
                        window = state.window,
                        onType = { field, typed ->
                            viewModel.typeEdit {
                                when (field) {
                                    EntryField.CALORIES -> it.copy(calories = typed)
                                    EntryField.PROTEIN -> it.copy(protein = typed)
                                    EntryField.FAT -> it.copy(fat = typed)
                                    else -> it.copy(carbs = typed)
                                }
                            }
                        },
                        servings = { modifier ->
                            FormField(
                                value = edit.servings,
                                onValueChange = { typed -> viewModel.typeEdit { it.copy(servings = typed) } },
                                label = stringResource(R.string.diary_servings_field),
                                problem = fieldProblem(EntryField.SERVINGS, problems, state.window),
                                keyboardType = KeyboardType.Decimal,
                                modifier = modifier,
                            )
                        },
                        estimated = { field ->
                            when (field) {
                                EntryField.PROTEIN -> edit.estimatedAt("protein", edit.entry.macros?.protein)
                                EntryField.FAT -> edit.estimatedAt("fat", edit.entry.macros?.fat)
                                else -> edit.estimatedAt("carbs", edit.entry.macros?.carbs)
                            }
                        },
                    )
                    Note(stringResource(R.string.diary_macros_optional))
                } else if (amount != null) {
                    // The amount as it was given, in the unit it was given in.
                    FormField(
                        value = edit.quantity,
                        onValueChange = { typed -> viewModel.typeEdit { it.copy(quantity = typed) } },
                        label = "${stringResource(R.string.diary_quantity)} (${unitName(amount.shownUnit)})",
                        problem = fieldProblem(EntryField.QUANTITY, problems, state.window),
                        keyboardType = KeyboardType.Decimal,
                    )
                } else {
                    FormField(
                        value = edit.servings,
                        onValueChange = { typed -> viewModel.typeEdit { it.copy(servings = typed) } },
                        label = stringResource(R.string.diary_servings_field),
                        problem = fieldProblem(EntryField.SERVINGS, problems, state.window),
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                if (edit.correctable) {
                    val own = edit.found?.takeIf { it.source == ProductSource.OWN }
                    if (own != null) {
                        // A version of the reader's own is there already: the entry can go on to it, not to a second one.
                        Text(stringResource(R.string.diary_entry_own_version, own.fullName), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { viewModel.moveEditToOwn(own) }, enabled = !state.sending) {
                            Text(stringResource(R.string.diary_entry_use_own_version))
                        }
                    } else {
                        Note(stringResource(R.string.diary_entry_correct_hint))
                        OutlinedButton(onClick = viewModel::lookUpForCorrection, enabled = !edit.looking && !state.sending) {
                            Text(stringResource(R.string.diary_entry_correct))
                        }
                    }
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

/** What opens over the window that adds what was eaten: a product of one's own being written, and the camera. */
@Composable
internal fun EntryWindowOverlays(window: EntryWindowState, host: EntryWindow) {
    window.form?.writingProduct?.let { code ->
        OwnProductDialog(
            product = null,
            code = code,
            from = window.form.correcting,
            onSaved = { saved -> host.takeWrittenProduct(saved.product) },
            onDismiss = host::closeOwnProduct,
            onOwnMeal = {
                host.closeOwnProduct()
                host.asOwnMeal(null)
            },
        )
    }
    // After the window it goes over: the window that opens last is the one on top.
    if (window.scanning) ScannerDialog(host::scanned, host::closeScanner)
}
