// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.OpenPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The packs a plan leaves something in, said one line a food as the web's page says them. */
class FoodsLeftTest {
    private fun pack(id: String, name: String, size: Double, left: Int) =
        OpenPack(id, name, "g", size, packs = 3, left = left, wasted = left > size / 5, date = "2026-01-05")

    @Test
    fun aFoodLeftOfInSeveralPacksIsOneLineInTheOrderTheServerGivesItsRestsAddedUp() {
        val open = listOf(
            pack("c", "Chicken breast", 500.0, 291),
            pack("t", "Turkey breast", 500.0, 230),
            pack("t", "Turkey breast", 500.0, 230),
            pack("s", "Spinach", 200.0, 51),
            pack("c", "Chicken breast", 500.0, 126),
            pack("c", "Chicken breast", 500.0, 126),
            pack("b", "Bacon", 150.0, 19),
            pack("s", "Spinach", 200.0, 10),
        )

        assertEquals(
            listOf(
                FoodLeft("Chicken breast", "g", 500.0, left = 543, packs = 3, much = 3),
                FoodLeft("Turkey breast", "g", 500.0, left = 460, packs = 2, much = 2),
                FoodLeft("Spinach", "g", 200.0, left = 61, packs = 2, much = 1),
                FoodLeft("Bacon", "g", 150.0, left = 19, packs = 1, much = 0),
            ),
            // The day before the packs are opened: none of them to give the pantry yet.
            foodsLeft(open, today = "2026-01-04"),
        )
    }

    @Test
    fun thePantryIsGivenThePacksOpenedByTodayThatItWasNotGivenAndSaysItWasGivenSome() {
        val chicken = pack("c", "Chicken breast", 500.0, 100)
        val monday = chicken.copy(left = 120)
        val tuesday = chicken.copy(date = "2026-01-06", left = 80, inPantry = true)
        val thursday = chicken.copy(date = "2026-01-08", left = 50)

        val food = foodsLeft(listOf(monday, tuesday, thursday), today = "2026-01-06").single()

        assertEquals(listOf(monday), food.toGive)
        assertTrue(food.given)
        // The day before the first is opened, nothing is given and nothing was.
        val before = foodsLeft(listOf(monday, thursday), today = "2026-01-04").single()
        assertEquals(emptyList<OpenPack>(), before.toGive)
        assertFalse(before.given)
    }

    @Test
    fun noPackLeftIsNoLine() {
        assertEquals(emptyList<FoodLeft>(), foodsLeft(emptyList()))
    }
}
