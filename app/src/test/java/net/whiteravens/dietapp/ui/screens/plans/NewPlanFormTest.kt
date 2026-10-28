// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.data.remote.PublicConfigDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.DayAsk
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.FieldRefusal.TooBig
import net.whiteravens.dietapp.domain.FieldRefusal.TooSmall
import net.whiteravens.dietapp.testing.fixture
import net.whiteravens.dietapp.testing.refusal
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

    /** The meals of a day at each number of meals, as a running backend says them (`config.json`). */
    private val mealSlots = AppModule.json().decodeFromString<PublicConfigDto>(fixture("config")).plans.mealSlots
        .mapKeys { (count, _) -> count.toInt() }

    @Test
    fun `an untouched form asks for a week from its day, the rest left to the profile`() {
        val ask = form.ask("p", profileMealCount = 3, sets = sets, mealSlots)!!

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
    fun `a field that holds no whole number asks for no plan, and whole numbers are sent for the API to say what it takes`() {
        assertEquals(setOf(PlanField.DURATION), form.copy(durationDays = "").problems())
        assertEquals(setOf(PlanField.CALORIE_TARGET, PlanField.MAX_REPEATS), form.copy(calorieTarget = "lots", maxRepeats = "2.5").problems())
        assertNull(form.copy(durationDays = "").ask("p", 3, sets, mealSlots))
        assertEquals(emptyList<LocalDate>(), form.copy(durationDays = "x").dates)
        val unread = form.copy(days = mapOf(monday to DayDraft(calorieTarget = "x", cookTime = "1,5")))
        assertEquals(mapOf(monday to setOf(DayField.CALORIE_TARGET, DayField.COOK_TIME)), unread.dayProblems())
        assertNull(unread.ask("p", 3, sets, mealSlots))
        // A skipped day shows none of its fields, and none of them counts.
        assertEquals(emptyMap<LocalDate, Set<DayField>>(), unread.copy(days = mapOf(monday to DayDraft(calorieTarget = "x", kind = "skip"))).dayProblems())

        val outOfBounds = form.copy(durationDays = "29", calorieTarget = "700", maxRepeats = "29")
        assertEquals(emptySet<PlanField>(), outOfBounds.problems())
        assertEquals(29, outOfBounds.ask("p", 3, sets, mealSlots)!!.durationDays)
        // A number typed by mistake draws no more days than the form draws.
        assertEquals(emptyList<LocalDate>(), form.copy(durationDays = "100000").dates)
    }

    @Test
    fun `the API's refusal names the plan's fields and a day's by the day's place among the days sent, until each is changed`() {
        // As the API's answer had it: forty days, a target of 100, fifty repeats, and a first day sent with 7000 kcal and 700 minutes.
        val tuesday = monday.plusDays(1)
        val chosen = form.copy(durationDays = "40", calorieTarget = "100", maxRepeats = "50", days = mapOf(tuesday to DayDraft(calorieTarget = "7000", cookTime = "700")))
        val ask = chosen.ask("p", 3, sets, mealSlots)!!

        val refusals = PlanRefusals.of(refusal("plan-refused").fieldRefusals, ask)

        assertEquals(
            mapOf(
                PlanField.DURATION to TooBig(28.0, inclusive = true),
                PlanField.CALORIE_TARGET to TooSmall(800.0, inclusive = true),
                PlanField.MAX_REPEATS to TooBig(28.0, inclusive = true),
            ),
            refusals.plan,
        )
        assertEquals(mapOf(tuesday to mapOf(DayField.CALORIE_TARGET to TooBig(6000.0, inclusive = true), DayField.COOK_TIME to TooBig(600.0, inclusive = true))), refusals.days)
        assertEquals(emptySet<String>(), refusals.elsewhere)

        val changed = refusals.after(chosen, chosen.copy(durationDays = "28", days = mapOf(tuesday to DayDraft(calorieTarget = "7000", cookTime = "60"))))
        assertEquals(setOf(PlanField.CALORIE_TARGET, PlanField.MAX_REPEATS), changed.plan.keys)
        assertEquals(mapOf(tuesday to setOf(DayField.CALORIE_TARGET)), changed.days.mapValues { it.value.keys })
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

        val days = chosen.ask("p", 3, sets, mealSlots)!!.days

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

        assertEquals(mapOf("breakfast" to "oats", "lunch" to "salad"), day.locks(sets, planMealCount = 3, mealSlots))
        // Four meals a day have a snack, which the set has a recipe for.
        assertEquals(mapOf("breakfast" to "oats", "lunch" to "salad", "snack" to "nuts"), day.copy(mealCount = 4).locks(sets, planMealCount = 3, mealSlots))
        assertEquals(mapOf("breakfast" to "oats"), DayDraft(setId = "s", mealCount = 2).locks(sets, planMealCount = 3, mealSlots))

        val asked = form.copy(days = mapOf(monday to day)).ask("p", profileMealCount = 3, sets = sets, mealSlots)!!
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

        val ask = chosen.ask("p", profileMealCount = 3, sets = sets, mealSlots)!!

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
