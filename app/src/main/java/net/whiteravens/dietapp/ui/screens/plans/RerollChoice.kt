// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.PlanDay

/** How much of a plan is made again: all of it, what is left of it from today, the days from one to another, or one day. */
enum class RerollScope { WHOLE, FROM_TODAY, RANGE, DAY }

/**
 * What the user chose in the window a plan is made again in: how much of it ([from] and [to] for
 * the days from one to another, [day] for one day), and what of it may go that is otherwise left
 * alone. Days are `yyyy-MM-dd`, which sort as text.
 */
data class RerollChoice(
    val scope: RerollScope,
    val from: String,
    val to: String,
    val day: String,
    val includePast: Boolean = false,
    val replaceEaten: Boolean = false,
    val replaceCustom: Boolean = false,
)

/**
 * What making a plan again would do to it as it stands: the days that get other meals and the
 * meals that give place to others, and what is kept as it is: the days not asked for, those that
 * have passed, those the user skips, and the meals eaten, of the user's own or locked to their recipe.
 */
data class RerollOutcome(
    val days: Int = 0,
    val replaced: Int = 0,
    val keptOutside: Int = 0,
    val keptPast: Int = 0,
    val keptSkipped: Int = 0,
    val keptEaten: Int = 0,
    val keptCustom: Int = 0,
    val keptLocked: Int = 0,
)

/** Which of what a re-roll leaves alone the days of a choice hold at all: there is nothing to ask about the rest. */
data class Removable(val past: Boolean, val eaten: Boolean, val custom: Boolean)

/** Why "from today on" is not on offer for a plan. */
enum class FromTodayRefusal { NOT_BEGUN, OVER }

/** The reading of a re-roll the API makes when the request arrives, as the web's window makes it before. */
object Rerolls {
    /** The first and the last day a choice asks for. */
    fun spanOf(days: List<PlanDay>, choice: RerollChoice, today: String): Pair<String, String> {
        val first = days.firstOrNull()?.date.orEmpty()
        val last = days.lastOrNull()?.date.orEmpty()
        return when (choice.scope) {
            RerollScope.DAY -> choice.day to choice.day
            RerollScope.RANGE -> choice.from to choice.to
            RerollScope.FROM_TODAY -> maxOf(today, first) to last
            RerollScope.WHOLE -> first to last
        }
    }

    /** Why "from today on" is not on offer: the plan has not begun, so that is all of it, or it is over; null where it is. */
    fun fromTodayRefused(days: List<PlanDay>, today: String): FromTodayRefusal? {
        val first = days.firstOrNull()?.date.orEmpty()
        val last = days.lastOrNull()?.date.orEmpty()
        return when {
            today <= first -> FromTodayRefusal.NOT_BEGUN
            today > last -> FromTodayRefusal.OVER
            else -> null
        }
    }

    /**
     * What a choice would do: a day before today stays unless the choice says otherwise; on a day
     * that is filled again a meal of the user's own and a meal marked as eaten stay unless the choice
     * says otherwise, and a meal locked to its recipe gets that recipe again.
     */
    fun outcome(days: List<PlanDay>, choice: RerollChoice, today: String): RerollOutcome {
        val (first, last) = spanOf(days, choice, today)
        var outcome = RerollOutcome()
        for (day in days) {
            outcome = when {
                day.date < first || day.date > last -> outcome.copy(keptOutside = outcome.keptOutside + 1)
                day.date < today && !choice.includePast -> outcome.copy(keptPast = outcome.keptPast + 1)
                day.skipped -> outcome.copy(keptSkipped = outcome.keptSkipped + 1)
                else -> {
                    var replaced = 0
                    for (meal in day.meals) {
                        val locked = meal.recipeId != null && day.locks[meal.slot] == meal.recipeId
                        when {
                            meal.custom && !choice.replaceCustom -> outcome = outcome.copy(keptCustom = outcome.keptCustom + 1)
                            meal.eaten && !choice.replaceEaten -> outcome = outcome.copy(keptEaten = outcome.keptEaten + 1)
                            locked -> outcome = outcome.copy(keptLocked = outcome.keptLocked + 1)
                            else -> replaced += 1
                        }
                    }
                    // A day whose every meal stays is not changed by being asked for.
                    val changed = replaced > 0 || day.meals.isEmpty()
                    outcome.copy(replaced = outcome.replaced + replaced, days = outcome.days + if (changed) 1 else 0)
                }
            }
        }
        return outcome
    }

    /** What of the days a choice asks for could go that is otherwise left alone. */
    fun whatCouldGo(days: List<PlanDay>, choice: RerollChoice, today: String): Removable {
        val (first, last) = spanOf(days, choice, today)
        val asked = days.filter { it.date in first..last }
        val filled = asked.filter { !it.skipped && (choice.includePast || it.date >= today) }
        return Removable(
            past = asked.any { it.date < today },
            eaten = filled.any { day -> day.meals.any { it.eaten } },
            custom = filled.any { day -> day.meals.any { it.custom } },
        )
    }
}
