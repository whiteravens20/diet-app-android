// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.testing.fixture
import net.whiteravens.dietapp.ui.common.CsvDialect
import net.whiteravens.dietapp.ui.common.csvOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** A plan taken out of the app as a table for a spreadsheet. */
class PlanTableTest {
    private val captured = AppModule.json().decodeFromString<MealPlanDto>(fixture("meal-plan")).toDomain()
    private val words = PlanWords(
        head = listOf("Date", "Meal", "Dish", "Servings", "kcal", "Protein (g)", "Fat (g)", "Carbohydrate (g)", "Eaten"),
        meals = mapOf("breakfast" to "Breakfast", "lunch" to "Lunch"),
        ownMeal = "Custom meal",
        yes = "Yes",
        no = "No",
        skipped = "Skipped",
    )

    /** Two days of the captured plan: on the first a meal a rebalance scaled and that was eaten, and a meal of the user's own with no name; the second skipped. */
    private val plan = captured.days.first().let { day ->
        val eaten = day.meals.first { !it.custom }.copy(
            slot = "breakfast",
            name = "Oats, \"overnight\"",
            servings = 1.5,
            quantityScale = 1.25,
            eaten = true,
            nutrition = Nutrition(412.4, 20.25, 10.0, 55.5),
        )
        val own = eaten.copy(
            id = "own",
            slot = "lunch",
            name = "",
            recipeId = null,
            custom = true,
            servings = 1.0,
            quantityScale = 1.0,
            eaten = false,
            nutrition = Nutrition(300.0, 12.0, 8.0, 40.0),
            lines = emptyList(),
        )
        captured.copy(
            startDate = "2026-10-13",
            days = listOf(
                day.copy(date = "2026-10-13", meals = listOf(eaten, own), skipped = false),
                day.copy(id = "day-2", date = "2026-10-14", meals = emptyList(), skipped = true),
            ),
        )
    }

    @Test
    fun `a plan is a row a meal under a row of heads, the servings with what a rebalance left on them, and a skipped day one row that says so`() {
        assertEquals(
            listOf(
                words.head,
                listOf("2026-10-13", "Breakfast", "Oats, \"overnight\"", 1.88, 412.4, 20.25, 10.0, 55.5, "Yes"),
                listOf("2026-10-13", "Lunch", "Custom meal", 1.0, 300.0, 12.0, 8.0, 40.0, "No"),
                listOf("2026-10-14", "Skipped", null, null, null, null, null, null, null),
            ),
            planTable(plan, words),
        )
        assertEquals("meal-plan-2026-10-13_2026-10-14", exportName(plan))
    }

    @Test
    fun `the table for a Polish spreadsheet parts its cells with semicolons and writes its decimals with a comma`() {
        assertEquals(
            "\uFEFFDate;Meal;Dish;Servings;kcal;Protein (g);Fat (g);Carbohydrate (g);Eaten\r\n" +
                "2026-10-13;Breakfast;\"Oats, \"\"overnight\"\"\";1,88;412,4;20,25;10;55,5;Yes\r\n" +
                "2026-10-13;Lunch;Custom meal;1;300;12;8;40;No\r\n" +
                "2026-10-14;Skipped;;;;;;;\r\n",
            csvOf(planTable(plan, words), CsvDialect.of("pl")),
        )
    }
}
