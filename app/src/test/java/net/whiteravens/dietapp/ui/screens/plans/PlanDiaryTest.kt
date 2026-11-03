// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the diary's entries of meals a plan's day has none of stand among its meals. */
class PlanDiaryTest {
    private fun meal(id: String, slot: String) = Meal(
        id = id,
        slot = slot,
        name = id,
        recipeId = "recipe-$id",
        servings = 1.0,
        eaten = false,
        offDiet = false,
        nutrition = Nutrition(300.0, 10.0, 8.0, 50.0),
        lines = emptyList(),
        allergens = emptyList(),
        conflicts = emptyList(),
        disliked = emptyList(),
        custom = false,
        quantityScale = 1.0,
        cookAhead = null,
        byModel = null,
        outsideLimits = emptyList(),
        onlyRecipe = false,
        cooking = null,
    )

    private fun entry(id: String, slot: String) = DiaryEntry(id, slot, "meal", id, 1.0, null, 200.0, null, false)

    private fun placed(meals: List<Meal>, vararg written: DiaryEntry) =
        entriesApart(meals, written.toList()).mapValues { (_, entries) -> entries.map { it.id } }

    @Test
    fun `an entry of a meal the day has none of stands before the first meal that comes after it, in the order of the day`() {
        val day = listOf(meal("porridge", "breakfast"), meal("stew", "dinner"))

        val apart = placed(day, entry("cake", "snack"), entry("soup", "dinner"), entry("apple", "second_breakfast"), entry("yoghurt", "breakfast"))

        // The soup and the yoghurt go under their own meals; the apple and the cake between the breakfast and the dinner.
        assertEquals(mapOf(1 to listOf("apple", "cake")), apart)
    }

    @Test
    fun `an entry of a meal that comes after every meal of the day stands after the last one`() {
        val day = listOf(meal("porridge", "breakfast"), meal("bowl", "lunch"))

        assertEquals(mapOf(-1 to listOf("cake", "soup")), placed(day, entry("soup", "dinner"), entry("cake", "snack")))
    }

    @Test
    fun `an entry of a meal that comes before every meal of the day stands before the first one`() {
        val day = listOf(meal("bowl", "lunch"), meal("stew", "dinner"))

        assertEquals(mapOf(0 to listOf("toast", "apple")), placed(day, entry("apple", "second_breakfast"), entry("toast", "breakfast")))
    }
}
