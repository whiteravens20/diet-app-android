// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealPlan

/** The words a plan is written out with, in the language of whoever reads it. */
data class PlanWords(
    /** The heads of the columns: date, meal, dish, servings, kilocalories, protein, fat, carbohydrate, eaten. */
    val head: List<String>,
    /** What each meal of the day is called, by its code. */
    val meals: Map<String, String>,
    /** What a meal of the user's own is called when it was given no name. */
    val ownMeal: String,
    val yes: String,
    val no: String,
    /** What stands in the place of the meals of a day the plan skips. */
    val skipped: String,
) {
    fun meal(code: String): String = meals[code] ?: code
}

/** What a meal is called: the title of its recipe, or the name the user gave a meal of their own. */
fun dishOf(meal: Meal, words: PlanWords): String = meal.name.ifEmpty { words.ownMeal }

/** The servings a meal is, with what a rebalance left on it. */
fun servingsOf(meal: Meal): Double = Math.round(meal.servings * meal.quantityScale * 100) / 100.0

/**
 * A plan as a table for a spreadsheet: a row for each meal, under a row of heads, with every number
 * the API worked out for it. A day the plan skips has one row that says so. Nothing is worked out here.
 */
fun planTable(plan: MealPlan, words: PlanWords): List<List<Any?>> =
    listOf(words.head) + plan.days.flatMap { day ->
        if (day.skipped) {
            listOf(listOf(day.date, words.skipped, null, null, null, null, null, null, null))
        } else {
            day.meals.map { meal ->
                listOf(
                    day.date,
                    words.meal(meal.slot),
                    dishOf(meal, words),
                    servingsOf(meal),
                    meal.nutrition.calories,
                    meal.nutrition.protein,
                    meal.nutrition.fat,
                    meal.nutrition.carbs,
                    if (meal.eaten) words.yes else words.no,
                )
            }
        }
    }

/** The last day of a plan, as the API writes days. */
fun lastDayOf(plan: MealPlan): String = plan.days.lastOrNull()?.date ?: plan.startDate

/** The name a plan taken out of the app is kept under: its first and its last day. */
fun exportName(plan: MealPlan): String = "meal-plan-${plan.startDate}_${lastDayOf(plan)}"
