// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.HouseholdListRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.HouseholdList
import net.whiteravens.dietapp.domain.HouseholdPart
import net.whiteravens.dietapp.domain.ItemShare
import net.whiteravens.dietapp.domain.MealPlanPage
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.domain.ShoppingGroup
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.testing.changeQueue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdViewModelTest {
    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private val adam = Profile("adam", "Adam", "balanced", 4, 90.0, null)
    private val ewa = Profile("ewa", "Ewa", "balanced", 4, 64.0, null)
    private val today = LocalDate.now()

    private fun plan(id: String, start: LocalDate, days: Int = 3) =
        MealPlanSummary(id, start.toString(), days, "balanced", Nutrition(2000.0, 100.0, 70.0, 220.0), 0.4, false)

    private val row = ShoppingItem(
        id = "pepper", name = "Bell pepper", totalQuantity = 425.0, toBuyQuantity = 425.0, unit = "g", displayUnit = "g", atHome = 0.0,
        purchasedQuantity = null, pantryBestBefore = null, estimatedCalories = 110.0, packs = null, checked = false,
        shares = listOf(ItemShare("adam", "Adam", 345.0), ItemShare("ewa", "Ewa", 80.0)),
    )

    private fun household(id: String = "home", planIds: List<String> = listOf("adams", "ewas")) = HouseholdList(
        id = id,
        fromDate = today.toString(),
        toDate = today.plusDays(1).toString(),
        parts = planIds.mapIndexed { i, planId -> HouseholdPart("part-$i", planId, if (i == 0) "adam" else "ewa", if (i == 0) "Adam" else "Ewa", today.toString(), today.plusDays(1).toString(), false) },
        groups = listOf(ShoppingGroup("vegetables", listOf(row), 110.0)),
        totalEstimatedCalories = 110.0,
        stale = false,
    )

    @Test
    fun `the form offers each profile's plans that have a day of its days, the one that begins last first, and asks for two profiles at least`() {
        val households = mockk<HouseholdListRepository>(relaxed = true)
        coEvery { households.refresh() } returns emptyList()
        val plans = mockk<MealPlanRepository>()
        coEvery { plans.plans("adam") } returns MealPlanPage(listOf(plan("adams", today), plan("adams-later", today.plusDays(10)), plan("adams-newer", today.minusDays(1))), 1, 1, null)
        coEvery { plans.plans("ewa") } returns MealPlanPage(listOf(plan("ewas", today)), 1, 1, null)
        val model = HouseholdViewModel(households, plans, changeQueue())

        model.startForm(listOf(adam, ewa))

        val form = model.state.value.form!!
        assertEquals(today to today.plusDays(6), form.from to form.to)
        // The plan of ten days on begins after the days; of the two that have them, the one that begins last comes first.
        assertEquals(listOf("adams", "adams-newer"), form.offered("adam")!!.map { it.id })
        assertEquals(listOf("adams", "ewas"), form.planIds)

        model.choose("ewa", null)
        assertEquals(listOf("adams"), model.state.value.form!!.planIds)
        model.make()
        coVerify(exactly = 0) { households.make(any(), any(), any()) }

        // Days that no plan of Ewa's has leave her none to choose.
        model.choose("ewa", "ewas")
        model.from(today.plusDays(20))
        assertEquals(today.plusDays(20), model.state.value.form!!.to)
        assertEquals(emptyList<String>(), model.state.value.form!!.planIds)
    }

    @Test
    fun `one made is opened and the form closed, and one made again with its plans and days replaces it`() {
        val households = mockk<HouseholdListRepository>(relaxed = true)
        coEvery { households.refresh() } returns emptyList()
        coEvery { households.make(listOf("adams", "ewas"), today.toString(), today.plusDays(6).toString()) } returns household()
        coEvery { households.make(listOf("adams", "ewas"), today.toString(), today.plusDays(1).toString()) } returns household("home-again")
        coEvery { households.cached() } returns listOf(household())
        val plans = mockk<MealPlanRepository>()
        coEvery { plans.plans(any()) } answers { MealPlanPage(listOf(plan(if (firstArg<String>() == "adam") "adams" else "ewas", today)), 1, 1, null) }
        val model = HouseholdViewModel(households, plans, changeQueue())

        model.startForm(listOf(adam, ewa))
        model.make()

        assertEquals("home", model.state.value.openedId)
        assertNull(model.state.value.form)

        model.update(model.state.value.opened!!)
        coVerify(exactly = 1) { households.make(listOf("adams", "ewas"), today.toString(), today.plusDays(1).toString()) }
        assertEquals("home-again", model.state.value.openedId)
    }

    @Test
    fun `a row ticked without a network shows as ticked and waiting, and a refusal of making one is said`() {
        val households = mockk<HouseholdListRepository>(relaxed = true)
        coEvery { households.cached() } returns listOf(household())
        coEvery { households.refresh() } throws IOException("offline")
        val queue = changeQueue()
        coEvery { households.change("home", "pepper", RowChange(checked = true)) } coAnswers {
            queue.submit(QueuedChange.HouseholdRow("home", "pepper", checked = true)) { throw IOException("offline") }
        }
        coEvery { households.make(any(), any(), any()) } throws ApiException(400, "HOUSEHOLD_PROFILE_TWICE", "A household list takes one plan of each profile.")
        val model = HouseholdViewModel(households, mockk(relaxed = true), queue)

        assertTrue(model.state.value.offline)
        model.open("home")
        model.change("pepper", RowChange(checked = true))

        assertTrue(model.state.value.shown!!.groups.single().items.single().checked)
        assertEquals(setOf("pepper"), model.state.value.waitingItems)

        model.update(model.state.value.opened!!)
        assertEquals("HOUSEHOLD_PROFILE_TWICE", (model.state.value.error as ApiException).code)
    }

    @Test
    fun `a deleted one is gone, and nothing is opened`() {
        val households = mockk<HouseholdListRepository>(relaxed = true)
        coEvery { households.refresh() } returns listOf(household())
        val model = HouseholdViewModel(households, mockk(relaxed = true), changeQueue())
        model.open("home")

        model.delete(model.state.value.opened!!)

        coVerify(exactly = 1) { households.delete(household()) }
        assertEquals(emptyList<HouseholdList>(), model.state.value.lists)
        assertNull(model.state.value.openedId)
    }
}
