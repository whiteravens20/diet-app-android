// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import net.whiteravens.dietapp.domain.DaySettings
import net.whiteravens.dietapp.domain.DaySettingsPreview
import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.RelaxationNeeded
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The settings of a day of a running plan, as the web's window changes them: what a change does is the API's answer, and nothing changes until it is saved. */
@OptIn(ExperimentalCoroutinesApi::class)
class DaySettingsViewModelTest {
    private val main = StandardTestDispatcher()

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private val asItIs = DaySettings(mealCount = 3)
    private val training = asItIs.copy(dayType = "training")

    private fun day(settings: DaySettings = asItIs) = PlanDay(
        id = "day",
        date = "2026-01-06",
        meals = emptyList(),
        nutrition = Nutrition(2000.0, 120.0, 60.0, 240.0),
        calorieTarget = 2000.0,
        calorieDelta = 0.0,
        skipped = false,
        offTarget = null,
        belowSafetyFloor = false,
        targetMacros = Macros(125.0, 67.0, 225.0),
        locks = emptyMap(),
        settings = settings,
    )

    private fun preview(then: Double, unchanged: Boolean = false) = DaySettingsPreview(
        revision = 7,
        targetNow = 2000.0,
        targetThen = then,
        macrosThen = Macros(144.0, 77.0, 259.0),
        belowSafetyFloor = false,
        meals = emptyList(),
        added = emptyList(),
        dayCalories = then,
        fits = true,
        listOutOfDate = false,
        unchanged = unchanged,
    )

    private val noChange = preview(2000.0, unchanged = true)
    private val more = preview(2300.0)
    private val asked = mutableListOf<DaySettings>()
    private val plans = mockk<MealPlanRepository>().also {
        coEvery { it.previewDaySettings("plan", "day", any()) } answers {
            val settings = thirdArg<DaySettings>()
            asked += settings
            if (settings == asItIs) noChange else more
        }
        every { it.relaxationNeeded(any()) } returns null
    }

    private fun opened(settings: DaySettings = asItIs) = DaySettingsViewModel(plans).also { it.open("plan", day(settings)) }

    @Test
    fun `the window asks at once what the day's settings as they stand do, and shows the answer as theirs`() = runTest(main) {
        val model = opened()
        runCurrent()

        assertEquals(listOf(asItIs), asked)
        assertSame(noChange, model.state.value.preview)
        assertTrue(model.state.value.current)
    }

    @Test
    fun `a change is asked about once the typing pauses, and of several only the last`() = runTest(main) {
        val model = opened()
        runCurrent()

        model.setKind("training")
        assertFalse("the answer on the screen is for the settings before the change", model.state.value.current)
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS / 2)
        model.setMealCount(4)
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS / 2)
        runCurrent()
        assertEquals(listOf(asItIs), asked)

        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS)
        runCurrent()

        assertEquals(listOf(asItIs, training.copy(mealCount = 4)), asked)
        assertSame(more, model.state.value.preview)
        assertTrue(model.state.value.current)
    }

    @Test
    fun `an empty field is none, and a number the API refuses asks nothing`() = runTest(main) {
        val model = opened(asItIs.copy(calorieTarget = 2500, cookTimeMinutes = 30))
        runCurrent()
        asked.clear()

        model.setTarget("700")
        model.setCookTime("thirty")
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()
        assertTrue(model.state.value.targetProblem)
        assertTrue(model.state.value.cookTimeProblem)
        assertNull(model.state.value.settings)
        assertEquals(emptyList<DaySettings>(), asked)

        model.setTarget("")
        model.setCookTime(" 45 ")
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()

        assertEquals(listOf(asItIs.copy(cookTimeMinutes = 45)), asked)
    }

    @Test
    fun `a skipped day keeps its kind, to come back as it was`() = runTest(main) {
        val model = opened(training)
        runCurrent()

        model.setKind(DaySettingsViewModel.SKIP)

        assertEquals(training.copy(skip = true), model.state.value.settings)
    }

    @Test
    fun `the save sends the settings the answer was for, at its revision, and hands the plan saved back`() = runTest(main) {
        val saved = mockk<MealPlan>()
        coEvery { plans.applyDaySettings("plan", "day", training, 7, null) } returns saved
        val model = opened()
        runCurrent()

        model.setKind("training")
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()
        model.save()
        runCurrent()

        coVerify(exactly = 1) { plans.applyDaySettings("plan", "day", training, 7, null) }
        assertSame(saved, model.state.value.saved)
    }

    @Test
    fun `nothing is saved while the answer is for other settings, or while nothing would change`() = runTest(main) {
        val model = opened()
        runCurrent()

        model.save()
        runCurrent()
        model.setKind("training")
        model.save()
        runCurrent()

        coVerify(exactly = 0) { plans.applyDaySettings(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the limits in the way are offered to give way, and saving again lets them, in the API's order`() = runTest(main) {
        val refusal = ApiException(422, "PLAN_NEEDS_RELAXATION", "The plan cannot be filled inside its limits.")
        val needed = RelaxationNeeded(listOf("same_day", "cook_time"), emptyList())
        every { plans.relaxationNeeded(refusal) } returns needed
        coEvery { plans.applyDaySettings("plan", "day", training, 7, null) } throws refusal
        coEvery { plans.applyDaySettings("plan", "day", training, 7, listOf("cook_time", "same_day")) } returns mockk()
        val model = opened()
        runCurrent()
        model.setKind("training")
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()

        model.save()
        runCurrent()
        assertSame(needed, model.state.value.inTheWay)
        assertNull("what stands in the way is no error", model.state.value.error)

        model.letGiveWay()
        runCurrent()

        coVerify(exactly = 1) { plans.applyDaySettings("plan", "day", training, 7, listOf("cook_time", "same_day")) }
    }

    @Test
    fun `a plan changed under the window has it read anew, and what the settings do asked again`() = runTest(main) {
        coEvery { plans.applyDaySettings("plan", "day", training, 7, null) } throws ApiException(409, "PLAN_CHANGED", "The plan changed.")
        val model = opened()
        runCurrent()
        model.setKind("training")
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()

        model.save()
        runCurrent()

        assertTrue(model.state.value.stale)
        assertNull(model.state.value.saved)
        assertEquals(listOf(asItIs, training, training), asked)
    }
}
