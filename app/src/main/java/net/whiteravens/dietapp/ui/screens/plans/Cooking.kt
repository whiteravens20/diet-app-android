// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.annotation.ArrayRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.CookedPlace
import net.whiteravens.dietapp.domain.CookingChange
import net.whiteravens.dietapp.domain.MEAL_TYPES
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.MealPlace
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.ui.common.formatList
import net.whiteravens.dietapp.ui.common.mealTypeLabel
import net.whiteravens.dietapp.ui.components.CheckRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** The meals of a day in the order the arrays of their names hold them. */
private val MEALS_IN_ARRAYS = listOf("breakfast", "second_breakfast", "lunch", "snack", "dinner")

/** The name of a day from one of the arrays, which begin with Monday. */
@Composable
private fun dayWord(@ArrayRes words: Int, date: String): String =
    stringArrayResource(words)[LocalDate.parse(date).dayOfWeek.value - 1]

/** The name of a meal from one of the arrays. */
@Composable
private fun mealWord(@ArrayRes words: Int, slot: String): String =
    stringArrayResource(words).getOrElse(MEALS_IN_ARRAYS.indexOf(slot)) { slot }

@Composable
private fun capitalised(text: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return text.replaceFirstChar { it.titlecase(locale) }
}

/**
 * The meal of its cooking that a mark speaks of: for a meal cooked earlier the one it was cooked at,
 * which comes first; for the one to cook a meal of another day where there is one, as the day says
 * more than the meal.
 */
private fun Meal.partner(date: String): CookedPlace? {
    val others = cooking?.with.orEmpty().takeIf { cookAhead != null }.orEmpty()
    return (if (cookAhead == "cook") others.firstOrNull { it.date != date } else null) ?: others.firstOrNull()
}

/** The mark on a meal of the day of [date] cooked with another; null for a meal cooked for itself. */
@Composable
internal fun cookChip(meal: Meal, date: String): String? {
    val other = meal.partner(date) ?: return null
    return when {
        meal.cookAhead == "cook" -> stringResource(if (other.date == date) R.string.plans_cook_meals else R.string.plans_cook_days)
        other.date == date -> capitalised(mealWord(R.array.plans_cooking_meal_from, other.slot))
        else -> capitalised(dayWord(R.array.plans_cooking_day_from, other.date))
    }
}

/** The mark of a leftover: the day it was left on. */
@Composable
internal fun leftoverChip(meal: Meal): String? =
    meal.leftover?.let { capitalised(dayWord(R.array.plans_cooking_day_from, it.date)) }

/** What a leftover is, under it; or, under the meal it was left of, where its rest is eaten. */
@Composable
internal fun leftoverHint(meal: Meal): String? {
    meal.leftover?.let {
        return stringResource(
            R.string.plans_leftover_from_hint,
            mealWord(R.array.plans_leftover_meal_of, it.slot),
            dayWord(R.array.plans_cooking_day_from, it.date),
        )
    }
    return meal.carriedTo?.let {
        stringResource(
            R.string.plans_leftover_carried_hint,
            dayWord(R.array.plans_cooking_day_on, it.date),
            mealWord(R.array.plans_cooking_meal_on, it.slot),
        )
    }
}

/** When a meal of [date] at [slot] is eaten, as a sentence says it: "on Wednesday" and "for lunch". */
@Composable
internal fun eatenWhen(date: String, slot: String): Pair<String, String> =
    dayWord(R.array.plans_cooking_day_on, date) to mealWord(R.array.plans_cooking_meal_on, slot)

/** What the mark means. */
@Composable
internal fun cookHint(meal: Meal, date: String): String? {
    val other = meal.partner(date) ?: return null
    val oneDay = other.date == date
    return if (meal.cookAhead == "cook") {
        stringResource(
            R.string.plans_cook_hint,
            if (oneDay) mealWord(R.array.plans_cooking_meal_for, meal.slot) else dayWord(R.array.plans_cooking_day_for, date),
            if (oneDay) mealWord(R.array.plans_cooking_meal_for, other.slot) else dayWord(R.array.plans_cooking_day_for, other.date),
        )
    } else {
        stringResource(
            R.string.plans_from_hint,
            if (oneDay) mealWord(R.array.plans_cooking_meal_from, other.slot) else dayWord(R.array.plans_cooking_day_from, other.date),
        )
    }
}

/** What to do where the day the dish was to be cooked on is over and nothing says it was. */
@Composable
internal fun notCookedNote(meal: Meal, date: String, today: String): String? {
    val other = meal.partner(date) ?: return null
    if (meal.cookAhead != "cooked" || other.eaten || other.date >= today || date < today) return null
    return stringResource(R.string.plans_not_cooked, dayWord(R.array.plans_cooking_day_on, other.date))
}

/** What follows for the other meal when this one leaves the cooking. Of a dish cooked for more than two, the rest are cooked once all the same. */
@Composable
internal fun apartNote(meal: Meal, date: String, today: String): String? =
    apartNote(meal.cookAhead, meal.cooking?.with.orEmpty(), date, today)

/** What follows for the other meals of a cooking ([cookAhead], [others]) when a meal of [date] leaves it. */
@Composable
internal fun apartNote(cookAhead: String?, others: List<CookedPlace>, date: String, today: String): String? {
    val other = others.firstOrNull()
    if (cookAhead == null || other == null || other.eaten || other.date < today) return null
    if (cookAhead == "cooked" && others.size > 1) return null
    val whenItIs = capitalised(
        if (other.date == date) mealWord(R.array.plans_cooking_meal_on, other.slot) else dayWord(R.array.plans_cooking_day_on, other.date),
    )
    return stringResource(if (cookAhead == "cook") R.string.plans_cooks_separately else R.string.plans_cooks_for_one, whenItIs)
}

/** A day at its shortest: "Tue", "wt.". */
@Composable
internal fun weekdayShort(date: String): String =
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE", LocalConfiguration.current.locales[0]))

/**
 * What a change of a dish does to the other meals of its cooking, said before the choice, with the
 * tick that keeps it to what was asked for; [onlyLabel] is what the tick is called.
 */
@Composable
internal fun CookingNote(
    change: CookingChange?,
    only: Boolean,
    onOnly: (Boolean) -> Unit,
    onlyLabel: Int = R.string.plans_cooking_only,
) {
    if (change == null) return
    val locale = LocalConfiguration.current.locales[0]

    @Composable
    fun places(meals: List<MealPlace>): String = formatList(
        meals.sortedWith(compareBy({ it.date }, { MEAL_TYPES.indexOf(it.slot) }))
            .map { "${weekdayShort(it.date)} ${mealTypeLabel(it.slot).lowercase(locale)}" },
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (change.changes.isNotEmpty()) {
            if (!only) Hint(stringResource(R.string.plans_cooking_also, places(change.changes)))
            CheckRow(label = stringResource(onlyLabel), checked = only, onChange = onOnly)
        }
        if (change.stays.isNotEmpty()) Hint(stringResource(R.string.plans_cooking_stays, places(change.stays)))
    }
}

/** The meals one cooking is eaten at, in the plan's order, with their days: what a dish cooked ahead is split into. None for a meal that is not cooked ahead. */
internal fun mealsOfCooking(plan: MealPlan, meal: Meal): List<Pair<Meal, String>> {
    val cooking = meal.cooking.takeIf { meal.cookAhead == "cook" } ?: return emptyList()
    val ids = setOf(meal.id) + cooking.with.map { it.mealId }
    return plan.days.flatMap { day -> day.meals.filter { it.id in ids }.map { it to day.date } }
}

/**
 * What to cook of a meal: its own lines, or those of the whole of a cooking on the meal it is cooked at,
 * and how many servings they are; of a pot both people of a joint plan cook, the pot's lines, with
 * [pot] saying whose portions it holds.
 */
internal data class MealToCook(val lines: List<MealLine>, val servings: Double, val whole: Boolean, val pot: PotPortions? = null)

/**
 * The portions in a pot both people of a joint plan cook: this plan's, and the other person's by their name,
 * with the lines a plate of either takes otherwise than the recipe has it ([platesOf]).
 */
internal data class PotPortions(val mine: Double, val name: String, val theirs: Double, val plates: List<PlateRow> = emptyList())

/** A line of a dish both people of a joint plan eat on plates of their own: what each plate takes of it, as it is shown. */
internal data class PlateRow(val ingredientId: String, val name: String, val mine: MealLine, val theirs: MealLine)

/**
 * The lines of a dish both people eat that a plate of either holds otherwise than the recipe's proportion: what
 * each plate takes of them. None where both plates are the recipe's, and for a meal of one plan alone.
 */
/** What a person's plate of a shared dish holds more and less of than the recipe, by the names of its lines. */
@Composable
internal fun plateWords(who: String, lines: List<MealLine>): String {
    val more = lines.filter { (it.share ?: 1.0) > 1.0 }.map { it.name }
    val less = lines.filter { (it.share ?: 1.0) < 1.0 }.map { it.name }
    val changes = listOfNotNull(
        more.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.plans_plate_more, formatList(it)) },
        less.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.plans_plate_less, formatList(it)) },
    )
    return stringResource(
        R.string.plans_plate_line,
        who,
        changes.joinToString("; ").ifEmpty { stringResource(R.string.plans_plate_as_recipe) },
    )
}

internal fun platesOf(meal: Meal): List<PlateRow> {
    val theirs = meal.shared?.with?.lines.orEmpty()
    return meal.lines.mapIndexedNotNull { index, line ->
        val other = theirs.getOrNull(index)?.takeIf { it.ingredientId == line.ingredientId } ?: return@mapIndexedNotNull null
        if (line.share == null && other.share == null) null else PlateRow(line.ingredientId, line.name, line, other)
    }
}

internal fun mealToCook(plan: MealPlan, meal: Meal): MealToCook {
    val shown = { servings: Double -> (servings * 10).roundToInt() / 10.0 }
    // A dish both cook in one pot takes what the pot takes: both portions of each meal it is eaten at. A leftover was cooked already.
    meal.shared?.takeUnless { it.apart || meal.leftover != null }?.let { shared ->
        val cooked = mealsOfCooking(plan, meal).map { it.first }.ifEmpty { listOf(meal) }
        val mine = cooked.sumOf { it.servings }
        val theirs = cooked.sumOf { it.shared?.with?.servings ?: 0.0 }
        // The plates of one meal: a pot cooked for several holds as many plates of each.
        val plates = if (cooked.size == 1) platesOf(meal) else emptyList()
        return MealToCook(shared.pot, shown(mine + theirs), true, PotPortions(shown(mine), shared.with.profileName, shown(theirs), plates))
    }
    val cooking = meal.cooking.takeIf { meal.cookAhead == "cook" } ?: return MealToCook(meal.lines, shown(meal.servings), false)
    val others = cooking.with.map { it.mealId }
    val servings = plan.days.flatMap { it.meals }.filter { it.id in others }.sumOf { it.servings } + meal.servings
    return MealToCook(cooking.lines, shown(servings), true)
}
