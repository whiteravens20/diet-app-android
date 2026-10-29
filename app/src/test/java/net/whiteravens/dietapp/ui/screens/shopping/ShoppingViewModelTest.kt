// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ShoppingListRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.ListProposal
import net.whiteravens.dietapp.domain.ListProposals
import net.whiteravens.dietapp.domain.MealPlanPage
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.domain.ShoppingGroup
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.testing.changeQueue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The shopping lists of a plan as the screen keeps them: made for some days, made again, chosen among and deleted. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun list(id: String, from: String, to: String, made: String) =
        ShoppingList(id, "plan", from, to, emptyList(), 0.0, stale = false, createdAt = "2026-10-${made}T08:00:00.000Z", syncedAt = null)

    /** The plan's lists as the API has them, the newest first: one for the first three days, one for two days later on. */
    private val early = list("early", "2026-10-13", "2026-10-15", made = "12")
    private val later = list("later", "2026-10-18", "2026-10-19", made = "11")

    private val users = mockk<UserRepository>().also {
        coEvery { it.profiles() } returns listOf(Profile("ada", "Ada", "balanced", 3, 70.0, null))
        every { it.aiModeChanges } returns emptyFlow()
    }
    private val plans = mockk<MealPlanRepository>().also {
        coEvery { it.cachedPlans(any()) } returns null
        coEvery { it.plans(any(), any()) } returns
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
        val model = ShoppingViewModel(users, plans, lists, changeQueue())

        assertEquals(early, model.state.value.list)

        model.generate("2026-10-14", "2026-10-16")

        assertEquals(listOf(made, later), model.state.value.lists)
        assertEquals(made, model.state.value.list)
    }

    @Test
    fun `the lists of a plan deleted elsewhere leave the screen, the copies shown first with them`() {
        coEvery { lists.cachedForPlan("plan") } returns listOf(early, later)
        coEvery { lists.refreshForPlan("plan") } throws ApiException(404, "PLAN_NOT_FOUND", "Meal plan not found.")

        val model = ShoppingViewModel(users, plans, lists, changeQueue())

        assertEquals(emptyList<ShoppingList>(), model.state.value.lists)
        assertEquals("PLAN_NOT_FOUND", (model.state.value.readError as ApiException).code)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.offline)
    }

    @Test
    fun `lists that cannot be read are said with a way to read them again, apart from a list that could not be made`() {
        val refused = ApiException(500, "INTERNAL_ERROR", "Something went wrong.")
        coEvery { lists.refreshForPlan("plan") } throws refused
        val model = ShoppingViewModel(users, plans, lists, changeQueue())
        assertEquals(refused, model.state.value.readError)

        coEvery { lists.refreshForPlan("plan") } returns listOf(early, later)
        model.retry()

        assertNull(model.state.value.readError)
        assertEquals(listOf(early, later), model.state.value.lists)

        coEvery { lists.generate(any(), any(), any()) } throws refused
        model.generate()

        assertEquals(refused, model.state.value.error)
        assertNull(model.state.value.readError)
    }

    @Test
    fun `a row changed while the lists were read again keeps its change, the older answer left aside`() {
        val model = ShoppingViewModel(users, plans, lists, changeQueue())
        val answer = CompletableDeferred<Unit>()
        coEvery { lists.refreshForPlan("plan") } coAnswers {
            answer.await()
            listOf(early, later)
        }
        val ticked = early.copy(totalEstimatedCalories = 1.0)
        coEvery { lists.change("early", "row", RowChange(checked = true)) } returns Submitted.Sent(ticked)

        model.pull()
        model.change("row", RowChange(checked = true))
        answer.complete(Unit)

        assertEquals(listOf(ticked, later), model.state.value.lists)
        assertFalse(model.state.value.pulled)
    }

    @Test
    fun `read again, a plan the profile no longer has gives way to its newest, whose lists are read`() {
        val model = ShoppingViewModel(users, plans, lists, changeQueue())
        val newer = MealPlanSummary("newer", "2026-10-20", 7, "balanced", Nutrition(2000.0, 100.0, 70.0, 220.0), 0.4, false)
        coEvery { plans.plans(any(), any()) } returns MealPlanPage(listOf(newer), 1, 1, null)
        coEvery { lists.cachedForPlan("newer") } returns emptyList()
        coEvery { lists.refreshForPlan("newer") } returns listOf(later.copy(id = "of-newer", planId = "newer"))

        model.shownAgain(cameToTheFront = false)

        assertEquals("newer", model.state.value.selectedPlanId)
        assertEquals(listOf("of-newer"), model.state.value.lists.map { it.id })
    }

    @Test
    fun `a list the plan changed under is made again for its own days`() {
        coEvery { lists.generate("plan", later.fromDate, later.toDate) } returns later.copy(id = "again", createdAt = "2026-10-13T09:00:00.000Z")
        val model = ShoppingViewModel(users, plans, lists, changeQueue())
        model.selectList(later.id)

        model.update(later)

        coVerify { lists.generate("plan", "2026-10-18", "2026-10-19") }
        assertEquals(listOf("again", "early"), model.state.value.lists.map { it.id })
        assertEquals("again", model.state.value.list?.id)
    }

    @Test
    fun `a deleted list goes, and the newest list left is shown`() {
        coEvery { lists.delete(any()) } just runs
        val model = ShoppingViewModel(users, plans, lists, changeQueue())
        model.selectList(later.id)

        model.delete(later.id)

        coVerify { lists.delete("later") }
        assertEquals(listOf(early), model.state.value.lists)
        assertEquals(early, model.state.value.list)
    }

    @Test
    fun `what a list could do without is kept with the list it was asked of, and weighed only on an account with a model`() {
        val found = ListProposals(listOf(ListProposal.Leftover("Feta", "g", 80, 200.0, null)), weighed = false, fallbackReason = null)
        coEvery { users.me() } returns SessionUser("u", "ala@example.test", "Ala", true, "en", aiMode = "none")
        coEvery { lists.proposals(any(), any()) } returns found
        val model = ShoppingViewModel(users, plans, lists, changeQueue())

        model.findProposals(weigh = true)

        coVerify { lists.proposals("early", weigh = false) }
        assertEquals(found, model.state.value.shownProposals)

        model.selectList(later.id)

        assertNull(model.state.value.shownProposals)

        model.selectList(early.id)

        assertEquals(found, model.state.value.shownProposals)
    }

    @Test
    fun `a row changed without a network is shown changed and waiting, the screen offline, and the list read again from its copy once the change is sent`() {
        val bread = ShoppingItem("bread", "Bread", 400.0, 400.0, "g", "slice", 0.0, null, null, 1000.0, null, checked = false)
        val withBread = early.copy(groups = listOf(ShoppingGroup("bakery", listOf(bread), 1000.0)))
        coEvery { lists.refreshForPlan("plan") } returns listOf(withBread, later)
        val queue = changeQueue()
        // The repository keeps the change, as it does without a network.
        coEvery { lists.change("early", "bread", RowChange(checked = true, have = 400.0)) } coAnswers {
            queue.submit(QueuedChange.ShoppingRow("early", "bread", checked = true, have = 400.0)) { throw IOException("no network") }
        }
        val model = ShoppingViewModel(users, plans, lists, queue)

        model.change("bread", RowChange(checked = true, have = 400.0))

        val shown = model.state.value.shown!!.groups.single().items.single()
        assertTrue(shown.checked)
        assertEquals(400.0, shown.purchasedQuantity)
        assertEquals(setOf("bread"), model.state.value.waitingItems)
        assertTrue(model.state.value.offline)
        // What was read stays as read.
        assertFalse(model.state.value.list!!.groups.single().items.single().checked)

        val answered = withBread.copy(groups = listOf(ShoppingGroup("bakery", listOf(bread.copy(checked = true, purchasedQuantity = 400.0, toBuyQuantity = 0.0)), 1000.0)))
        coEvery { lists.cached("early") } returns answered
        runBlocking { queue.drain { } }

        assertEquals(answered, model.state.value.list)
        assertEquals(emptySet<String>(), model.state.value.waitingItems)
        assertFalse(model.state.value.offline)
    }

    @Test
    fun `a row changed while another change waits is kept behind it, and the screen is not said to be offline`() {
        val queue = changeQueue()
        runBlocking { queue.submit(QueuedChange.ShoppingRow("later", "milk", checked = true)) { throw IOException("no network") } }
        coEvery { lists.change("early", "bread", RowChange(checked = true)) } coAnswers {
            queue.submit(QueuedChange.ShoppingRow("early", "bread", checked = true)) { error("sent before the change waiting") }
        }
        val model = ShoppingViewModel(users, plans, lists, queue)

        model.change("bread", RowChange(checked = true))

        assertEquals(setOf("bread"), model.state.value.waitingItems)
        assertFalse(model.state.value.offline)
    }

    @Test
    fun `everything bought is cleared by a nought over the list read, as the server clears it, and a change of another list is not shown`() {
        val bread = ShoppingItem("bread", "Bread", 400.0, 0.0, "g", "slice", 0.0, 400.0, null, 1000.0, null, checked = true)
        val read = early.copy(groups = listOf(ShoppingGroup("bakery", listOf(bread), 1000.0)))

        val shown = read.withWaiting(
            listOf(
                QueuedChange.ShoppingRow("early", "bread", checked = false, have = 0.0),
                QueuedChange.ShoppingRow("later", "bread", atHome = 100.0),
            ),
        ).groups.single().items.single()

        assertFalse(shown.checked)
        assertNull(shown.purchasedQuantity)
        assertEquals(0.0, shown.atHome, 0.0)
    }
}
