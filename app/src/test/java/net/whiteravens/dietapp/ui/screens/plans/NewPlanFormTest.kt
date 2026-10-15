// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.DayAsk
import net.whiteravens.dietapp.domain.FavoriteSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A new plan's form as it is chosen, and the plan the API is asked for from it, as the web's form asks. */
class NewPlanFormTest {
    private val monday = LocalDate.of(2026, 10, 12)
    private val form = NewPlanForm(startDate = monday)
    private val sets = listOf(FavoriteSet("s", "Weekday", mapOf("breakfast" to "oats", "lunch" to "soup", "snack" to "nuts")))

    @Test
    fun `an untouched form asks for a week from its day, the rest left to the profile`() {
        val ask = form.ask("p", profileMealCount = 3, sets = sets)!!

        assertEquals(7, ask.durationDays)
        assertEquals(monday, ask.startDate)
        assertNull(ask.dietType)
        assertNull(ask.mealCount)
        assertNull(ask.calorieTarget)
        assertNull(ask.maxRepeatsPerRecipe)
        assertTrue(ask.respectExclusions && ask.respectFavorites && ask.respectInventory)
        assertFalse(ask.mealPrepFriendly)
        assertEquals(emptyList<String>(), ask.allowRelaxations)
        assertEquals(emptyList<DayAsk>(), ask.days)
        assertEquals((0L..6L).map { monday.plusDays(it) }, form.dates)
    }

    @Test
    fun `figures outside the API's bounds are refused, and no plan is asked for`() {
        assertEquals(setOf(PlanField.DURATION), form.copy(durationDays = "29").problems())
        assertEquals(setOf(PlanField.DURATION), form.copy(durationDays = "0").problems())
        assertEquals(setOf(PlanField.CALORIE_TARGET), form.copy(calorieTarget = "700").problems())
        assertEquals(setOf(PlanField.MAX_REPEATS), form.copy(maxRepeats = "29").problems())
        assertNull(form.copy(durationDays = "").ask("p", 3, sets))
        assertEquals(emptyList<LocalDate>(), form.copy(durationDays = "x").dates)

        val tooMuch = form.copy(days = mapOf(monday to DayDraft(calorieTarget = "7000", cookTime = "601")))
        assertEquals(mapOf(monday to setOf(DayField.CALORIE_TARGET, DayField.COOK_TIME)), tooMuch.dayProblems())
        assertNull(tooMuch.ask("p", 3, sets))
        // A skipped day shows none of its fields, and none of them is held against the plan.
        assertEquals(emptyMap<LocalDate, Set<DayField>>(), tooMuch.copy(days = mapOf(monday to DayDraft(calorieTarget = "7000", kind = "skip"))).dayProblems())
    }

    @Test
    fun `only a day that differs from the plan is sent, with what differs`() {
        val tuesday = monday.plusDays(1)
        val wednesday = monday.plusDays(2)
        val chosen = form.copy(
            days = mapOf(
                monday to DayDraft(),
                tuesday to DayDraft(kind = "rest", calorieTarget = "1800", cookTime = "0", useUpBy = true),
                wednesday to DayDraft(kind = "skip", mealCount = 5, calorieTarget = "2000", setId = "s"),
            ),
        )

        val days = chosen.ask("p", 3, sets)!!.days

        assertEquals(
            listOf(
                // A nought for the cooking time leaves it to the plan, as an empty field does.
                DayAsk(tuesday, calorieTarget = 1800, dayType = "rest", useUpBy = true),
                DayAsk(wednesday, skip = true),
            ),
            days,
        )
    }

    @Test
    fun `a day is given its set's recipes for the meals it has, and a meal locked by hand goes before the set's`() {
        val day = DayDraft(setId = "s", lockSlot = "lunch", lockRecipeId = "salad")

        assertEquals(mapOf("breakfast" to "oats", "lunch" to "salad"), day.locks(sets, planMealCount = 3))
        // Four meals a day have a snack, which the set has a recipe for.
        assertEquals(mapOf("breakfast" to "oats", "lunch" to "salad", "snack" to "nuts"), day.copy(mealCount = 4).locks(sets, planMealCount = 3))
        assertEquals(mapOf("breakfast" to "oats"), DayDraft(setId = "s", mealCount = 2).locks(sets, planMealCount = 3))

        val asked = form.copy(days = mapOf(monday to day)).ask("p", profileMealCount = 3, sets = sets)!!
        assertEquals(listOf(DayAsk(monday, locks = mapOf("breakfast" to "oats", "lunch" to "salad"))), asked.days)
    }

    @Test
    fun `what the plan is made for where it differs from the profile is sent, and the plan's meals decide a day's locks`() {
        val chosen = form.copy(
            durationDays = "3",
            dietType = "vegan",
            mealCount = 4,
            calorieTarget = " 1900 ",
            mealPrepFriendly = true,
            respectFavorites = false,
            maxRepeats = "2",
            mayGiveWay = listOf("cook_time", "weekly_cap"),
            days = mapOf(monday to DayDraft(setId = "s")),
        )

        val ask = chosen.ask("p", profileMealCount = 3, sets = sets)!!

        assertEquals(3, ask.durationDays)
        assertEquals("vegan", ask.dietType)
        assertEquals(4, ask.mealCount)
        assertEquals(1900, ask.calorieTarget)
        assertTrue(ask.mealPrepFriendly)
        assertFalse(ask.respectFavorites)
        assertEquals(2, ask.maxRepeatsPerRecipe)
        assertEquals(listOf("cook_time", "weekly_cap"), ask.allowRelaxations)
        assertEquals(mapOf("breakfast" to "oats", "lunch" to "soup", "snack" to "nuts"), ask.days.single().locks)
    }
}
