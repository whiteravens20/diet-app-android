// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ShoppingListRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.MealPlanPage
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ShoppingList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The shopping lists of a plan as the screen keeps them: made for some days, made again, chosen among and deleted. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    private fun list(id: String, from: String, to: String, made: String) =
        ShoppingList(id, "plan", from, to, emptyList(), 0.0, stale = false, createdAt = "2026-10-${made}T08:00:00.000Z", syncedAt = null)

    /** The plan's lists as the API has them, the newest first: one for the first three days, one for two days later on. */
    private val early = list("early", "2026-10-13", "2026-10-15", made = "12")
    private val later = list("later", "2026-10-18", "2026-10-19", made = "11")

    private val users = mockk<UserRepository>().also { coEvery { it.profiles() } returns listOf(Profile("ada", "Ada", "balanced", 3)) }
    private val plans = mockk<MealPlanRepository>().also {
        coEvery { it.cachedPlans(any()) } returns null
        coEvery { it.plans(any(), any(), any()) } returns
            MealPlanPage(listOf(MealPlanSummary("plan", "2026-10-13", 7, "balanced", Nutrition(2000.0, 100.0, 70.0, 220.0), 0.4, false)), 1, 1, null)
    }
    private val lists = mockk<ShoppingListRepository>().also {
        coEvery { it.cachedForPlan("plan") } returns emptyList()
        coEvery { it.refreshForPlan("plan") } returns listOf(early, later)
    }

    @Test
    fun `a list made for some days stands in the place of the lists that share a day with it, and is the one shown`() {
        val made = list("made", "2026-10-14", "2026-10-16", made = "13")
        coEvery { lists.generate("plan", "2026-10-14", "2026-10-16") } returns made
        val model = ShoppingViewModel(users, plans, lists)

        assertEquals(early, model.state.value.list)

        model.generate("2026-10-14", "2026-10-16")

        assertEquals(listOf(made, later), model.state.value.lists)
        assertEquals(made, model.state.value.list)
    }

    @Test
    fun `a list the plan changed under is made again for its own days`() {
        coEvery { lists.generate("plan", later.fromDate, later.toDate) } returns later.copy(id = "again", createdAt = "2026-10-13T09:00:00.000Z")
        val model = ShoppingViewModel(users, plans, lists)
        model.selectList(later.id)

        model.update(later)

        coVerify { lists.generate("plan", "2026-10-18", "2026-10-19") }
        assertEquals(listOf("again", "early"), model.state.value.lists.map { it.id })
        assertEquals("again", model.state.value.list?.id)
    }

    @Test
    fun `a deleted list goes, and the newest list left is shown`() {
        coEvery { lists.delete(any()) } just runs
        val model = ShoppingViewModel(users, plans, lists)
        model.selectList(later.id)

        model.delete(later.id)

        coVerify { lists.delete("later") }
        assertEquals(listOf(early), model.state.value.lists)
        assertEquals(early, model.state.value.list)
    }
}
