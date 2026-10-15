// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.DayWay
import net.whiteravens.dietapp.domain.DayWays
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.domain.WayMeal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Other ways to fill a day, as the web's window offers them: nothing changes until one is taken. */
@OptIn(ExperimentalCoroutinesApi::class)
class DayWaysViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    private val day = Nutrition(2000.0, 120.0, 70.0, 210.0)
    private fun way(offer: String, reason: String? = null) = DayWay(listOf(WayMeal("lunch", "Soup", 500.0, stays = false)), day, wasted = 0, reason = reason, offer = offer)
    private val engine = DayWays(2000.0, day, nowWasted = 1, ways = listOf(way("a"), way("b")), weighed = false, fallbackReason = null)
    private val model = DayWays(2000.0, day, nowWasted = 1, ways = listOf(way("b", "Lighter."), way("a", "Heavier.")), weighed = true, fallbackReason = null)

    private val plans = mockk<MealPlanRepository>().also {
        coEvery { it.dayWays("plan", "day", false) } returns engine
        coEvery { it.dayWays("plan", "day", true) } returns model
    }
    private val users = mockk<UserRepository>().also { coEvery { it.me() } returns account("admin") }

    private fun account(aiMode: String) = SessionUser("u", "ala@example.test", "Ala", true, "en", "system", aiMode = aiMode)

    private fun opened() = DayWaysViewModel(plans, users).also { it.open("plan", "day") }

    @Test
    fun `the ways come in the engine's order, and a model's order with its sentences takes their place where it is asked for`() {
        val viewModel = opened()
        assertSame(engine, viewModel.state.value.ways)

        viewModel.weigh()

        assertSame(model, viewModel.state.value.ways)
        assertEquals(listOf("b", "a"), viewModel.state.value.ways?.ways?.map { it.offer })
    }

    @Test
    fun `an account without a model asks none`() {
        coEvery { users.me() } returns account("none")
        val viewModel = opened()

        viewModel.weigh()

        assertEquals(false, viewModel.state.value.aiOn)
        coVerify(exactly = 0) { plans.dayWays(any(), any(), true) }
    }

    @Test
    fun `the way taken is the day, and a refused one has the plan read anew`() {
        val taken = mockk<MealPlan>()
        coEvery { plans.takeDayWay("plan", "day", "a") } returns taken
        coEvery { plans.takeDayWay("plan", "day", "b") } throws ApiException(409, "PLAN_CHANGED", "The plan changed.")
        val viewModel = opened()

        viewModel.take("b")

        assertTrue(viewModel.state.value.stale)
        assertNull(viewModel.state.value.taken)

        viewModel.take("a")

        assertSame(taken, viewModel.state.value.taken)
    }
}
