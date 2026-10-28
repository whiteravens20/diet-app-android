// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.domain.Reroll
import net.whiteravens.dietapp.domain.RerollCouldGo
import net.whiteravens.dietapp.domain.RerollKept
import net.whiteravens.dietapp.domain.RerollPreview
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What making a plan again would do, as the web's window asks it: the API's answer to the choice on the screen, asked once the choice pauses. */
@OptIn(ExperimentalCoroutinesApi::class)
class RerollViewModelTest {
    private val main = StandardTestDispatcher()

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private val whole = Reroll()
    private val firstDay = Reroll(dayId = "d1")
    private val secondDay = Reroll(dayId = "d2")

    private val nothingKept = RerollKept(outside = 0, past = 0, skipped = 0, eaten = 0, custom = 0, locked = 0)
    private val nothingMore = RerollCouldGo(past = false, eaten = false, custom = false)
    private val wholeAnswer = RerollPreview(days = 3, replaced = 12, kept = nothingKept, couldGo = nothingMore, fromToday = "not_begun")
    private val dayAnswer = RerollPreview(days = 1, replaced = 4, kept = nothingKept.copy(outside = 2), couldGo = nothingMore, fromToday = "not_begun")

    private val asked = mutableListOf<Reroll>()
    private val plans = mockk<MealPlanRepository>().also {
        coEvery { it.previewReroll("plan", any()) } answers {
            val reroll = secondArg<Reroll>()
            asked += reroll
            if (reroll == whole) wholeAnswer else dayAnswer
        }
    }

    private fun opened() = RerollViewModel(plans).also { it.open() }

    @Test
    fun `the window asks at once what the choice it opens on would do, and shows the answer as the choice's`() = runTest(main) {
        val model = opened()

        model.ask("plan", 7, whole)
        assertFalse(model.state.value.current)
        runCurrent()

        assertEquals(listOf(whole), asked)
        assertSame(wholeAnswer, model.state.value.preview)
        assertTrue(model.state.value.current)
    }

    @Test
    fun `a changed choice is asked about once it pauses, and of several only the last, the earlier answer shown as older meanwhile`() = runTest(main) {
        val model = opened()
        model.ask("plan", 7, whole)
        runCurrent()

        model.ask("plan", 7, firstDay)
        assertFalse("the answer on the screen is for the choice before the change", model.state.value.current)
        assertSame(wholeAnswer, model.state.value.preview)
        advanceTimeBy(RerollViewModel.PAUSE_MILLIS / 2)
        model.ask("plan", 7, secondDay)
        advanceTimeBy(RerollViewModel.PAUSE_MILLIS / 2)
        runCurrent()
        assertEquals(listOf(whole), asked)

        advanceTimeBy(RerollViewModel.PAUSE_MILLIS)
        runCurrent()

        assertEquals(listOf(whole, secondDay), asked)
        assertSame(dayAnswer, model.state.value.preview)
        assertTrue(model.state.value.current)
    }

    @Test
    fun `a question answered or on its way is not asked again, and the plan changed since is asked about anew`() = runTest(main) {
        val model = opened()
        model.ask("plan", 7, whole)
        model.ask("plan", 7, whole)
        runCurrent()
        model.ask("plan", 7, whole)
        runCurrent()
        assertEquals(listOf(whole), asked)

        model.ask("plan", 8, whole)
        advanceTimeBy(RerollViewModel.PAUSE_MILLIS * 2)
        runCurrent()

        assertEquals(listOf(whole, whole), asked)
        assertTrue(model.state.value.current)
    }

    @Test
    fun `a question that cannot be answered says why, and is asked again at once when tried again`() = runTest(main) {
        val refusal = ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 30)
        coEvery { plans.previewReroll("plan", whole) } throws refusal andThen wholeAnswer
        val model = opened()
        model.ask("plan", 7, whole)
        runCurrent()

        assertSame(refusal, model.state.value.error)
        assertNull(model.state.value.preview)
        assertFalse(model.state.value.current)

        model.retry()
        runCurrent()

        assertNull(model.state.value.error)
        assertSame(wholeAnswer, model.state.value.preview)
        assertTrue(model.state.value.current)
    }

    @Test
    fun `the window opened anew shows nothing of the opening before, and asks at once`() = runTest(main) {
        val model = opened()
        model.ask("plan", 7, whole)
        runCurrent()

        model.open()
        assertEquals(RerollViewModel.UiState(), model.state.value)
        model.ask("plan", 7, firstDay)
        runCurrent()

        assertEquals(listOf(whole, firstDay), asked)
        assertSame(dayAnswer, model.state.value.preview)
    }
}
