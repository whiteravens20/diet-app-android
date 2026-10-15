// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What making a plan again would do to it, read as the API reads the request when it arrives, as the web's window reads it. */
class RerollChoiceTest {
    private val nothing = Nutrition(0.0, 0.0, 0.0, 0.0)

    private fun meal(slot: String, recipe: String? = "r-$slot", eaten: Boolean = false, custom: Boolean = false) = Meal(
        id = "m-$slot",
        slot = slot,
        name = slot,
        recipeId = recipe,
        servings = 1.0,
        eaten = eaten,
        offDiet = false,
        nutrition = nothing,
        lines = emptyList(),
        allergens = emptyList(),
        conflicts = emptyList(),
        disliked = emptyList(),
        custom = custom,
        quantityScale = 1.0,
        cookAhead = null,
        byModel = null,
        outsideLimits = emptyList(),
        onlyRecipe = false,
    )

    private fun day(date: String, meals: List<Meal> = listOf(meal("breakfast"), meal("lunch")), skipped: Boolean = false, locks: Map<String, String> = emptyMap()) =
        PlanDay("d-$date", date, meals, nothing, 2000.0, 0.0, skipped, null, false, Macros(0.0, 0.0, 0.0), locks)

    // A week from Monday the 12th; today is the 14th.
    private val today = "2026-10-14"
    private val days = listOf(
        day("2026-10-12"),
        day("2026-10-13", listOf(meal("breakfast", eaten = true), meal("lunch"))),
        day("2026-10-14", listOf(meal("breakfast", eaten = true), meal("lunch", recipe = null, custom = true), meal("dinner"))),
        day("2026-10-15", locks = mapOf("lunch" to "r-lunch")),
        day("2026-10-16", skipped = true, meals = emptyList()),
        day("2026-10-17"),
    )

    private fun choice(scope: RerollScope, from: String = "2026-10-12", to: String = "2026-10-17", day: String = "2026-10-15") = RerollChoice(scope, from, to, day)

    @Test
    fun `the whole plan leaves the days that have passed, a skipped day, and the meals eaten, of one's own or locked`() {
        val outcome = Rerolls.outcome(days, choice(RerollScope.WHOLE), today)

        assertEquals(RerollOutcome(days = 3, replaced = 4, keptPast = 2, keptSkipped = 1, keptEaten = 1, keptCustom = 1, keptLocked = 1), outcome)
    }

    @Test
    fun `what is otherwise left alone goes where the user lets it`() {
        val all = choice(RerollScope.WHOLE).copy(includePast = true, replaceEaten = true, replaceCustom = true)

        assertEquals(RerollOutcome(days = 5, replaced = 10, keptSkipped = 1, keptLocked = 1), Rerolls.outcome(days, all, today))
    }

    @Test
    fun `a day asked for alone leaves the others outside, and one whose every meal stays is not changed`() {
        assertEquals(RerollOutcome(days = 1, replaced = 1, keptOutside = 5, keptLocked = 1), Rerolls.outcome(days, choice(RerollScope.DAY), today))

        val locked = listOf(day("2026-10-15", meals = listOf(meal("lunch")), locks = mapOf("lunch" to "r-lunch")))
        assertEquals(0, Rerolls.outcome(locked, choice(RerollScope.DAY), today).days)
    }

    @Test
    fun `from today on begins on the plan's first day where that is later, and is no choice for a plan that has not begun or is over`() {
        assertEquals(today to "2026-10-17", Rerolls.spanOf(days, choice(RerollScope.FROM_TODAY), today))
        assertEquals("2026-10-12" to "2026-10-17", Rerolls.spanOf(days, choice(RerollScope.FROM_TODAY), "2026-10-01"))

        assertNull(Rerolls.fromTodayRefused(days, today))
        assertEquals(FromTodayRefusal.NOT_BEGUN, Rerolls.fromTodayRefused(days, "2026-10-12"))
        assertEquals(FromTodayRefusal.OVER, Rerolls.fromTodayRefused(days, "2026-10-18"))
    }

    @Test
    fun `only what the days chosen hold is asked about`() {
        assertEquals(Removable(past = true, eaten = true, custom = true), Rerolls.whatCouldGo(days, choice(RerollScope.WHOLE), today))
        // The eaten breakfast of the 13th has passed: it is asked about only once the days that have passed may go.
        assertEquals(Removable(past = true, eaten = false, custom = false), Rerolls.whatCouldGo(days, choice(RerollScope.RANGE, to = "2026-10-13"), today))
        assertEquals(
            Removable(past = true, eaten = true, custom = false),
            Rerolls.whatCouldGo(days, choice(RerollScope.RANGE, to = "2026-10-13").copy(includePast = true), today),
        )
        assertEquals(Removable(past = false, eaten = false, custom = false), Rerolls.whatCouldGo(days, choice(RerollScope.DAY, day = "2026-10-17"), today))
    }
}
