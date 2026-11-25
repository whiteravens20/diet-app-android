// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.DayHistoryDto
import net.whiteravens.dietapp.data.remote.PlanChangeEffectsDto
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.domain.DayEventUndo
import net.whiteravens.dietapp.domain.DayHistory
import net.whiteravens.dietapp.domain.DayHolds
import net.whiteravens.dietapp.domain.Rebalanced
import net.whiteravens.dietapp.domain.ScaleChange
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The history of a plan's day, as the web's window shows it: read when it opens, and each change made from it read again. */
@OptIn(ExperimentalCoroutinesApi::class)
class DayHistoryViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun history(undo: DayEventUndo? = null, revision: Int = 7) = DayHistory(
        planId = "plan",
        dayId = "day",
        date = "2026-11-25",
        revision = revision,
        kept = null,
        holds = DayHolds(emptyList(), emptyList(), emptyList(), emptyList(), 0),
        events = emptyList(),
        undo = undo,
    )

    private val restore = listOf(ScaleChange("oats", before = 1.0, after = 1.0))
    private val answer = mockk<Rebalanced>()
    private val plans = mockk<MealPlanRepository>()

    private fun opened(first: DayHistory) = DayHistoryViewModel(plans).also {
        coEvery { plans.dayHistory("plan", "day") } returns first
        it.open("plan", "day")
    }

    @Test
    fun `takes the last change back by its portions, and reads the history again`() {
        coEvery { plans.restorePortions("plan", restore) } returns answer
        val model = opened(history(DayEventUndo(swap = false, restore = restore, alsoDays = emptyList())))
        coEvery { plans.dayHistory("plan", "day") } returns history(revision = 8)

        model.undo()

        coVerify { plans.restorePortions("plan", restore) }
        assertEquals(8, model.state.value.history?.revision)
        assertEquals(1, model.state.value.changes)
        assertNull(model.state.value.history?.undo)
    }

    @Test
    fun `takes a swap back by the swap's own undo, from the plan as the history read it`() {
        coEvery { plans.undoSwap("plan", 7) } returns answer
        val model = opened(history(DayEventUndo(swap = true, restore = emptyList(), alsoDays = emptyList())))

        model.undo()

        coVerify { plans.undoSwap("plan", 7) }
        coVerify(exactly = 0) { plans.restorePortions(any(), any()) }
    }

    @Test
    fun `keeps the day's portions from the plan as the history read it`() {
        coEvery { plans.keepDay("plan", "day", 7) } returns history(revision = 8)
        val model = opened(history())

        model.keep()

        coVerify { plans.keepDay("plan", "day", 7) }
        assertEquals(1, model.state.value.changes)
    }

    @Test
    fun `says so where a change asked from it was refused, and tells the screen under it of nothing`() {
        coEvery { plans.keepDay("plan", "day", 7) } throws ApiException(409, "PLAN_CHANGED", "The plan changed.")
        val model = opened(history())

        model.keep()

        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.acting)
        assertEquals(0, model.state.value.changes)
    }

    @Test
    fun `an answer that did nothing to the lists or the freezer says nothing of them`() {
        assertNull(PlanChangeEffectsDto().toDomain())
        assertEquals(listOf("2026-11-26"), PlanChangeEffectsDto(thaw = listOf("2026-11-26")).toDomain()?.thaw)
    }

    @Test
    fun `reads a day's history as the API answers it`() {
        val read = Json { ignoreUnknownKeys = true }.decodeFromString<DayHistoryDto>(
            """{"planId":"plan","dayId":"day","date":"2026-11-25","revision":3,"kept":{"choice":"undo","at":"2026-11-25T08:00:00.000Z"},
            "holds":{"ownMeals":["Zupa"],"eatenMeals":[],"leftovers":[],"rests":["Kurczak"],"diaryEntries":2},
            "events":[{"id":"e1","at":"2026-11-25T09:00:00.000Z","cause":"resize","own":false,"from":["2026-11-24"],
              "meals":[{"mealId":"m1","mealType":"lunch","before":{"name":"Kasza","servings":1,"calories":400,"eaten":false,"leftover":false},
                "after":{"name":"Kasza","servings":1.2,"calories":480,"eaten":false,"leftover":false}}],
              "target":null,"choice":{"before":"undo","after":null},
              "effects":{"lists":[{"id":"l1","planId":"plan","profileId":"p1","fromDate":"2026-11-24","toDate":"2026-11-30","householdId":null}],"thaw":[],"freeze":["2026-11-26"]}}],
            "undo":{"kind":"restore","eventId":"e1","restore":[{"mealId":"m1","scale":1}],"alsoDays":["2026-11-24"]}}""",
        ).toDomain()

        assertEquals("undo" to "2026-11-25T08:00:00.000Z", read.kept)
        assertEquals(listOf("Kurczak"), read.holds.rests)
        val event = read.events.single()
        assertFalse(event.own)
        assertEquals(listOf("2026-11-24"), event.from)
        assertEquals(400 to 480, event.meals.single().let { it.before!!.calories to it.after!!.calories })
        assertEquals("undo" to null, event.choice)
        assertEquals(listOf("p1"), event.effects.lists.map { it.profileId })
        assertEquals(listOf("2026-11-26"), event.effects.freeze)
        assertEquals(DayEventUndo(swap = false, restore = listOf(ScaleChange("m1", 1.0, 1.0)), alsoDays = listOf("2026-11-24")), read.undo)
    }
}
