// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.ShoppingListDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.testing.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A plan as a running backend answered it, as the phone shows it: a meal is the share of its
 * recipe the plan gives it, so its lines are the meal's own amounts; the recipe as written belongs
 * to the recipe's screen alone.
 */
class MealMappingTest {
    private val json = AppModule.json()
    private val plan = json.decodeFromString<MealPlanDto>(fixture("meal-plan"))

    @Test
    fun `a meal's lines are the amounts the plan gives it, not the recipe's as written`() {
        val meal = plan.days.flatMap { it.meals }.first { meal ->
            val recipe = meal.recipe ?: return@first false
            meal.ingredients.map { it.display.quantity } != recipe.ingredients.map { it.display.quantity }
        }

        val lines = meal.toDomain().lines

        assertEquals(meal.ingredients.map { MealLine(it.name, it.display.quantity, it.display.unit) }, lines)
        assertNotEquals(meal.recipe!!.ingredients.map { it.display.quantity }, lines.map { it.quantity })
    }

    @Test
    fun `a meal names the ingredients the profile would rather not eat, and what it has ruled out since`() {
        val planned = plan.days.first().meals.first { it.recipe != null }
        val meal = planned.copy(
            dislikedIngredientIds = listOf(planned.ingredients.first().ingredientId),
            restrictionConflicts = listOf("allergen"),
        ).toDomain()

        assertEquals(listOf(planned.ingredients.first().name), meal.disliked)
        assertEquals(listOf("allergen"), meal.conflicts)
    }

    @Test
    fun `a day says where it stands against its target, and a list says when the plan has changed under it`() {
        val day = plan.days.first().copy(offTarget = "over", belowSafetyFloor = true).toDomain()
        val list = json.decodeFromString<ShoppingListDto>(fixture("shopping-list")).copy(stale = true).toDomain()

        assertEquals("over", day.offTarget)
        assertTrue(day.belowSafetyFloor)
        assertTrue(list.stale)
    }
}
