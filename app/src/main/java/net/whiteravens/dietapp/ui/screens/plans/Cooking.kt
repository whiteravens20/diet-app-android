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
internal fun apartNote(meal: Meal, date: String, today: String): String? {
    val others = meal.cooking?.with.orEmpty()
    val other = others.firstOrNull()
    if (meal.cookAhead == null || other == null || other.eaten || other.date < today) return null
    if (meal.cookAhead == "cooked" && others.size > 1) return null
    val whenItIs = capitalised(
        if (other.date == date) mealWord(R.array.plans_cooking_meal_on, other.slot) else dayWord(R.array.plans_cooking_day_on, other.date),
    )
    return stringResource(if (meal.cookAhead == "cook") R.string.plans_cooks_separately else R.string.plans_cooks_for_one, whenItIs)
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

/** What to cook of a meal: its own lines, or those of the whole of a cooking on the meal it is cooked at, and how many servings they are. */
internal data class MealToCook(val lines: List<MealLine>, val servings: Double, val whole: Boolean)

internal fun mealToCook(plan: MealPlan, meal: Meal): MealToCook {
    val shown = { servings: Double -> (servings * 10).roundToInt() / 10.0 }
    val cooking = meal.cooking.takeIf { meal.cookAhead == "cook" } ?: return MealToCook(meal.lines, shown(meal.servings), false)
    val others = cooking.with.map { it.mealId }
    val servings = plan.days.flatMap { it.meals }.filter { it.id in others }.sumOf { it.servings } + meal.servings
    return MealToCook(cooking.lines, shown(servings), true)
}
