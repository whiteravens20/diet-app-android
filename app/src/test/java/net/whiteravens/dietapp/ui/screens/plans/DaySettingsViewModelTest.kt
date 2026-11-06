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
import net.whiteravens.dietapp.data.remote.SessionUserDto
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.toDomain
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.DaySettings
import net.whiteravens.dietapp.domain.DaySettingsPreview
import net.whiteravens.dietapp.domain.DaySettingsWeek
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.PlanWeek
import net.whiteravens.dietapp.domain.RelaxationNeeded
import net.whiteravens.dietapp.testing.fixture
import net.whiteravens.dietapp.testing.refusal
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

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

    /** The account cannot be read, unless a test says otherwise: the unit is that of where the phone is. */
    private val users = mockk<UserRepository>().also { coEvery { it.me() } throws IOException("no network") }

    private fun opened(settings: DaySettings = asItIs, week: PlanWeek? = null) =
        DaySettingsViewModel(plans, users).also { it.open("plan", day(settings), week) }

    /** The week of the day, as a training day changed alone leaves it: off the plan's target, which evening it out brings back. */
    private val uneven = DaySettingsWeek(
        target = 2000.0,
        averageNow = 2000.0,
        averageAlone = 2043.0,
        averageEvened = 2001.0,
        paceNow = 0.5,
        paceAlone = 0.46,
        paceEvened = 0.5,
        unevenAlone = true,
        evenedTarget = 2240.0,
        otherDays = 4,
    )

    @Test
    fun `a change that leaves the week off its target is saved with the week evened out, or alone, as the reader chooses`() = runTest(main) {
        coEvery { plans.previewDaySettings("plan", "day", training) } returns more.copy(week = uneven)
        val alone = mockk<MealPlan>()
        val evened = mockk<MealPlan>()
        coEvery { plans.applyDaySettings("plan", "day", training, 7, null, false, false) } returns alone
        coEvery { plans.applyDaySettings("plan", "day", training, 7, null, false, true) } returns evened
        val first = opened()
        runCurrent()
        assertFalse(first.state.value.twoWays)

        first.setKind("training")
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()
        assertTrue(first.state.value.twoWays)
        first.save(evenWeek = true)
        runCurrent()

        coVerify(exactly = 1) { plans.applyDaySettings("plan", "day", training, 7, null, false, true) }
        assertSame(evened, first.state.value.saved)

        val second = opened()
        runCurrent()
        second.setKind("training")
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()
        second.save()
        runCurrent()

        coVerify(exactly = 1) { plans.applyDaySettings("plan", "day", training, 7, null, false, false) }
        assertSame(alone, second.state.value.saved)
    }

    @Test
    fun `a week that stands off its target is evened out as it is while nothing of the day changes, and a week that is even is not`() = runTest(main) {
        val evened = mockk<MealPlan>()
        coEvery { plans.evenWeek("plan", "2026-01-06", 7) } returns evened
        val off = PlanWeek("2026-01-05", "2026-01-11", target = 2000.0, average = 2043.0, uneven = true)
        val even = opened(week = off.copy(average = 2004.0, uneven = false))
        runCurrent()
        assertFalse(even.state.value.evenAlone)
        even.evenWeek()
        runCurrent()
        coVerify(exactly = 0) { plans.evenWeek(any(), any(), any()) }

        val model = opened(week = off)
        runCurrent()
        assertTrue(model.state.value.evenAlone)
        model.evenWeek()
        runCurrent()

        coVerify(exactly = 1) { plans.evenWeek("plan", "2026-01-06", 7) }
        assertSame(evened, model.state.value.saved)
    }

    @Test
    fun `the pace of the week is said in the account's unit once the account is read`() = runTest(main) {
        val account = AppModule.json().decodeFromString<SessionUserDto>(fixture("users-me")).toDomain()
        coEvery { users.me() } returns account.copy(weightUnit = "lb")

        val model = opened()
        runCurrent()

        assertEquals(WeightUnit.LB, model.state.value.unit)
    }

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
    fun `an empty field is none, what is no whole number asks nothing, and the fields the API refuses are marked with its reasons`() = runTest(main) {
        val outOfBounds = asItIs.copy(calorieTarget = 100, cookTimeMinutes = 1000)
        coEvery { plans.previewDaySettings("plan", "day", outOfBounds) } answers {
            asked += thirdArg<DaySettings>()
            throw refusal("day-settings-preview-refused")
        }
        val model = opened(asItIs.copy(calorieTarget = 2500, cookTimeMinutes = 30))
        runCurrent()
        asked.clear()

        model.setTarget("100")
        model.setCookTime("thirty")
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()
        assertFalse(model.state.value.targetNotWhole)
        assertTrue(model.state.value.cookTimeNotWhole)
        assertNull(model.state.value.settings)
        assertEquals(emptyList<DaySettings>(), asked)

        model.setCookTime("1000")
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()

        assertEquals(listOf(outOfBounds), asked)
        assertEquals(
            mapOf(
                "calorieTarget" to FieldRefusal.TooSmall(800.0, inclusive = true),
                "cookTimeBudgetMinutes" to FieldRefusal.TooBig(600.0, inclusive = true),
            ),
            model.state.value.refused,
        )
        // The fields say why; the answer before is no longer the settings' own, so nothing is saved.
        assertNull(model.state.value.previewError)
        assertFalse(model.state.value.current)

        model.setTarget("")
        model.setCookTime(" 45 ")
        assertEquals(emptyMap<String, FieldRefusal>(), model.state.value.refused)
        advanceTimeBy(DaySettingsViewModel.PAUSE_MILLIS * 2)
        runCurrent()

        assertEquals(listOf(outOfBounds, asItIs.copy(cookTimeMinutes = 45)), asked)
        assertTrue(model.state.value.current)
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
    fun `the limits in the way are offered to give way, and saving again lets them, as the refusal names them`() = runTest(main) {
        val refusal = ApiException(422, "PLAN_NEEDS_RELAXATION", "The plan cannot be filled inside its limits.")
        // The API names them in the order in which they give way.
        val needed = RelaxationNeeded(listOf("cook_time", "same_day"), emptyList())
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
