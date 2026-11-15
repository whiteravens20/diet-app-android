// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.FrozenRest
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.Thaw

/** What a meal takes out of the freezer, as a day of the plan says it: [slot] null for a meal of the other plan of the kitchen. */
data class ThawLine(val name: String, val unit: String, val amount: Int, val date: String, val slot: String?)

/** What a day says to take out of the freezer: for the next day's meals, and for its own where no day before it is to come. */
data class DayThaws(val forTomorrow: List<ThawLine> = emptyList(), val forToday: List<ThawLine> = emptyList()) {
    val any: Boolean get() = forTomorrow.isNotEmpty() || forToday.isNotEmpty()
}

/**
 * What each day of [plan] says to take out of the freezer, a whole portion each, by its date: the portions of the rests
 * it freezes and the portion of each food the freezer held when it was made. A meal's is said on the day before it
 * where that is a day of the plan from [today] on, and on its own day otherwise, as the web's page says it.
 */
fun thawsByDay(plan: MealPlan, today: String): Map<String, DayThaws> {
    val dates = plan.days.map { it.date }
    val slotOf = plan.days.flatMap { day -> day.meals.map { it.id to it.slot } }.toMap()
    val said = mutableMapOf<String, DayThaws>()
    val taken = plan.packs.fromFreezer.map { held -> Triple(held.name, held.unit, Thaw(held.date, held.mealId, held.amount)) } +
        plan.packs.frozen.flatMap { rest -> rest.thawed.map { Triple(rest.name, rest.unit, it) } }
    for ((name, unit, thaw) in taken) {
        val at = dates.indexOf(thaw.date)
        if (at < 0) continue
        val line = ThawLine(name, unit, thaw.amount, thaw.date, thaw.mealId?.let(slotOf::get))
        val before = dates.getOrNull(at - 1)
        if (before != null && before >= today) {
            val day = said[before] ?: DayThaws()
            said[before] = day.copy(forTomorrow = day.forTomorrow + line)
        } else {
            val day = said[thaw.date] ?: DayThaws()
            said[thaw.date] = day.copy(forToday = day.forToday + line)
        }
    }
    return said
}

/** Of the rests [plan] freezes on [day], those said with a meal of the day, by its id, and those of a meal of the other plan of the kitchen. */
fun frozenOfDay(plan: MealPlan, day: PlanDay): Pair<Map<String, List<FrozenRest>>, List<FrozenRest>> {
    val meals = day.meals.map { it.id }.toSet()
    val ofDay = plan.packs.frozen.filter { it.date == day.date }
    val (withMeal, without) = ofDay.partition { it.mealId != null && it.mealId in meals }
    return withMeal.groupBy { it.mealId!! } to without
}
