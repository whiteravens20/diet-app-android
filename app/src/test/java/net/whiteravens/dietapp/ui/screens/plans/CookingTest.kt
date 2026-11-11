// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.CookedPlace
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealCooking
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanDay
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import net.whiteravens.dietapp.domain.OtherPortion
import net.whiteravens.dietapp.domain.SharedMeal
import net.whiteravens.dietapp.domain.LeftoverOf

/** What a meal takes to cook: its own lines, or the whole of a cooking on the meal it is cooked at. */
class CookingTest {
    private val own = listOf(MealLine("oats", "Oats", 60.0, "g"))
    private val whole = listOf(MealLine("oats", "Oats", 130.0, "g"))

    private fun meal(id: String, servings: Double, cookAhead: String?, cooking: MealCooking?) = Meal(
        id = id,
        slot = "breakfast",
        name = "Porridge",
        recipeId = "recipe",
        servings = servings,
        eaten = false,
        offDiet = false,
        nutrition = Nutrition(300.0, 10.0, 8.0, 50.0),
        lines = own,
        allergens = emptyList(),
        conflicts = emptyList(),
        disliked = emptyList(),
        custom = false,
        quantityScale = 1.0,
        cookAhead = cookAhead,
        byModel = null,
        outsideLimits = emptyList(),
        onlyRecipe = false,
        cooking = cooking,
    )

    private fun plan(vararg meals: Meal): MealPlan {
        val day = mockk<PlanDay>()
        every { day.meals } returns meals.toList()
        val plan = mockk<MealPlan>()
        every { plan.days } returns listOf(day)
        return plan
    }

    /** A plan of several days, each its date and its meals. */
    private fun planOf(vararg days: Pair<String, List<Meal>>): MealPlan {
        val plan = mockk<MealPlan>()
        every { plan.days } returns days.map { (date, meals) ->
            mockk<PlanDay>().also { day ->
                every { day.date } returns date
                every { day.meals } returns meals
            }
        }
        return plan
    }

    @Test
    fun `the meal it is cooked at takes what all the meals of its cooking take`() {
        val cook = meal("first", 1.0, "cook", MealCooking(listOf(CookedPlace("second", "2026-11-04", "breakfast", false)), whole))
        val second = meal("second", 1.35, "cooked", MealCooking(listOf(CookedPlace("first", "2026-11-03", "breakfast", false)), whole))

        assertEquals(MealToCook(whole, 2.4, true), mealToCook(plan(cook, second), cook))
    }

    @Test
    fun `the meal eaten from it takes what it holds`() {
        val cook = meal("first", 1.0, "cook", MealCooking(listOf(CookedPlace("second", "2026-11-04", "breakfast", false)), whole))
        val second = meal("second", 1.25, "cooked", MealCooking(listOf(CookedPlace("first", "2026-11-03", "breakfast", false)), whole))

        assertEquals(MealToCook(own, 1.3, false), mealToCook(plan(cook, second), second))
    }

    @Test
    fun `a meal cooked for itself takes what it holds`() {
        val alone = meal("alone", 1.0, null, null)

        assertEquals(MealToCook(own, 1.0, false), mealToCook(plan(alone), alone))
    }

    @Test
    fun `a dish cooked ahead is split into the meal it is cooked at and those eaten from it, in the plan's order, each with its day`() {
        val cook = meal("first", 1.0, "cook", MealCooking(listOf(CookedPlace("second", "2026-11-04", "breakfast", false)), whole))
        val second = meal("second", 1.35, "cooked", MealCooking(listOf(CookedPlace("first", "2026-11-03", "breakfast", false)), whole))
        val other = meal("other", 1.0, null, null)
        val plan = planOf("2026-11-03" to listOf(other, cook), "2026-11-04" to listOf(second))

        assertEquals(listOf(cook to "2026-11-03", second to "2026-11-04"), mealsOfCooking(plan, cook))
    }

    @Test
    fun `nothing is split from a meal eaten from a dish cooked earlier, or cooked for itself`() {
        val cook = meal("first", 1.0, "cook", MealCooking(listOf(CookedPlace("second", "2026-11-04", "breakfast", false)), whole))
        val second = meal("second", 1.35, "cooked", MealCooking(listOf(CookedPlace("first", "2026-11-03", "breakfast", false)), whole))
        val alone = meal("alone", 1.0, null, null)
        val plan = planOf("2026-11-03" to listOf(cook, alone), "2026-11-04" to listOf(second))

        assertEquals(emptyList<Pair<Meal, String>>(), mealsOfCooking(plan, second))
        assertEquals(emptyList<Pair<Meal, String>>(), mealsOfCooking(plan, alone))
    }

    @Test
    fun `a dish both people of a joint plan cook in one pot takes what the pot takes, both portions of each meal of its cooking, and one cooked apart its own`() {
        val pot = listOf(MealLine("oats", "Oats", 260.0, "g"))
        val shared = SharedMeal("s", apart = false, with = OtherPortion("theirs", "plan-2", "Ewa", 0.8, 240.0, eaten = false), pot = pot)
        val alone = meal("alone", 1.2, null, null).copy(shared = shared)
        val apart = alone.copy(shared = shared.copy(apart = true))
        val cook = meal("first", 1.0, "cook", MealCooking(listOf(CookedPlace("second", "2026-11-04", "breakfast", false)), whole)).copy(shared = shared)
        val second = meal("second", 1.3, "cooked", MealCooking(listOf(CookedPlace("first", "2026-11-03", "breakfast", false)), whole))
            .copy(shared = shared.copy(with = shared.with.copy(mealId = "theirs-2", servings = 1.0)))

        assertEquals(MealToCook(pot, 2.0, true, PotPortions(1.2, "Ewa", 0.8)), mealToCook(plan(alone), alone))
        assertEquals(MealToCook(own, 1.2, false), mealToCook(plan(apart), apart))
        assertEquals(MealToCook(pot, 4.1, true, PotPortions(2.3, "Ewa", 1.8)), mealToCook(planOf("2026-11-03" to listOf(cook), "2026-11-04" to listOf(second)), cook))
    }

    @Test
    fun `a leftover shared by both takes what it holds, as it was cooked already`() {
        val pot = listOf(MealLine("oats", "Oats", 260.0, "g"))
        val shared = SharedMeal("s", apart = false, with = OtherPortion("theirs", "plan-2", "Ewa", 0.8, 240.0, eaten = false), pot = pot)
        val leftover = meal("left", 0.6, null, null).copy(shared = shared, leftover = LeftoverOf("2026-11-03", "dinner", undoable = true))

        assertEquals(MealToCook(own, 0.6, false), mealToCook(plan(leftover), leftover))
    }
}
