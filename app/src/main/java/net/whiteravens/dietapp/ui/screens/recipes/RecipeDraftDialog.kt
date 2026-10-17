// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.MealSlots
import net.whiteravens.dietapp.domain.RecipeDraftAsk
import net.whiteravens.dietapp.ui.common.cuisineLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.errorMessage
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.ErrorBanner
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.screens.profiles.ProfileBounds
import kotlin.math.roundToInt

/**
 * The window that asks a model for a recipe of one's own, as the web's does: no text of the user's,
 * only choices, each left to the model where nothing is chosen; the profile's allergens always held;
 * what the profile favours and excludes where the user says; and a word that a model may err. The
 * recipe written goes to [onDrafted].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeDraftDialog(onDrafted: (recipeId: String) -> Unit, onDismiss: () -> Unit, viewModel: RecipeDraftViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.drafted) { state.drafted?.let { onDrafted(it.id) } }

    var profileId by rememberSaveable { mutableStateOf<String?>(null) }
    val profile = state.profiles.firstOrNull { it.id == profileId } ?: state.profiles.firstOrNull()
    var dietType by rememberSaveable { mutableStateOf<String?>(null) }
    var mealType by rememberSaveable { mutableStateOf<String?>(null) }
    var cuisine by rememberSaveable { mutableStateOf<String?>(null) }
    var cookingMethod by rememberSaveable { mutableStateOf<String?>(null) }
    var complexity by rememberSaveable { mutableStateOf<String?>(null) }
    var kcalOn by rememberSaveable { mutableStateOf(false) }
    var kcal by rememberSaveable { mutableFloatStateOf(500f) }
    var timeOn by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableFloatStateOf(45f) }
    var useFavorites by rememberSaveable { mutableStateOf(false) }
    var respectExclusions by rememberSaveable { mutableStateOf(true) }
    var addToFavorites by rememberSaveable { mutableStateOf(true) }

    ScreenDialog(onDismiss) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.draft_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
            Note(stringResource(R.string.draft_subhead))
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.draft_disclaimer), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (profile != null) {
                // Whose recipe it is, where there is more than one profile.
                if (state.profiles.size > 1) {
                    Choice(stringResource(R.string.recipes_favorites_profile), state.profiles.map { it.id }, profile.id, { id -> state.profiles.first { it.id == id }.figures.name }, any = false) { profileId = it }
                }
                val diet = dietType ?: profile.figures.dietType
                Choice(stringResource(R.string.draft_diet_type), NAMED_DIETS, diet, { dietTypeLabel(it) }, any = false) { dietType = it }
                Choice(stringResource(R.string.draft_meal_type), MealSlots.BY_COUNT.getValue(5), mealType, { mealTypeLabel(it) }) { mealType = it }
                Choice(stringResource(R.string.draft_cuisine), ProfileBounds.CUISINES, cuisine, { cuisineLabel(it) }) { cuisine = it }
                Choice(stringResource(R.string.draft_cooking_method), COOKING_METHODS, cookingMethod, { cookingLabel(it) }) { cookingMethod = it }
                Choice(stringResource(R.string.draft_complexity), COMPLEXITIES, complexity, { complexityLabel(it) }) { complexity = it }

                Text(stringResource(R.string.draft_targets), style = MaterialTheme.typography.titleSmall)
                Target(stringResource(R.string.draft_kcal_target), kcalOn, { kcalOn = it }, kcal, { kcal = it }, 200f..1200f, steps = 19, stringResource(R.string.draft_kcal_value, formatNumber(kcal.roundToInt())))
                Target(stringResource(R.string.draft_prep_time_max), timeOn, { timeOn = it }, minutes, { minutes = it }, 5f..120f, steps = 22, stringResource(R.string.draft_minutes_value, formatNumber(minutes.roundToInt())))

                val favourites = profile.preferences.favoriteIngredientIds.size
                val excluded = profile.preferences.excludedIngredientIds.size
                val allergens = profile.figures.allergens.size
                Text(stringResource(R.string.draft_profile_prefs), style = MaterialTheme.typography.titleSmall)
                CheckRow(
                    stringResource(R.string.draft_use_favorites),
                    hint = if (favourites == 0) stringResource(R.string.draft_use_favorites_empty) else pluralStringResource(R.plurals.draft_use_favorites_count, favourites, favourites),
                    checked = useFavorites && favourites > 0,
                    enabled = favourites > 0,
                ) { useFavorites = it }
                CheckRow(
                    stringResource(R.string.draft_respect_exclusions),
                    hint = if (excluded == 0) stringResource(R.string.draft_respect_exclusions_empty) else pluralStringResource(R.plurals.draft_respect_exclusions_count, excluded, excluded),
                    checked = respectExclusions && excluded > 0,
                    enabled = excluded > 0,
                ) { respectExclusions = it }
                if (allergens > 0) Note(pluralStringResource(R.plurals.draft_allergens_respected, allergens, allergens))
                CheckRow(stringResource(R.string.draft_add_to_favorites), checked = addToFavorites) { addToFavorites = it }
                Note(stringResource(R.string.draft_determinism_hint))
                state.error?.let { ErrorBanner(errorMessage(it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = {
                            viewModel.draft(
                                RecipeDraftAsk(
                                    profileId = profile.id,
                                    dietType = diet,
                                    mealType = mealType,
                                    cuisine = cuisine,
                                    cookingMethod = cookingMethod,
                                    complexity = complexity,
                                    kcalTarget = kcal.roundToInt().takeIf { kcalOn },
                                    prepMinutes = minutes.roundToInt().takeIf { timeOn },
                                    useFavoriteIngredients = useFavorites && favourites > 0,
                                    respectExclusions = respectExclusions,
                                    addToFavorites = addToFavorites,
                                ),
                            )
                        },
                        enabled = !state.drafting,
                    ) { Text(stringResource(if (state.drafting) R.string.draft_drafting else R.string.draft_submit)) }
                }
            } else if (!state.loading) {
                state.error?.let { ErrorBanner(errorMessage(it)) }
            }
        }
    }
}

/** The diets a recipe may be drafted for: every diet but one's own split of macros. */
private val NAMED_DIETS = listOf("balanced", "high_protein", "low_carb", "vegetarian", "vegan", "keto", "mediterranean")
private val COOKING_METHODS = listOf("baked", "grilled", "pan_fried", "boiled", "steamed", "stewed", "raw", "no_cook")
private val COMPLEXITIES = listOf("simple", "medium", "complex")

@Composable
private fun cookingLabel(code: String): String = when (code) {
    "baked" -> stringResource(R.string.cooking_baked)
    "grilled" -> stringResource(R.string.cooking_grilled)
    "pan_fried" -> stringResource(R.string.cooking_pan_fried)
    "boiled" -> stringResource(R.string.cooking_boiled)
    "steamed" -> stringResource(R.string.cooking_steamed)
    "stewed" -> stringResource(R.string.cooking_stewed)
    "raw" -> stringResource(R.string.cooking_raw)
    else -> stringResource(R.string.cooking_no_cook)
}

@Composable
private fun complexityLabel(code: String): String = when (code) {
    "simple" -> stringResource(R.string.complexity_simple)
    "medium" -> stringResource(R.string.complexity_medium)
    else -> stringResource(R.string.complexity_complex)
}

/** One of [options] under [title], or none ("Any", where [any]), each a chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(title: String, options: List<String>, selected: String?, label: @Composable (String) -> String, any: Boolean = true, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (any) FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.draft_any)) })
            options.forEach { option ->
                FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

/** A target that may be set: a box to turn it on, and a slider for it while it is. */
@Composable
private fun Target(
    label: String,
    on: Boolean,
    onOn: (Boolean) -> Unit,
    value: Float,
    onValue: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    said: String,
) {
    Column {
        CheckRow(label, hint = said.takeIf { on }, checked = on) { onOn(it) }
        if (on) Slider(value = value, onValueChange = onValue, valueRange = range, steps = steps)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
