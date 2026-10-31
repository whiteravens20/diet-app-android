// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.domain.DiaryAmount
import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.DiaryPlanMeal
import net.whiteravens.dietapp.domain.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What waits to be sent, as a day of a diary shows it over what was read. */
class WaitingOverDiaryTest {
    private val date = "2026-10-31"
    private val milk = DiaryEntry("milk", "snack", "ingredient", "Milk", null, DiaryAmount(250.0, "g", 250.0, "g"), 48.0, Macros(2.0, 4.0, 2.0), false)
    private val stew = DiaryEntry("stew", "lunch", "recipe", "Stew", 1.0, null, 300.0, Macros(10.0, 10.0, 40.0), false)

    private fun added(profileId: String = "a", onDate: String = date, slot: String = "dinner", key: String = "k", name: String = "Soup", recipeId: String? = null, ingredientId: String? = null, servings: Double? = null, quantity: Double? = null, unit: String? = null, pieceName: String? = null, calories: Double? = null) =
        QueuedChange.DiaryAdded(profileId, onDate, slot, name, recipeId = recipeId, servings = servings, ingredientId = ingredientId, quantity = quantity, unit = unit, pieceName = pieceName, calories = calories, key = key)

    @Test
    fun `an entry written comes after those read, under its key, with what is known of it`() {
        val shown = listOf(stew).entriesWithWaiting("a", date, listOf(added(servings = 2.0, calories = 300.0))).last()

        assertEquals("k", shown.id)
        assertEquals("meal", shown.kind)
        assertEquals(600.0, shown.calories)
        assertNull(shown.macros)
    }

    @Test
    fun `an entry of a recipe says its calories by its servings, one of an ingredient its amount alone, a piece as the ingredient calls it`() {
        val recipe = emptyList<DiaryEntry>().entriesWithWaiting("a", date, listOf(added(recipeId = "r", servings = 1.5, calories = 200.0))).single()
        assertEquals("recipe", recipe.kind)
        assertEquals(300.0, recipe.calories)

        val cloves = emptyList<DiaryEntry>().entriesWithWaiting("a", date, listOf(added(ingredientId = "i", quantity = 2.0, unit = "piece", pieceName = "clove"))).single()
        assertEquals("ingredient", cloves.kind)
        assertNull(cloves.calories)
        assertEquals(DiaryAmount(2.0, "piece", 2.0, "clove"), cloves.amount)
        assertNull(cloves.servings)
    }

    @Test
    fun `an entry of another day or of another profile is not shown here`() {
        val waiting = listOf(added(onDate = "2026-10-30"), added(profileId = "b"))

        assertEquals(listOf(stew), listOf(stew).entriesWithWaiting("a", date, waiting))
    }

    @Test
    fun `an entry moved to another meal keeps its figures, one of another amount leaves them to the server`() {
        val moved = listOf(stew).entriesWithWaiting("a", date, listOf(QueuedChange.DiaryChanged("a", "stew", slot = "dinner"))).single()
        assertEquals("dinner", moved.slot)
        assertEquals(300.0, moved.calories)

        val served = listOf(stew).entriesWithWaiting("a", date, listOf(QueuedChange.DiaryChanged("a", "stew", servings = 2.0))).single()
        assertEquals(2.0, served.servings)
        assertNull(served.calories)
        assertNull(served.macros)

        val more = listOf(milk).entriesWithWaiting("a", date, listOf(QueuedChange.DiaryChanged("a", "milk", quantity = 300.0))).single()
        assertEquals(DiaryAmount(300.0, "g", 300.0, "g"), more.amount)
        assertNull(more.calories)
    }

    @Test
    fun `an entry taken out is left out, and changes are shown in the order they were made`() {
        val waiting = listOf(QueuedChange.DiaryChanged("a", "stew", slot = "dinner"), QueuedChange.DiaryRemoved("a", "stew"))

        assertEquals(listOf(milk), listOf(stew, milk).entriesWithWaiting("a", date, waiting))
    }

    private val first = DiaryPlanMeal("m", "lunch", "Stew", "r", false, 500.0)
    private val second = DiaryPlanMeal("n", "dinner", "Soup", "s", false, 400.0)

    @Test
    fun `a meal of the plan is marked as it waits to be, of this plan alone`() {
        val meals = listOf(first, second).mealsWithWaiting("p", date, listOf(QueuedChange.Eaten("p", "m", true), QueuedChange.Eaten("other", "n", true)))

        assertTrue(meals[0].eaten)
        assertFalse(meals[1].eaten)
    }

    @Test
    fun `a meal taken off its day is left out, and one of one's own comes on its day alone, in the place of what it replaces`() {
        fun own(onDate: String = date, replaces: String? = null) = QueuedChange.OwnMealAdded("p", onDate, "Mine", "lunch", 200.0, 1.0, 1.0, 1.0, 2.0, replaces, "own")

        assertEquals(listOf(second), listOf(first, second).mealsWithWaiting("p", date, listOf(QueuedChange.MealRemoved("p", "m"))))
        assertEquals(listOf(first, second), listOf(first, second).mealsWithWaiting("p", date, listOf(own(onDate = "2026-10-30"))))
        val beside = listOf(first, second).mealsWithWaiting("p", date, listOf(own()))
        assertEquals(listOf("m", "n", "own"), beside.map { it.id })
        assertEquals(400.0, beside.last().calories)
        assertEquals(listOf("n", "own"), listOf(first, second).mealsWithWaiting("p", date, listOf(own(replaces = "m"))).map { it.id })
    }

    @Test
    fun `the sum leaves out what waits of this day, of what it read and of what is to come`() {
        val waiting = listOf(
            added(),
            added(onDate = "2026-10-30", key = "other-day"),
            added(profileId = "b", key = "other-profile"),
            QueuedChange.DiaryChanged("a", "stew", slot = "dinner"),
            QueuedChange.DiaryChanged("a", "not-this-day", slot = "dinner"),
            QueuedChange.DiaryRemoved("a", "milk"),
            QueuedChange.Eaten("p", "m", true),
            QueuedChange.Eaten("p", "not-this-day", true),
            QueuedChange.MealRemoved("p", "n"),
            QueuedChange.WeightDeleted("a", "w"),
        )

        assertEquals(5, waitingOverDay("a", date, listOf(stew, milk), "p", listOf(first, second), waiting))
    }

    @Test
    fun `a day not read leaves out what is written for it alone`() {
        assertEquals(1, waitingOverDay("a", date, emptyList(), null, emptyList(), listOf(added(), QueuedChange.DiaryRemoved("a", "milk"), QueuedChange.Eaten("p", "m", true))))
    }

    @Test
    fun `a change of a diary or of a plan's meal is one a day shows, any other is not`() {
        assertTrue(added().isOfADiary())
        assertTrue(QueuedChange.Eaten("p", "m", true).isOfADiary())
        assertFalse(QueuedChange.WeightDeleted("a", "w").isOfADiary())
        assertFalse(QueuedChange.NotificationRead("n").isOfADiary())
    }
}
