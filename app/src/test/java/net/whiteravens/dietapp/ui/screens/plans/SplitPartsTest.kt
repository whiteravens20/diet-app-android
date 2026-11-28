// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.Meal
import net.whiteravens.dietapp.domain.MealLine
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.OtherPortion
import net.whiteravens.dietapp.domain.Pot
import net.whiteravens.dietapp.domain.SharedMeal
import net.whiteravens.dietapp.domain.SplitLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a dish is split into: each meal's portion, the other person's of a pot both cook, the portions eaten already left
 * out, and what the pot holds beyond its meals' portions a portion of its own, in the dish's make-up.
 */
class SplitPartsTest {
    private val rice = MealLine("rice", "Rice", 80.0, "g", exact = 81.0)
    private val chicken = MealLine("chicken", "Chicken", 150.0, "g", exact = 152.0)

    private fun meal(
        id: String,
        servings: Double = 1.0,
        kcal: Double = 600.0,
        eaten: Boolean = false,
        shared: SharedMeal? = null,
        pot: Pot? = null,
        scale: Double = 1.0,
    ) = Meal(
        id = id,
        slot = "lunch",
        name = "Chicken with rice",
        recipeId = "recipe",
        servings = servings,
        eaten = eaten,
        offDiet = false,
        nutrition = Nutrition(kcal, 40.0, 15.0, 70.0),
        lines = listOf(rice, chicken),
        allergens = emptyList(),
        conflicts = emptyList(),
        disliked = emptyList(),
        custom = false,
        quantityScale = scale,
        cookAhead = "cook",
        byModel = null,
        outsideLimits = emptyList(),
        onlyRecipe = false,
        shared = shared,
        pot = pot,
    )

    private fun ewa(eaten: Boolean = false, apart: Boolean = false) =
        SharedMeal("s", apart, OtherPortion("hers", "plan-2", "Ewa", 1.0, 450.0, eaten, listOf(rice.copy(exact = 60.0), chicken.copy(exact = 90.0))), pot = emptyList())

    @Test
    fun `a dish cooked ahead is split into its meals not eaten yet, the eaten ones named apart`() {
        val parts = partsOfPot(listOf(meal("mon") to "2026-11-30", meal("tue", eaten = true) to "2026-12-01", meal("wed") to "2026-12-02"), "Paweł")

        assertEquals(listOf("mon", "wed"), parts.split.map { it.key })
        assertEquals(listOf("tue"), parts.eaten.map { it.key })
        // A dish of one person's names no one.
        assertEquals(listOf<String?>(null, null), parts.split.map { it.who })
        assertEquals(listOf(SplitLine("rice", "Rice", 81.0), SplitLine("chicken", "Chicken", 152.0)), parts.split.first().lines)
        assertNull(parts.rest)
    }

    @Test
    fun `a pot both people cook is split into both portions of each meal under each name, an eaten one left out, and one cooked apart into its own`() {
        val pot = partsOfPot(listOf(meal("mine", shared = ewa()) to "2026-11-30"), "Paweł")
        assertEquals(listOf("Paweł" to "mine", "Ewa" to "hers"), pot.split.map { it.who to it.key })
        assertEquals(listOf(600.0, 450.0), pot.split.map { it.kcal })

        val hersEaten = partsOfPot(listOf(meal("mine", shared = ewa(eaten = true)) to "2026-11-30"), "Paweł")
        assertEquals(listOf("mine"), hersEaten.split.map { it.key })
        assertEquals(listOf("Ewa"), hersEaten.eaten.map { it.who })

        val apart = partsOfPot(listOf(meal("mine", shared = ewa(apart = true)) to "2026-11-30"), "Paweł")
        assertEquals(listOf<String?>(null), apart.split.map { it.who })
    }

    @Test
    fun `what the pot holds beyond its meals' portions is a portion of its own, of the dish's make-up, the rests of its meals together`() {
        // Cooked for 1.5 servings, sized down to 1 since: half a serving stays; and of a meal sized to 0.8 (its servings as
        // eaten, a rebalance's 0.8 in them already) 0.2 stays, a quarter of its portion.
        val parts = partsOfPot(
            listOf(
                meal("cooked") to "2026-11-30",
                meal("first", servings = 1.0, kcal = 600.0, pot = Pot(cooked = 1.5, rest = 0.5, held = false, offer = "ok")) to "2026-12-01",
                meal("second", servings = 0.8, kcal = 480.0, pot = Pot(cooked = 1.0, rest = 0.2, held = false, offer = "ok"), scale = 0.8) to "2026-12-02",
            ),
            "Paweł",
        )

        val rest = parts.rest!!
        assertEquals(600.0 * 0.5 + 480.0 * 0.25, rest.kcal, 1e-9)
        assertEquals(listOf("rice", "chicken"), rest.lines.map { it.ingredientId })
        assertEquals(81.0 * 0.5 + 81.0 * 0.25, rest.lines[0].quantity, 1e-9)
        assertEquals(152.0 * 0.75, rest.lines[1].quantity, 1e-9)
        // Nothing stays in a pot whose meals take all of it.
        assertNull(partsOfPot(listOf(meal("first", pot = Pot(1.0, 0.0, held = false, offer = null)) to "2026-12-01"), "Paweł").rest)
    }

    @Test
    fun `a dish whose plates hold their parts otherwise than the recipe is split one part at a time, either person's plate`() {
        val mine = meal("mine", shared = ewa()).copy(lines = listOf(rice.copy(share = 1.2), chicken))
        assertTrue(partsOfPot(listOf(mine to "2026-11-30"), "Paweł").plated)
        val hers = ewa().let { it.copy(with = it.with.copy(lines = it.with.lines.map { line -> line.copy(share = 0.8) })) }
        assertTrue(partsOfPot(listOf(meal("mine", shared = hers) to "2026-11-30"), "Paweł").plated)
        // Both plates the recipe's: the whole dish is weighed.
        assertFalse(partsOfPot(listOf(meal("mine", shared = ewa()) to "2026-11-30"), "Paweł").plated)
    }
}
