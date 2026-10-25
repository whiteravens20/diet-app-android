// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.ModelPart
import net.whiteravens.dietapp.domain.Rebalanced
import net.whiteravens.dietapp.domain.SubstitutionPreview
import net.whiteravens.dietapp.ui.common.MARK
import net.whiteravens.dietapp.ui.common.amountLabel
import net.whiteravens.dietapp.ui.common.emphasized
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.fallbackText
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.components.AiMark
import net.whiteravens.dietapp.ui.components.AiOffNote
import net.whiteravens.dietapp.ui.components.aiOffText
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.components.modelPartText
import net.whiteravens.dietapp.ui.screens.profiles.WarningBox
import kotlin.math.roundToInt

/**
 * The window one ingredient of a meal is substituted in, as the web's: 1. the line that leaves,
 * 2. the ingredient that comes (the catalogue's suggestion, a search, or a model's pick), 3. what
 * that does to the meal, with what stands in its way, the change of a serving to take where there
 * is one, and a model's rewording of the text to ask for. The plan the substitution leaves goes
 * to [onApplied].
 */
@Composable
fun SubstituteDialog(
    planId: String,
    meal: Meal,
    onApplied: (Rebalanced) -> Unit,
    onDismiss: () -> Unit,
    onTurnAiOn: () -> Unit,
    viewModel: SubstituteViewModel = hiltViewModel(key = "substitute-${meal.id}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(meal.id) { viewModel.open(planId, meal.id) }
    // The window's model outlives it: what the model holds now is read, so that a substitution an
    // earlier opening on the same meal made is not handed back before the state on the screen catches up.
    LaunchedEffect(state.done) { viewModel.state.value.done?.let(onApplied) }

    ScreenDialog(onDismiss) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.substitute_title), style = MaterialTheme.typography.titleLarge)
                    Text(meal.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
            state.error?.let { ErrorBanner(errorMessage(it)) }

            Text(stringResource(R.string.substitute_step_pick), style = MaterialTheme.typography.titleSmall)
            meal.lines.forEach { line ->
                Choice(
                    label = line.name,
                    aside = amountLabel(line.quantity, line.unit),
                    selected = line.ingredientId == state.fromId,
                    onClick = { viewModel.pickLine(line.ingredientId) },
                )
            }

            val from = meal.lines.find { it.ingredientId == state.fromId }
            if (from != null) {
                Text(emphasized(stringResource(R.string.substitute_step_replace, MARK), from.name), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.search,
                        onValueChange = viewModel::search,
                        placeholder = { Text(stringResource(R.string.pickers_ingredient_search)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::suggest, enabled = state.aiOn == true && !state.suggesting) {
                        Text(stringResource(if (state.suggesting) R.string.substitute_ai_suggest_pending else R.string.substitute_ai_suggest))
                    }
                }
                if (state.aiOn == false) AiOffNote(onTurnAiOn)
                else Note(stringResource(R.string.substitute_tooltip_ai_suggest))
                state.suggested?.let { suggested ->
                    suggested.fallbackReason?.let { reason -> fallbackText(reason)?.let { Note(it, MaterialTheme.colorScheme.tertiary) } }
                    suggested.reason?.let { Note(stringResource(R.string.substitute_ai_reason, it), MaterialTheme.colorScheme.onSurface) }
                }
                state.suggestions[from.ingredientId]?.let { suggestion ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Note(stringResource(R.string.substitute_catalogue_suggests))
                        TextButton(onClick = { viewModel.pickSubstitute(suggestion) }) { Text(suggestion.name) }
                    }
                }
                if (state.search.trim().length >= 2 || state.candidates.isNotEmpty()) {
                    if (state.candidates.isEmpty()) Note(stringResource(R.string.pickers_no_matches))
                    state.candidates.forEach { candidate ->
                        // A model's own pick says so where it stands among the others.
                        val picked = state.suggested?.takeIf { it.pick != null && it.ingredient.id == candidate.id }
                        Choice(
                            label = candidate.name,
                            aside = null,
                            selected = candidate.id == state.to?.id,
                            onClick = { viewModel.pickSubstitute(candidate) },
                            mark = picked?.let { { AiMark(modelPartText(ModelPart.Picked(it.reason))) } },
                        )
                    }
                }
            }

            if (from != null && state.to != null) {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.substitute_step_delta), style = MaterialTheme.typography.titleSmall)
                        if (state.previewing) Note(stringResource(R.string.substitute_computing))
                        state.preview?.let { preview -> PreviewOf(preview, state.acceptChange, viewModel::setAcceptChange) }
                        CheckRow(
                            label = stringResource(R.string.substitute_rewrite_with_ai),
                            hint = if (state.aiOn == false) aiOffText() else stringResource(R.string.substitute_rewrite_with_ai_hint),
                            checked = state.aiOn == true && state.rewriteWithAi,
                            onChange = viewModel::setRewriteWithAi,
                            enabled = state.aiOn == true,
                        )
                        Button(onClick = viewModel::apply, enabled = state.canApply, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (state.applying) R.string.substitute_applying else R.string.substitute_apply))
                        }
                    }
                }
            }
        }
    }
}

/** What a substitution does to the meal: how the amount was decided, the figures before and after, and what stands in its way. */
@Composable
private fun PreviewOf(preview: SubstitutionPreview, acceptChange: Boolean, onAcceptChange: (Boolean) -> Unit) {
    val fromAmount = amountLabel(preview.from.quantity, preview.from.unit)
    val toAmount = amountLabel(preview.to.quantity, preview.to.unit)
    val sizing = when (preview.sizing) {
        "capped" -> R.string.substitute_sizing_capped
        "mass" -> R.string.substitute_sizing_mass
        else -> R.string.substitute_sizing_energy
    }
    Note(stringResource(sizing, fromAmount, preview.from.name, toAmount, preview.to.name))
    if (preview.merged) Note(stringResource(R.string.substitute_merged, preview.to.name))
    val locale = LocalConfiguration.current.locales[0]
    Delta("kcal", preview.before.calories, preview.after.calories, grams = false)
    Delta(stringResource(R.string.macros_protein).lowercase(locale), preview.before.protein, preview.after.protein, grams = true)
    Delta(stringResource(R.string.macros_fat).lowercase(locale), preview.before.fat, preview.after.fat, grams = true)
    Delta(stringResource(R.string.macros_carbs).lowercase(locale), preview.before.carbs, preview.after.carbs, grams = true)
    if (!preview.valid) {
        Text(stringResource(R.string.substitute_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        preview.violations.forEach { violation ->
            Text("• ${violationText(violation)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    } else if (preview.moved.isNotEmpty()) {
        val moved = preview.moved.map { movedName(it) }.joinToString(", ")
        WarningBox {
            Text(stringResource(R.string.substitute_moved, moved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            CheckRow(label = stringResource(R.string.substitute_accept_change), checked = acceptChange, onChange = onAcceptChange)
        }
    }
}

/** A figure before and after, and by how much it moves, rounded as it is shown: more in the colour of a warning, less in the main one. */
@Composable
private fun Delta(label: String, before: Double, after: Double, grams: Boolean) {
    val digits = if (grams) 1 else 0
    val unit = if (grams) " g" else ""
    val diff = ((after - before) * 10).roundToInt() / 10.0
    val sign = if (diff > 0) "+" else ""
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatNumber(before, digits)}$unit → ${formatNumber(after, digits)}$unit ", style = MaterialTheme.typography.bodySmall)
        Text(
            "($sign${formatNumber(diff, digits)}$unit)",
            style = MaterialTheme.typography.bodySmall,
            color = when {
                diff > 0 -> MaterialTheme.colorScheme.error
                diff < 0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** One choice of a list: a line that leaves, or an ingredient that comes. */
@Composable
private fun Choice(label: String, aside: String?, selected: Boolean, onClick: () -> Unit, mark: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
        mark?.invoke()
        aside?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun violationText(violation: String): String = when (violation) {
    "off_diet" -> stringResource(R.string.substitute_violation_off_diet)
    "allergen" -> stringResource(R.string.substitute_violation_allergen)
    else -> stringResource(R.string.substitute_violation_excluded_ingredient)
}

@Composable
private fun movedName(nutrient: String): String = when (nutrient) {
    "calories" -> stringResource(R.string.substitute_moved_calories)
    "protein" -> stringResource(R.string.substitute_moved_protein)
    "fat" -> stringResource(R.string.substitute_moved_fat)
    else -> stringResource(R.string.substitute_moved_carbs)
}
