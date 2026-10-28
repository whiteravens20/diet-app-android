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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.DIFFICULTIES
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.MEAL_TYPES
import net.whiteravens.dietapp.ui.common.Bound
import net.whiteravens.dietapp.ui.common.allergenLabel
import net.whiteravens.dietapp.ui.common.cuisineLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.difficultyLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.common.refusalText
import net.whiteravens.dietapp.ui.common.unitName
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.components.Skeleton
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import net.whiteravens.dietapp.ui.screens.profiles.ProfileChoices
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel

/** A change of the form, as the view model takes it. */
private typealias RecipeEdit = ((RecipeForm) -> RecipeForm) -> Unit

/**
 * A recipe of one's own written by hand, or edited, as the web's form has it: what it is called
 * and what it is, the meals it suits, how many servings it makes, how long it takes, how hard it is
 * and its kitchen; its ingredients found in the catalogue, each with its amount, unit and note; its
 * steps in order; and what a serving holds as it is written. Saved, it opens as [onSaved] opens
 * it, an edit its new version; a recipe of one's own it would repeat is [onOpenRecipe].
 */
@Composable
fun RecipeFormScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenRecipe: (String) -> Unit,
    viewModel: RecipeFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { state.saved?.let(onSaved) }
    val editing = state.editing != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(
            title = stringResource(if (editing) R.string.own_recipe_edit_title else R.string.own_recipe_new_title),
            subtitle = stringResource(if (editing) R.string.own_recipe_edit_intro else R.string.own_recipe_new_intro),
            onBack = onBack,
        )
        when {
            state.loading -> {
                Skeleton(Modifier.fillMaxWidth().height(240.dp))
                Skeleton(Modifier.fillMaxWidth().height(160.dp))
            }

            state.loadError != null -> {
                ErrorBanner(errorMessage(state.loadError!!))
                Button(onClick = viewModel::load) { Text(stringResource(R.string.common_retry)) }
            }

            else -> {
                val marks = state.marks
                AboutCard(state.form, marks, viewModel::edit)
                LinesCard(state, marks, viewModel)
                StepsCard(state.form, marks, viewModel::edit)
                ServingCard(state)
                SaveFailure(state, marks, onOpenRecipe)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = viewModel::save, enabled = !state.saving, shape = MaterialTheme.shapes.medium) {
                        Text(stringResource(if (state.saving) R.string.own_recipe_saving else R.string.own_recipe_save))
                    }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}

/** What the recipe is called and what it is, the meals it suits, its servings and times, how hard it is and its kitchen. */
@Composable
private fun AboutCard(form: RecipeForm, marks: RecipeMarks, onEdit: RecipeEdit) {
    val refused = marks.refusals.fields
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(
                value = form.title,
                onValueChange = { typed -> onEdit { it.copy(title = typed) } },
                label = stringResource(R.string.own_recipe_field_title),
                problem = refused[RecipeField.TITLE]?.let { refusalOf(it, Bound.Characters) },
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences,
            )
            TextArea(
                value = form.description,
                onValueChange = { typed -> onEdit { it.copy(description = typed) } },
                label = stringResource(R.string.own_recipe_field_description),
                problem = refused[RecipeField.DESCRIPTION]?.let { refusalOf(it, Bound.Characters) },
            )
            MealsField(form.mealTypes, refused[RecipeField.MEALS], onEdit)
            FormField(
                value = form.servings,
                onValueChange = { typed -> onEdit { it.copy(servings = typed) } },
                label = stringResource(R.string.own_recipe_field_servings),
                problem = numberMark(RecipeField.SERVINGS in marks.notWhole, refused[RecipeField.SERVINGS], Bound.Number(), whole = true),
            )
            FormField(
                value = form.prepMinutes,
                onValueChange = { typed -> onEdit { it.copy(prepMinutes = typed) } },
                label = stringResource(R.string.own_recipe_field_prep),
                problem = numberMark(RecipeField.PREP in marks.notWhole, refused[RecipeField.PREP], Bound.Number(), whole = true),
            )
            FormField(
                value = form.cookMinutes,
                onValueChange = { typed -> onEdit { it.copy(cookMinutes = typed) } },
                label = stringResource(R.string.own_recipe_field_cook),
                problem = numberMark(RecipeField.COOK in marks.notWhole, refused[RecipeField.COOK], Bound.Number(), whole = true),
            )
            ChoiceField(
                label = stringResource(R.string.recipes_difficulty),
                options = DIFFICULTIES,
                selected = form.difficulty,
                optionLabel = { difficultyLabel(it) },
                onSelect = { chosen -> onEdit { it.copy(difficulty = chosen) } },
            )
            ChoiceField(
                label = stringResource(R.string.recipes_cuisine),
                options = listOf<String?>(null) + ProfileChoices.CUISINES,
                selected = form.cuisine,
                optionLabel = { cuisine -> if (cuisine == null) stringResource(R.string.own_recipe_cuisine_none) else cuisineLabel(cuisine) },
                onSelect = { chosen -> onEdit { it.copy(cuisine = chosen) } },
            )
        }
    }
}

/** The meals the recipe suits, as many as fit it, in the order of a day; and why the API refused them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealsField(chosen: List<String>, refused: RecipeRefusal?, onEdit: RecipeEdit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.own_recipe_field_meals), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MEAL_TYPES.forEach { meal ->
                val on = meal in chosen
                FilterChip(
                    selected = on,
                    onClick = { onEdit { form -> form.copy(mealTypes = MEAL_TYPES.filter { if (it == meal) !on else it in form.mealTypes }) } },
                    label = { Text(mealTypeLabel(meal)) },
                )
            }
        }
        refused?.let { Problem(mealsText(it)) }
    }
}

/**
 * The recipe's ingredients, each on a line of its own with its amount, unit and note, to be taken
 * off; and an ingredient of the catalogue, found by a part of its name, put on a line of its own.
 */
@Composable
private fun LinesCard(state: RecipeFormViewModel.UiState, marks: RecipeMarks, viewModel: RecipeFormViewModel) {
    val lines = state.form.lines
    val refused = marks.refusals.fields[RecipeField.LINES]
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.recipes_ingredients))
            when {
                refused != null -> Problem(linesText(refused))
                lines.isEmpty() -> Hint(stringResource(R.string.own_recipe_no_ingredients))
            }
            lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LineFields(line, marks, viewModel::edit)
            }
            SearchField(state, viewModel)
        }
    }
}

/** A line: its ingredient, to be taken off, and its amount, unit and note, each with why it is refused. */
@Composable
private fun LineFields(line: FormLine, marks: RecipeMarks, onEdit: RecipeEdit) {
    val name = line.ingredient.name
    val refused = marks.refusals.lines[line.key].orEmpty()
    fun change(edit: (FormLine) -> FormLine) = onEdit { form -> form.copy(lines = form.lines.map { if (it.key == line.key) edit(it) else it }) }
    // A piece is called what the ingredient calls it: a slice of bread, a clove of garlic.
    val unitLabel: @Composable (String) -> String = { unit -> unitName(if (unit == PIECE) line.ingredient.pieceName else unit) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onEdit { form -> form.copy(lines = form.lines.filterNot { it.key == line.key }) } },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.own_recipe_remove_ingredient, name))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val amountOf = stringResource(R.string.own_recipe_amount_of, name)
            FormField(
                value = line.quantity,
                onValueChange = { typed -> change { it.copy(quantity = typed) } },
                label = stringResource(R.string.own_recipe_field_amount),
                problem = numberMark(
                    notANumber = line.key in marks.notNumbers,
                    refusal = refused[LinePart.QUANTITY],
                    bound = Bound.Number(decimals = 2, unit = unitLabel(line.unit)),
                    whole = false,
                ),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f).semantics { contentDescription = amountOf },
            )
            ChoiceField(
                label = stringResource(R.string.own_recipe_field_unit),
                options = line.ingredient.units,
                selected = line.unit,
                optionLabel = unitLabel,
                onSelect = { unit -> change { it.copy(unit = unit) } },
                description = stringResource(R.string.own_recipe_unit_of, name),
                modifier = Modifier.weight(1f),
            )
        }
        val noteOf = stringResource(R.string.own_recipe_note_of, name)
        FormField(
            value = line.note,
            onValueChange = { typed -> change { it.copy(note = typed) } },
            label = stringResource(R.string.own_recipe_field_note),
            problem = refused[LinePart.NOTE]?.let { refusalOf(it, Bound.Characters) },
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Sentences,
            modifier = Modifier.semantics { contentDescription = noteOf },
        )
    }
}

/** An ingredient of the catalogue looked for by a part of its name: those found, each to put on a line, or a word that none was. */
@Composable
private fun SearchField(state: RecipeFormViewModel.UiState, viewModel: RecipeFormViewModel) {
    OutlinedTextField(
        value = state.search,
        onValueChange = viewModel::search,
        label = { Text(stringResource(R.string.own_recipe_add_ingredient)) },
        supportingText = { Text(stringResource(R.string.own_recipe_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.search.trim().length < ProfilesViewModel.SEARCH_FROM) return
    if (state.found.isNotEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                state.found.forEach { ingredient ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { viewModel.add(ingredient) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(ingredient.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    } else if (state.searched) {
        Hint(stringResource(R.string.own_recipe_nothing_found))
    }
}

/** The method, step by step in order: each step moved up or down or taken off, and another added after them. */
@Composable
private fun StepsCard(form: RecipeForm, marks: RecipeMarks, onEdit: RecipeEdit) {
    val refused = marks.refusals.fields[RecipeField.STEPS]
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.recipes_steps))
            Hint(stringResource(R.string.own_recipe_allergen_hint))
            form.steps.forEachIndexed { index, step ->
                val number = index + 1
                Row(verticalAlignment = Alignment.Top) {
                    TextArea(
                        value = step.text,
                        onValueChange = { typed -> onEdit { it.copy(steps = it.steps.map { kept -> if (kept.key == step.key) kept.copy(text = typed) else kept }) } },
                        label = stringResource(R.string.own_recipe_step_number, number),
                        problem = marks.refusals.steps[step.key]?.let { refusalOf(it, Bound.Characters) },
                        minLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    Column {
                        IconButton(onClick = { onEdit { it.withStepMoved(step.key, -1) } }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.own_recipe_step_up, number))
                        }
                        IconButton(onClick = { onEdit { it.withStepMoved(step.key, 1) } }, enabled = index < form.steps.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.own_recipe_step_down, number))
                        }
                        IconButton(onClick = { onEdit { it.copy(steps = it.steps.filterNot { kept -> kept.key == step.key }) } }, enabled = form.steps.size > 1) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.own_recipe_remove_step, number))
                        }
                    }
                }
            }
            refused?.let { Problem(stepsText(it)) }
            OutlinedButton(onClick = { onEdit { it.withStep() } }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.own_recipe_add_step), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * What a serving holds as the recipe is written, its allergens and the diets it fits; or what the
 * figures wait for, or why they cannot be said.
 */
@Composable
private fun ServingCard(state: RecipeFormViewModel.UiState) {
    val preview = state.preview
    AppCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle(stringResource(R.string.recipes_nutrition_per_serving))
            when {
                state.form.previewAsk() == null -> Hint(stringResource(R.string.own_recipe_preview_waiting))
                state.previewRefusals.marked -> Hint(stringResource(R.string.calorie_preview_fix_fields))
                state.previewFailed != null -> Hint(errorMessage(state.previewFailed))
                preview == null -> Hint(stringResource(R.string.own_recipe_preview_waiting))
                else -> {
                    val serving = preview.perServing
                    Text(
                        stringResource(
                            R.string.own_recipe_preview_figures,
                            formatNumber(serving.calories),
                            formatNumber(serving.protein),
                            formatNumber(serving.fat),
                            formatNumber(serving.carbs),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Hint(
                        if (preview.allergens.isEmpty()) {
                            stringResource(R.string.own_recipe_preview_no_allergens)
                        } else {
                            "${stringResource(R.string.recipe_allergens)} ${preview.allergens.map { allergenLabel(it) }.joinToString(", ")}"
                        },
                    )
                    if (preview.dietTags.isNotEmpty()) {
                        Hint(stringResource(R.string.own_recipe_preview_diets, preview.dietTags.map { dietTypeLabel(it) }.joinToString(", ")))
                    }
                }
            }
        }
    }
}

/**
 * Why saving failed, beside the button: that the fields marked hold what was not taken, or what the
 * fields cannot say; and the recipe of one's own the one saved would repeat, to be opened.
 */
@Composable
private fun SaveFailure(state: RecipeFormViewModel.UiState, marks: RecipeMarks, onOpenRecipe: (String) -> Unit) {
    val failed = stringResource(R.string.own_recipe_failed)
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    val error = state.error
    when {
        error != null -> ErrorBanner("$failed ${errorMessage(error)}", live)
        state.saveRefusals.marked || marks.notWhole.isNotEmpty() || marks.notNumbers.isNotEmpty() ->
            ErrorBanner("$failed ${stringResource(R.string.own_recipe_fix_fields)}", live)
    }
    state.yours?.let { id -> TextButton(onClick = { onOpenRecipe(id) }) { Text(stringResource(R.string.own_recipe_open_yours)) } }
}

/** A field of several lines, with what is wrong with it once that is said. */
@Composable
private fun TextArea(value: String, onValueChange: (String) -> Unit, label: String, problem: String?, modifier: Modifier = Modifier, minLines: Int = 3) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = problem?.let { { Text(it) } },
        isError = problem != null,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Problem(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** What [refusal] says of a field, its bound said as [bound]; a rule by the words for it. */
@Composable
private fun refusalOf(refusal: RecipeRefusal, bound: Bound): String = when (refusal) {
    is RecipeRefusal.OutOfBounds -> refusalText(refusal.refusal, bound)
    is RecipeRefusal.AgainstRule -> when (refusal.rule) {
        "markup" -> stringResource(R.string.own_recipe_problem_markup)
        "text-too-long" -> stringResource(R.string.own_recipe_problem_too_long)
        "quantity-too-large" -> stringResource(R.string.own_recipe_problem_too_much)
        in MISSING -> stringResource(R.string.refusal_empty)
        else -> stringResource(R.string.refusal_other)
    }
}

/** What a field of numbers says: that it holds no number, or no whole one where [whole] it should, or why the API refused it. */
@Composable
private fun numberMark(notANumber: Boolean, refusal: RecipeRefusal?, bound: Bound, whole: Boolean): String? = when {
    notANumber -> stringResource(if (whole) R.string.refusal_not_whole else R.string.refusal_not_a_number)
    refusal != null -> refusalOf(refusal, bound)
    else -> null
}

/** What the meals say when the API refused them: fewer chosen than it takes. */
@Composable
private fun mealsText(refusal: RecipeRefusal): String {
    val least = ((refusal as? RecipeRefusal.OutOfBounds)?.refusal as? FieldRefusal.TooSmall)?.least?.toInt()
        ?: 1.takeIf { refusal is RecipeRefusal.AgainstRule && refusal.rule in MISSING }
    return if (least != null) pluralStringResource(R.plurals.own_recipe_meals_at_least, least, least) else refusalOf(refusal, Bound.Number())
}

/** What the ingredient lines say when the API refused them: none, or more than it takes. */
@Composable
private fun linesText(refusal: RecipeRefusal): String {
    val bound = (refusal as? RecipeRefusal.OutOfBounds)?.refusal
    return when {
        bound is FieldRefusal.TooSmall || refusal is RecipeRefusal.AgainstRule && refusal.rule in MISSING -> stringResource(R.string.own_recipe_no_ingredients)
        bound is FieldRefusal.TooBig -> bound.most.toInt().let { pluralStringResource(R.plurals.own_recipe_lines_at_most, it, it) }
        else -> refusalOf(refusal, Bound.Number())
    }
}

/** What the steps say when the API refused them: none written, or more than it takes. */
@Composable
private fun stepsText(refusal: RecipeRefusal): String {
    val bound = (refusal as? RecipeRefusal.OutOfBounds)?.refusal
    return when {
        bound is FieldRefusal.TooSmall || refusal is RecipeRefusal.AgainstRule && refusal.rule in MISSING -> stringResource(R.string.own_recipe_steps_none)
        bound is FieldRefusal.TooBig -> bound.most.toInt().let { pluralStringResource(R.plurals.own_recipe_steps_at_most, it, it) }
        else -> refusalOf(refusal, Bound.Number())
    }
}

/** The rules a field breaks by holding nothing. */
private val MISSING = setOf("missing-field", "missing-locale")

/** The unit of an ingredient counted in pieces. */
private const val PIECE = "piece"
