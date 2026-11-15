// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.every
import io.mockk.mockk
import net.whiteravens.dietapp.domain.FreezerStock
import net.whiteravens.dietapp.domain.FrozenRest
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.PlanPacks
import net.whiteravens.dietapp.domain.Thaw
import org.junit.Assert.assertEquals
import org.junit.Test

/** On which day of a plan what goes in the freezer and what comes out of it is said, as the web's page says it. */
class FreezerTest {
    private fun meal(id: String, slot: String) = mockk<Meal>().also { meal ->
        every { meal.id } returns id
        every { meal.slot } returns slot
    }

    /** Three days from Monday, a lunch on each; Monday's opens a pack of chicken whose rest Wednesday takes 100 g of. */
    private fun plan(frozen: List<FrozenRest>, fromFreezer: List<FreezerStock> = emptyList()): MealPlan {
        val days = listOf("2026-01-05", "2026-01-06", "2026-01-07").mapIndexed { index, date ->
            mockk<PlanDay>().also { day ->
                every { day.date } returns date
                every { day.meals } returns listOf(meal("lunch-$index", "lunch"))
            }
        }
        return mockk<MealPlan>().also { plan ->
            every { plan.days } returns days
            every { plan.packs } returns PlanPacks(500.0, 250.0, emptyList(), frozen, fromFreezer)
        }
    }

    private val chicken = FrozenRest(
        ingredientId = "chicken",
        name = "Chicken breast",
        unit = "g",
        date = "2026-01-05",
        mealId = "lunch-0",
        amount = 350,
        thawed = listOf(Thaw("2026-01-07", "lunch-2", 100)),
        left = 250,
        inPantry = false,
    )

    @Test
    fun `a meal's take from the freezer is said the day before it, from today on`() {
        val said = thawsByDay(plan(listOf(chicken)), today = "2026-01-05")

        assertEquals(mapOf("2026-01-06" to DayThaws(forTomorrow = listOf(ThawLine("Chicken breast", "g", 100, "2026-01-07", "lunch")))), said)
    }

    @Test
    fun `a meal whose day before is over, or is no day of the plan, has its take said on its own day`() {
        assertEquals(
            mapOf("2026-01-07" to DayThaws(forToday = listOf(ThawLine("Chicken breast", "g", 100, "2026-01-07", "lunch")))),
            thawsByDay(plan(listOf(chicken)), today = "2026-01-07"),
        )
        // What the freezer held comes out whole, for its first meal: on the plan's first day, that day.
        val stock = FreezerStock("salmon", "Salmon", "g", "2026-01-05", "lunch-0", 286, 150)
        assertEquals(
            mapOf("2026-01-05" to DayThaws(forToday = listOf(ThawLine("Salmon", "g", 286, "2026-01-05", "lunch")))),
            thawsByDay(plan(emptyList(), listOf(stock)), today = "2026-01-05"),
        )
    }

    @Test
    fun `a rest goes in with the meal that opens its pack, or with its day where that meal is the other plan's`() {
        val other = chicken.copy(ingredientId = "salmon", mealId = "their-lunch")
        val monday = plan(listOf(chicken, other)).days[0]

        val (byMeal, ofDay) = frozenOfDay(plan(listOf(chicken, other)), monday)

        assertEquals(mapOf("lunch-0" to listOf(chicken)), byMeal)
        assertEquals(listOf(other), ofDay)
        assertEquals(emptyMap<String, List<FrozenRest>>() to emptyList<FrozenRest>(), frozenOfDay(plan(listOf(chicken)), plan(listOf(chicken)).days[1]))
    }
}
