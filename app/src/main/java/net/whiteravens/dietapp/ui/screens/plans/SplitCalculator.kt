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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.SplitLine
import net.whiteravens.dietapp.domain.foodWeight
import net.whiteravens.dietapp.domain.ingredientsOf
import net.whiteravens.dietapp.domain.sharesOf
import net.whiteravens.dietapp.domain.sharesOfIngredient
import net.whiteravens.dietapp.domain.splitWhole
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.formatPercent
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.AppCard
import net.whiteravens.dietapp.ui.components.CheckRow
import net.whiteravens.dietapp.ui.components.ScreenDialog
import net.whiteravens.dietapp.ui.components.ScreenHeader
import net.whiteravens.dietapp.ui.screens.profiles.ChoiceField
import net.whiteravens.dietapp.ui.screens.profiles.FormField
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A portion a dish is split into: what it is called, the energy it is planned to hold where that is known, and
 * what it takes of each ingredient where the plan says so, by which a part of the dish weighed alone is split.
 */
internal data class SplitPortion(val key: String, val label: String, val kcal: Double?, val lines: List<SplitLine>? = null)

/** What is weighed: the whole of a mixed dish, one part of a dish in parts at a time, or pieces counted. */
private enum class Way { WHOLE, PART, PIECES }

/** How many portions a dish split on its own may have, as the web's. */
private const val MOST_PORTIONS = 12

/** A number above nothing typed in a field, a comma taken for a point; null for anything else. */
private fun typed(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

/**
 * Splits what was cooked once into its portions, each by its share, as the web's calculator does:
 * from the energy the plan gives each where [planned] portions are given (a dish cooked ahead),
 * otherwise portions chosen here, equal or by the energy typed for each. The whole dish is weighed,
 * or one part of it at a time, or its pieces counted; a dish weighed in its pot is the scale's
 * weight less the empty pot's. A part weighed alone of portions that say what they take of each
 * ingredient is split by what each takes of the ingredient chosen: each plate of a dish both people eat
 * holds its parts in proportions of its own. Whole grams and pieces, the last portion taking what is left.
 * Nothing is kept beyond the screen, and nothing is asked of the server.
 */
@Composable
internal fun SplitCalculator(planned: List<SplitPortion>?) {
    var count by rememberSaveable { mutableIntStateOf(2) }
    var byKcal by rememberSaveable { mutableStateOf(false) }
    var kcalTexts by rememberSaveable { mutableStateOf(List(MOST_PORTIONS) { "" }) }
    var way by rememberSaveable { mutableStateOf(Way.WHOLE) }
    var scaleText by rememberSaveable { mutableStateOf("") }
    var inPot by rememberSaveable { mutableStateOf(false) }
    var potText by rememberSaveable { mutableStateOf("") }
    var piecesText by rememberSaveable { mutableStateOf("") }
    var ingredientId by rememberSaveable { mutableStateOf<String?>(null) }

    val portions = planned ?: (1..count).map { n ->
        SplitPortion(n.toString(), stringResource(R.string.split_portion, n), if (byKcal) typed(kcalTexts[n - 1]) else null)
    }
    val kcalMissing = planned == null && byKcal && portions.any { it.kcal == null }
    // The ingredients a part weighed alone may be, where every portion says what it takes of each.
    val lines = linesOf(portions)
    val ingredients = lines?.let(::ingredientsOf).orEmpty()
    val byIngredient = way == Way.PART && ingredients.isNotEmpty()
    val chosen = ingredients.firstOrNull { it.ingredientId == ingredientId }?.takeIf { byIngredient }
    val shares = if (chosen != null) sharesOfIngredient(lines!!, chosen.ingredientId) else sharesOf(portions.map { it.kcal })
    val unchosen = byIngredient && chosen == null

    val onScale = typed(scaleText)
    val emptyPot = if (inPot) typed(potText) else null
    val weighed = way != Way.PIECES && onScale != null && (!inPot || emptyPot != null)
    val food = if (weighed) foodWeight(onScale!!, emptyPot) else null
    val potTooHeavy = weighed && inPot && food == null
    val pieces = if (way == Way.PIECES) typed(piecesText)?.takeIf { it % 1.0 == 0.0 } else null
    val amounts = when {
        kcalMissing || unchosen -> null
        way == Way.PIECES -> pieces?.let { splitWhole(it, shares) }
        else -> food?.let { splitWhole(it, shares) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (planned == null) {
            ChoiceField(
                label = stringResource(R.string.split_portion_count),
                options = (2..MOST_PORTIONS).toList(),
                selected = count,
                optionLabel = { formatNumber(it) },
                onSelect = { count = it },
            )
            CheckRow(label = stringResource(R.string.split_by_kcal), checked = byKcal, onChange = { byKcal = it })
            if (byKcal) {
                portions.forEachIndexed { index, _ ->
                    FormField(
                        value = kcalTexts[index],
                        onValueChange = { text -> kcalTexts = kcalTexts.toMutableList().also { it[index] = text } },
                        label = stringResource(R.string.split_kcal_of, index + 1),
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
        }

        Text(stringResource(R.string.split_way_legend), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Way.entries.forEach { option ->
                WayRow(
                    label = stringResource(
                        when (option) {
                            Way.WHOLE -> R.string.split_way_whole
                            Way.PART -> R.string.split_way_part
                            Way.PIECES -> R.string.split_way_pieces
                        },
                    ),
                    hint = stringResource(
                        when (option) {
                            Way.WHOLE -> R.string.split_way_whole_hint
                            Way.PART -> if (ingredients.isNotEmpty()) R.string.split_way_part_own_hint else R.string.split_way_part_hint
                            Way.PIECES -> R.string.split_way_pieces_hint
                        },
                    ),
                    selected = way == option,
                    onClick = { way = option },
                )
            }
        }

        if (way == Way.PIECES) {
            FormField(
                value = piecesText,
                onValueChange = { piecesText = it },
                label = stringResource(R.string.split_piece_count),
                keyboardType = KeyboardType.Number,
            )
        } else {
            if (byIngredient) {
                ChoiceField(
                    label = stringResource(R.string.split_part_which),
                    options = listOf<String?>(null) + ingredients.map { it.ingredientId },
                    selected = chosen?.ingredientId,
                    optionLabel = { id -> ingredients.firstOrNull { it.ingredientId == id }?.name ?: stringResource(R.string.split_part_choose) },
                    onSelect = { ingredientId = it },
                )
            }
            FormField(
                value = scaleText,
                onValueChange = { scaleText = it },
                label = stringResource(R.string.split_on_scale),
                keyboardType = KeyboardType.Decimal,
            )
            CheckRow(label = stringResource(R.string.split_in_pot), checked = inPot, onChange = { inPot = it })
            if (inPot) {
                FormField(
                    value = potText,
                    onValueChange = { potText = it },
                    label = stringResource(R.string.split_empty_pot),
                    keyboardType = KeyboardType.Decimal,
                    problem = stringResource(R.string.split_pot_too_heavy).takeIf { potTooHeavy },
                )
                food?.let { Hint(stringResource(R.string.split_food_alone, formatNumber(it.roundToLong()))) }
            }
        }

        Text(stringResource(R.string.split_portions), style = MaterialTheme.typography.titleSmall)
        // The portions are said again as the figures typed change them.
        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            portions.forEachIndexed { index, portion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(portion.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when {
                                kcalMissing -> stringResource(R.string.split_share_unknown)
                                unchosen -> stringResource(R.string.split_part_unknown)
                                chosen != null -> stringResource(R.string.split_share, formatPercent(shares[index]))
                                portion.kcal != null -> stringResource(
                                    R.string.split_share_of_kcal,
                                    formatPercent(shares[index]),
                                    formatNumber(portion.kcal.roundToInt()),
                                )
                                else -> stringResource(R.string.split_share, formatPercent(shares[index]))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        amounts?.get(index)?.let { amount ->
                            if (way == Way.PIECES) {
                                pluralStringResource(R.plurals.split_pieces, amount.toInt(), formatNumber(amount))
                            } else {
                                stringResource(R.string.split_grams, formatNumber(amount))
                            }
                        } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                if (index < portions.lastIndex) HorizontalDivider()
            }
        }
        if (amounts != null && way != Way.PIECES) Hint(stringResource(R.string.split_tare))
        if (byIngredient) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.split_part_risk),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.split_risk),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/** One way of weighing, with what it is for under it, chosen like the others of its group. */
@Composable
private fun WayRow(label: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp, top = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The portions a dish cooked ahead is split into: its meals, named by their day and time of day, with the energy the plan gives each. */
@Composable
internal fun portionsOfCooking(meals: List<Pair<Meal, String>>): List<SplitPortion> {
    val locale = LocalConfiguration.current.locales[0]
    return meals.map { (meal, date) ->
        val day = LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) }
        SplitPortion(
            key = meal.id,
            label = stringResource(R.string.split_portion_of, day, mealTypeLabel(meal.slot).lowercase(locale)),
            kcal = meal.nutrition.calories,
            lines = splitLines(meal.lines),
        )
    }
}

/**
 * The portions a pot both people of a joint plan cook is split into: of each meal it is eaten at, this
 * plan's portion under [ownName] and the other person's under theirs, with the energy the plans give each.
 */
@Composable
internal fun portionsOfPot(meals: List<Pair<Meal, String>>, ownName: String): List<SplitPortion> {
    val locale = LocalConfiguration.current.locales[0]
    return meals.flatMap { (meal, date) ->
        val day = LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) }
        val whenEaten = stringResource(R.string.split_portion_of, day, mealTypeLabel(meal.slot).lowercase(locale))
        val other = meal.shared?.takeUnless { it.apart }?.with
        listOfNotNull(
            SplitPortion(
                key = meal.id,
                label = stringResource(R.string.split_portion_of_who, ownName, whenEaten),
                kcal = meal.nutrition.calories,
                lines = splitLines(meal.lines),
            ),
            other?.let {
                SplitPortion(
                    key = it.mealId,
                    label = stringResource(R.string.split_portion_of_who, it.profileName, whenEaten),
                    kcal = it.calories,
                    lines = splitLines(it.lines),
                )
            },
        )
    }
}

/**
 * The window a dish cooked ahead, or a pot both people cook, is split in, into the portions of the plan, by the
 * plan's shares: their energy, or what each takes of a part weighed alone.
 */
@Composable
internal fun SplitDialog(portions: List<SplitPortion>, onDismiss: () -> Unit) {
    ScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.split_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hint(stringResource(if (splitsByParts(portions)) R.string.split_plan_intro_parts else R.string.split_plan_intro))
                SplitCalculator(portions)
            }
        }
    }
}

/** A cooked dish split on its own, into portions chosen here: what the plans' screen leads to. */
@Composable
fun SplitScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.split_page_title),
            subtitle = stringResource(R.string.split_page_intro),
            onBack = onBack,
        )
        AppCard { SplitCalculator(planned = null) }
    }
}

/** What every portion takes of each ingredient; null unless each says so. */
private fun linesOf(portions: List<SplitPortion>): List<List<SplitLine>>? =
    portions.map { it.lines }.takeIf { all -> all.isNotEmpty() && all.all { it != null } }?.map { it!! }

/** Whether a part weighed alone can be split by what each portion takes of it. */
internal fun splitsByParts(portions: List<SplitPortion>): Boolean = linesOf(portions)?.let(::ingredientsOf).orEmpty().isNotEmpty()

/** What a meal takes of each ingredient, exactly, as a split goes by it. */
private fun splitLines(lines: List<MealLine>): List<SplitLine> = lines.map { SplitLine(it.ingredientId, it.name, it.exact) }
