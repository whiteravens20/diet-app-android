// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import net.whiteravens.dietapp.data.remote.GenerationReportDto
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.ModelPickDto
import net.whiteravens.dietapp.data.remote.RelaxedMealDto
import net.whiteravens.dietapp.data.remote.ShoppingListDto
import net.whiteravens.dietapp.data.remote.SingleCandidateDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.ModelPart
import net.whiteravens.dietapp.testing.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

        val lines = meal.toDomain(plan.days.first { meal in it.meals }.date, plan.report).lines

        assertEquals(meal.ingredients.map { MealLine(it.name, it.display.quantity, it.display.unit) }, lines)
        assertNotEquals(meal.recipe!!.ingredients.map { it.display.quantity }, lines.map { it.quantity })
    }

    @Test
    fun `a meal names the ingredients the profile would rather not eat, and what it has ruled out since`() {
        val planned = plan.days.first().meals.first { it.recipe != null }
        val meal = planned.copy(
            dislikedIngredientIds = listOf(planned.ingredients.first().ingredientId),
            restrictionConflicts = listOf("allergen"),
        ).toDomain(plan.days.first().date, plan.report)

        assertEquals(listOf(planned.ingredients.first().name), meal.disliked)
        assertEquals(listOf("allergen"), meal.conflicts)
    }

    @Test
    fun `a day says where it stands against its target, and a list says when the plan has changed under it`() {
        val day = plan.days.first().copy(offTarget = "over", belowSafetyFloor = true).toDomain(plan.report)
        val list = json.decodeFromString<ShoppingListDto>(fixture("shopping-list")).copy(stale = true).toDomain()

        assertEquals("over", day.offTarget)
        assertTrue(day.belowSafetyFloor)
        assertTrue(list.stale)
    }

    @Test
    fun `what the generation said of a meal is said by the meal of that day and time of day, and never by a meal of one's own`() {
        val day = plan.days[1]
        val meal = day.meals.first { it.recipe != null }
        val report = GenerationReportDto(
            relaxations = listOf(RelaxedMealDto(day.date, meal.mealType, listOf("weekly_cap"))),
            singleCandidate = listOf(SingleCandidateDto(day.date, meal.mealType)),
            pantry = "counted",
        )

        val said = plan.copy(report = report).toDomain()

        val placed = said.days[1].meals.single { it.id == meal.id }
        assertEquals(listOf("weekly_cap"), placed.outsideLimits)
        assertTrue(placed.onlyRecipe)
        // The same time of day on another day was not spoken of.
        assertTrue(said.days[0].meals.filter { it.slot == meal.mealType }.all { it.outsideLimits.isEmpty() && !it.onlyRecipe })
        assertEquals("counted", said.pantry)

        val own = meal.copy(source = "USER_CUSTOM", recipe = null, customName = null).toDomain(day.date, report)
        assertTrue(own.custom)
        assertEquals(emptyList<String>(), own.outsideLimits)
        assertFalse(own.onlyRecipe)
        assertEquals("", own.name)
    }

    @Test
    fun `a model's pick of a meal goes before what it had to do with the recipe, and a plan made with one says so`() {
        val day = plan.days.first()
        val planned = day.meals.first { it.recipe != null }
        val written = planned.copy(recipe = planned.recipe!!.copy(byModel = "written"))

        assertNull(planned.toDomain(day.date, null).byModel)
        assertEquals(ModelPart.Written, written.toDomain(day.date, null).byModel)
        assertEquals(ModelPart.Picked("Light and quick"), written.copy(modelPick = ModelPickDto("Light and quick")).toDomain(day.date, null).byModel)
        assertTrue(plan.copy(generationMode = "ai_assisted").toDomain().byModel)
        assertFalse(plan.toDomain().byModel)
    }

    @Test
    fun `a plan carries its targets, its packs, the limits it may go outside of, and how many of its meals the profile has ruled out since`() {
        val day = plan.days.first()
        val ruledOut = day.copy(meals = day.meals.mapIndexed { i, meal -> if (i < 2) meal.copy(restrictionConflicts = listOf("avoided")) else meal })

        val mapped = plan.copy(days = listOf(ruledOut) + plan.days.drop(1), options = plan.options.copy(allowRelaxations = listOf("cook_time"))).toDomain()

        assertEquals(2, mapped.conflicts)
        assertEquals(listOf("cook_time"), mapped.mayGiveWay)
        assertEquals(plan.targetMacros.protein, mapped.targetMacros.protein, 0.0)
        assertEquals(day.targetMacros.carbs, mapped.days.first().targetMacros.carbs, 0.0)
        assertEquals(plan.packs.open.map { it.name to it.wasted }, mapped.packs.open.map { it.name to it.wasted })
        assertEquals(plan.ingredientReuseScore, mapped.ingredientReuse, 0.0)
    }

    @Test
    fun `a meal balanced by a factor or cooked ahead says so`() {
        val day = plan.days.first()

        val meal = day.meals.first().copy(quantityScale = 1.25, cookAhead = "cook").toDomain(day.date, null)

        assertEquals(1.25, meal.quantityScale, 0.0)
        assertEquals("cook", meal.cookAhead)
    }
}

