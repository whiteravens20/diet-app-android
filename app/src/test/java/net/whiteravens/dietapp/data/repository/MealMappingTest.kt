// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import net.whiteravens.dietapp.data.remote.GenerationReportDto
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.ModelPickDto
import net.whiteravens.dietapp.data.remote.RecipeSourceDto
import net.whiteravens.dietapp.data.remote.RelaxedMealDto
import net.whiteravens.dietapp.data.remote.ShoppingListDto
import net.whiteravens.dietapp.data.remote.SingleCandidateDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.ModelPart
import net.whiteravens.dietapp.domain.RecipeSource
import net.whiteravens.dietapp.testing.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import net.whiteravens.dietapp.data.remote.JointPlanDto
import net.whiteravens.dietapp.data.remote.OtherPortionDto
import net.whiteravens.dietapp.data.remote.SharedMealOfDto
import net.whiteravens.dietapp.domain.JointPlan
import net.whiteravens.dietapp.domain.OtherPortion
import net.whiteravens.dietapp.data.remote.CookedPlaceDto
import net.whiteravens.dietapp.data.remote.LeftoverOfDto
import net.whiteravens.dietapp.data.remote.LeftoverOfferDto
import net.whiteravens.dietapp.data.remote.LeftoverOtherDto
import net.whiteravens.dietapp.data.remote.LeftoverPlaceDto
import net.whiteravens.dietapp.data.remote.LeftoverPortionDto
import net.whiteravens.dietapp.data.remote.MealPlaceDto
import net.whiteravens.dietapp.domain.CookedPlace
import net.whiteravens.dietapp.domain.LeftoverOf
import net.whiteravens.dietapp.domain.LeftoverOffer
import net.whiteravens.dietapp.domain.LeftoverOther
import net.whiteravens.dietapp.domain.LeftoverPlace
import net.whiteravens.dietapp.domain.MealPlace
import net.whiteravens.dietapp.domain.Portion
import net.whiteravens.dietapp.data.remote.DisplayAmountDto
import net.whiteravens.dietapp.data.remote.MealToppingDto
import net.whiteravens.dietapp.data.remote.NutritionDto
import net.whiteravens.dietapp.data.remote.PlannedIngredientDto
import net.whiteravens.dietapp.domain.MealTopping
import net.whiteravens.dietapp.domain.Nutrition

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

        assertEquals(meal.ingredients.map { MealLine(it.ingredientId, it.name, it.display.quantity, it.display.unit) }, lines)
        assertNotEquals(meal.recipe!!.ingredients.map { it.display.quantity }, lines.map { it.quantity })
    }

    @Test
    fun `a meal names the topping on this person's plate, in its amount as shown, and keeps the dish's own figures`() {
        val day = plan.days.first { it.meals.any { meal -> meal.recipe != null } }
        val dto = day.meals.first { it.recipe != null }
        val topping = MealToppingDto(
            ingredient = PlannedIngredientDto("t-1", "Turkey breast", 150.0, "g", DisplayAmountDto(150.0, "g"), null),
            nutrition = NutritionDto(171.0, 35.0, 3.0, 0.0),
        )

        val meal = dto.copy(topping = topping).toDomain(day.date, plan.report)

        assertEquals(MealTopping(MealLine("t-1", "Turkey breast", 150.0, "g"), Nutrition(171.0, 35.0, 3.0, 0.0)), meal.topping)
        assertEquals(dto.toDomain(day.date, plan.report).nutrition, meal.nutrition)
        assertNull(dto.toDomain(day.date, plan.report).topping)
    }

    @Test
    fun `a meal's line says what the recipe says of it`() {
        val planned = plan.days.first().meals.first { it.recipe != null && it.ingredients.size > 1 }
        val meal = planned.copy(
            ingredients = planned.ingredients.mapIndexed { index, line -> if (index == 0) line.copy(note = "diced") else line },
        ).toDomain(plan.days.first().date, plan.report)

        assertEquals(listOf("diced", null), meal.lines.take(2).map { it.note })
    }

    @Test
    fun `a meal says where its recipe is from and that a model had a part in it`() {
        val planned = plan.days.first().meals.first { it.recipe != null }
        val meal = planned.copy(
            recipe = planned.recipe!!.copy(source = RecipeSourceDto("https://recipes.example/soup", "CC BY 4.0"), byModel = "changed"),
        ).toDomain(plan.days.first().date, plan.report)

        assertEquals(RecipeSource("https://recipes.example/soup", "CC BY 4.0"), meal.recipeSource)
        assertTrue(meal.recipeByModel)
    }

    @Test
    fun `a meal of a recipe written here, by nobody but people, says no source and no model's part`() {
        val planned = plan.days.first().meals.first { it.recipe != null }
        val meal = planned.copy(recipe = planned.recipe!!.copy(source = null, byModel = null)).toDomain(plan.days.first().date, plan.report)

        assertNull(meal.recipeSource)
        assertFalse(meal.recipeByModel)
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
    fun `a plan says whether it wastes less and the diet it names itself, and one from before says it does not`() {
        val before = plan.toDomain()
        assertFalse(before.lessWaste)
        assertNull(before.ownDiet)

        val made = plan.copy(options = plan.options.copy(lessWaste = true, dietType = "keto")).toDomain()
        assertTrue(made.lessWaste)
        assertEquals("keto", made.ownDiet)
    }

    @Test
    fun `a meal balanced by a factor or cooked ahead says so`() {
        val day = plan.days.first()

        val meal = day.meals.first().copy(quantityScale = 1.25, cookAhead = "cook").toDomain(day.date, null)

        assertEquals(1.25, meal.quantityScale, 0.0)
        assertEquals("cook", meal.cookAhead)
    }

    @Test
    fun `a plan made together says with whom and which times of day were planned apart, and a plan of one person says neither`() {
        val joint = plan.copy(
            joint = JointPlanDto(planId = "plan-2", profileId = "ewa", profileName = "Ewa"),
            report = GenerationReportDto(relaxations = emptyList(), singleCandidate = emptyList(), pantry = "empty", apart = listOf("snack")),
        ).toDomain()

        assertEquals(JointPlan("plan-2", "ewa", "Ewa"), joint.joint)
        assertEquals(listOf("snack"), joint.apart)
        assertNull(plan.toDomain().joint)
        assertTrue(plan.toDomain().apart.isEmpty())
    }

    @Test
    fun `a shared meal says the other person's portion and what the pot takes, and every other meal shares nothing`() {
        val first = plan.days.first()
        val meal = first.meals.first { it.recipe != null }
        val pot = meal.ingredients.map { it.copy(quantity = it.quantity * 2) }
        val joint = plan.copy(
            days = plan.days.map { day ->
                if (day != first) day else day.copy(
                    meals = day.meals.map {
                        if (it.id != meal.id) it else it.copy(
                            shared = SharedMealOfDto(
                                id = "shared-1",
                                apart = false,
                                with = OtherPortionDto(mealId = "meal-9", planId = "plan-2", profileName = "Ewa", servings = 1.1, calories = 540.0, eaten = true),
                                pot = pot,
                            ),
                        )
                    },
                )
            },
        ).toDomain()

        val shared = joint.days.first().meals.first { it.id == meal.id }.shared!!
        assertEquals(OtherPortion("meal-9", "plan-2", "Ewa", 1.1, 540.0, eaten = true), shared.with)
        assertEquals(pot.map { it.display.quantity }, shared.pot.map { it.quantity })
        assertTrue(joint.days.flatMap { it.meals }.filter { it.id != meal.id }.all { it.shared == null })
    }

    @Test
    fun `a leftover says where it was left, the meal it was left of where its rest is eaten, and a meal whether its rest can go to the next day`() {
        val first = plan.days.first()
        val (leftover, source, other) = first.meals.filter { it.recipe != null }.take(3)
        val marked = plan.copy(
            days = plan.days.map { day ->
                if (day != first) day else day.copy(
                    meals = day.meals.map {
                        when (it.id) {
                            leftover.id -> it.copy(leftover = LeftoverOfDto("2026-10-12", "dinner", undoable = true))
                            source.id -> it.copy(carriedTo = MealPlaceDto("2026-10-14", "lunch"))
                            other.id -> it.copy(leftoverOffer = "no_day")
                            else -> it
                        }
                    },
                )
            },
        ).toDomain()

        val meals = marked.days.first().meals.associateBy { it.id }
        assertEquals(LeftoverOf("2026-10-12", "dinner", undoable = true), meals.getValue(leftover.id).leftover)
        assertEquals(MealPlace("2026-10-14", "lunch"), meals.getValue(source.id).carriedTo)
        assertEquals("no_day", meals.getValue(other.id).leftoverOffer)
        assertNull(meals.getValue(other.id).leftover)
        assertNull(meals.getValue(leftover.id).carriedTo)
    }

    @Test
    fun `where a leftover can go is read as the API offers it`() {
        val offer = LeftoverOfferDto(
            planId = "plan-2",
            date = "2026-10-14",
            portion = LeftoverPortionDto(0.8, 412.0),
            places = listOf(
                LeftoverPlaceDto(
                    mealType = "lunch",
                    name = "Bean soup",
                    calories = 600.0,
                    sharedWith = "Ewa",
                    cookAhead = "cook",
                    cookedWith = listOf(CookedPlaceDto("meal-5", "2026-10-15", "lunch", eaten = false)),
                ),
            ),
            other = LeftoverOtherDto("Ewa", eaten = true, portion = LeftoverPortionDto(0.6, 300.0), places = listOf("lunch")),
        ).toDomain()

        assertEquals(
            LeftoverOffer(
                planId = "plan-2",
                date = "2026-10-14",
                portion = Portion(0.8, 412.0),
                places = listOf(
                    LeftoverPlace("lunch", "Bean soup", 600.0, "Ewa", "cook", listOf(CookedPlace("meal-5", "2026-10-15", "lunch", eaten = false))),
                ),
                other = LeftoverOther("Ewa", eaten = true, portion = Portion(0.6, 300.0), places = listOf("lunch")),
            ),
            offer,
        )
    }
}
