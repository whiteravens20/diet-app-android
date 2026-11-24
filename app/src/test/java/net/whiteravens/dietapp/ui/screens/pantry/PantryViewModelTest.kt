// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.pantry

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.PantryRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.PantryChange
import net.whiteravens.dietapp.domain.PantryPortion
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.StockableIngredient
import net.whiteravens.dietapp.testing.HeldChanges
import net.whiteravens.dietapp.testing.changeQueue
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** A profile's pantry as the screen keeps it: read, something found and put in, and a row changed or taken out where it is shown. */
@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {
    private val main = StandardTestDispatcher()

    /** The application's scope, which outlives a screen: where a removal still held when the model is cleared is sent. */
    private val application = CoroutineScope(main)

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private val garlic = PantryRow("garlic-row", "garlic", "Garlic", "spices", 3.0, "clove", LocalDate.parse("2026-11-06"))
    private val egg = PantryRow("egg-row", "egg", "Large egg", "dairy", 6.0, "piece", LocalDate.parse("2026-10-22"))
    private val oats = PantryRow("oats-row", "oats", "Rolled oats", "grains", 212.0, "g", null)
    private val milk = PantryRow("milk-row", "milk", "Whole milk", "dairy", 1000.0, "ml", null)

    private val eggs = StockableIngredient("egg", "Large egg", "dairy", listOf("piece"), "piece", "piece")
    private val eggplant = StockableIngredient("eggplant", "Eggplant", "vegetables", listOf("g"), "g", "piece")

    /** The changes made on the phone that wait to be sent: none, unless a test makes one without a network. */
    private val queue = changeQueue()

    private val offline: suspend () -> Nothing = { throw IOException("no network") }

    private val users = mockk<UserRepository>().also {
        coEvery { it.profiles() } returns listOf(Profile("ada", "Ada", "balanced", 3, 70.0, null), Profile("bo", "Bo", "vegan", 4, 70.0, null))
    }

    private fun pantry(vararg rows: PantryRow) = mockk<PantryRepository>().also {
        coEvery { it.rows(any()) } returns rows.toList()
    }

    private fun refused(code: String) = ApiException(400, code, "")

    @Test
    fun `the first profile's pantry is read, and its rows put under their categories where the first of each comes`() = runTest(main) {
        val pantry = pantry(garlic, egg, oats, milk)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        assertEquals("ada", model.state.value.profileId)
        assertEquals(
            listOf("spices" to listOf(garlic), "dairy" to listOf(egg, milk), "grains" to listOf(oats)),
            model.state.value.groups,
        )

        model.pickProfile("bo")
        runCurrent()
        coVerify { pantry.rows("bo") }
    }

    @Test
    fun `the catalogue is searched once two letters are typed and typing pauses, and what the pantry holds already is not offered`() = runTest(main) {
        val pantry = pantry(egg)
        coEvery { pantry.ingredients("eg") } returns listOf(eggs, eggplant)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.search("e")
        advanceTimeBy(1_000)
        coVerify(exactly = 0) { pantry.ingredients(any()) }

        model.search("eg")
        advanceTimeBy(ProfilesViewModel.SEARCH_DELAY_MS + 1)
        runCurrent()

        assertTrue(model.state.value.form.searched)
        assertEquals(listOf(eggplant), model.state.value.offered)
    }

    @Test
    fun `a food's frozen row stands after its fresh one, its category by name, and the food is still offered fresh`() = runTest(main) {
        val frozen = PantryRow("chicken-frozen", "chicken", "Chicken breast", "meat", 286.0, "g", LocalDate.parse("2026-12-14"), frozen = true)
        val fresh = PantryRow("chicken-fresh", "chicken", "Chicken breast", "meat", 100.0, "g", LocalDate.parse("2026-11-16"))
        val bacon = PantryRow("bacon-row", "bacon", "Bacon", "meat", 150.0, "g", null)
        val chicken = StockableIngredient("chicken", "Chicken breast", "meat", listOf("g"), "g", "piece")
        val pantry = pantry(frozen, bacon)
        coEvery { pantry.ingredients("ch") } returns listOf(chicken)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.search("ch")
        advanceTimeBy(ProfilesViewModel.SEARCH_DELAY_MS + 1)
        runCurrent()

        assertEquals(listOf("meat" to listOf(bacon, frozen)), model.state.value.groups)
        assertEquals(listOf(chicken), model.state.value.offered)
        val both = PantryViewModel(pantry(frozen, fresh, bacon), users, queue, application, SavedStateHandle())
        runCurrent()
        assertEquals(listOf("meat" to listOf(bacon, fresh, frozen)), both.state.value.groups)
    }

    @Test
    fun `the freezer's box dates what goes in 30 days on where no day is chosen, keeps a day chosen, and takes away the day it wrote`() = runTest(main) {
        val model = PantryViewModel(pantry(), users, queue, application, SavedStateHandle())
        runCurrent()
        val today = LocalDate.parse("2026-11-18")
        val form = { model.state.value.form }

        model.freezeIt(true, today)
        assertEquals(true to LocalDate.parse("2026-12-18"), form().frozen to form().bestBefore)
        model.freezeIt(false, today)
        assertEquals(false to null, form().frozen to form().bestBefore)

        model.chooseBestBefore(LocalDate.parse("2027-03-01"))
        model.freezeIt(true, today)
        assertEquals(LocalDate.parse("2027-03-01"), form().bestBefore)
        model.freezeIt(false, today)
        assertEquals(LocalDate.parse("2027-03-01"), form().bestBefore)

        // A day the box wrote and then changed stays when the box is taken away.
        model.chooseBestBefore(null)
        model.freezeIt(true, today)
        model.chooseBestBefore(LocalDate.parse("2026-12-24"))
        model.freezeIt(false, today)
        assertEquals(LocalDate.parse("2026-12-24"), form().bestBefore)
    }

    @Test
    fun `any food is offered to go into the freezer, one held fresh or frozen too, and goes in in the portions asked`() = runTest(main) {
        val chicken = StockableIngredient("chicken", "Chicken breast", "meat", listOf("g"), "g", "piece")
        val frozenChicken = PantryRow("chicken-frozen", "chicken", "Chicken breast", "meat", 286.0, "g", LocalDate.parse("2026-12-14"), frozen = true)
        val frozenEgg = egg.copy(id = "egg-frozen", shown = 2.0, bestBefore = LocalDate.parse("2026-12-18"), frozen = true)
        val pantry = pantry(egg, frozenChicken)
        coEvery { pantry.ingredients("eg") } returns listOf(eggs, eggplant)
        coEvery { pantry.ingredients("ch") } returns listOf(chicken)
        coEvery { pantry.add("ada", eggs, 6.0, "piece", LocalDate.parse("2026-12-18"), true, 4) } returns Submitted.Sent(frozenEgg)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.freezeIt(true, LocalDate.parse("2026-11-18"))
        model.search("ch")
        advanceTimeBy(ProfilesViewModel.SEARCH_DELAY_MS + 1)
        runCurrent()
        assertEquals(listOf(chicken), model.state.value.offered)
        model.search("eg")
        advanceTimeBy(ProfilesViewModel.SEARCH_DELAY_MS + 1)
        runCurrent()
        assertEquals(listOf(eggs, eggplant), model.state.value.offered)

        model.pick(eggs)
        model.typeQuantity("6")
        assertEquals(listOf(6.0), model.state.value.form.parts)
        model.typePortions("10")
        assertNull(model.state.value.form.parts)
        assertFalse(model.state.value.canAdd)
        model.typePortions("x")
        assertEquals("10", model.state.value.form.portions)
        model.typePortions("4")
        assertEquals(listOf(2.0, 2.0, 1.0, 1.0), model.state.value.form.parts)
        model.add()
        runCurrent()

        coVerify { pantry.add("ada", eggs, 6.0, "piece", LocalDate.parse("2026-12-18"), true, 4) }
        assertEquals(PantryViewModel.AddForm(), model.state.value.form)
    }

    @Test
    fun `what is put in fresh goes in as one row, whatever portions were typed while the box was ticked`() = runTest(main) {
        val pantry = pantry()
        coEvery { pantry.ingredients("eg") } returns listOf(eggs)
        coEvery { pantry.add("ada", eggs, 6.0, "piece", null, false, 1) } returns Submitted.Sent(egg)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.freezeIt(true, LocalDate.parse("2026-11-18"))
        model.typePortions("4")
        model.freezeIt(false, LocalDate.parse("2026-11-18"))
        model.search("eg")
        advanceTimeBy(ProfilesViewModel.SEARCH_DELAY_MS + 1)
        runCurrent()
        model.pick(eggs)
        model.typeQuantity("6")
        model.add()
        runCurrent()

        coVerify { pantry.add("ada", eggs, 6.0, "piece", null, false, 1) }
    }

    @Test
    fun `a fresh row goes into the freezer from its window, in the portions typed, and the pantry is shown as the API answers it`() = runTest(main) {
        val today = LocalDate.parse("2026-11-18")
        val first = milk.copy(id = "milk-1", shown = 500.0, bestBefore = LocalDate.parse("2026-12-18"), frozen = true)
        val second = first.copy(id = "milk-2")
        val pantry = pantry(milk, egg)
        coEvery { pantry.freeze("ada", "milk-row", 2, today) } returns Submitted.Sent(listOf(egg, first, second))
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.openFreeze(milk)
        assertEquals(milk to "1", model.state.value.freezing to model.state.value.freezePortions)
        assertEquals(listOf(1000.0), model.state.value.freezeParts)
        model.typeFreezePortions("2")
        assertEquals(listOf(500.0, 500.0), model.state.value.freezeParts)
        model.freeze(today)
        runCurrent()

        assertEquals(listOf(egg, first, second), model.state.value.rows)
        assertNull(model.state.value.freezing)
        assertNull(model.state.value.changing)
        // A package is not put into the freezer again.
        model.openFreeze(first)
        assertNull(model.state.value.freezing)
    }

    @Test
    fun `the window asks the size the plans eat the food in, and starts its count at the fewest packages none bigger than it`() = runTest(main) {
        val chicken = PantryRow("chicken-row", "chicken", "Chicken breast", "meat", 500.0, "g", null)
        val pantry = pantry(chicken)
        coEvery { pantry.portion("chicken-row") } returns PantryPortion(300, "g")
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.openFreeze(chicken)
        assertEquals("1", model.state.value.freezePortions)
        runCurrent()

        assertEquals("2", model.state.value.freezePortions)
        assertEquals(300, model.state.value.freezeHint)
        assertEquals(listOf(250.0, 250.0), model.state.value.freezeParts)
    }

    @Test
    fun `a number typed before the plans answer is kept, and nothing is said of a row no bigger than the size, nor without an answer`() = runTest(main) {
        val chicken = PantryRow("chicken-row", "chicken", "Chicken breast", "meat", 500.0, "g", null)
        val pantry = pantry(chicken)
        coEvery { pantry.portion("chicken-row") } returns PantryPortion(300, "g")
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.openFreeze(chicken)
        model.typeFreezePortions("4")
        runCurrent()
        assertEquals("4", model.state.value.freezePortions)

        model.closeFreeze()
        coEvery { pantry.portion("chicken-row") } returns PantryPortion(500, "g")
        model.openFreeze(chicken)
        runCurrent()
        assertEquals("1" to null, model.state.value.freezePortions to model.state.value.freezeHint)

        model.closeFreeze()
        coEvery { pantry.portion("chicken-row") } throws IOException("no network")
        model.openFreeze(chicken)
        runCurrent()
        assertEquals("1" to null, model.state.value.freezePortions to model.state.value.freezeHint)
    }

    @Test
    fun `a window asking for portions of less than one of the unit sends nothing, and a refusal is said in it while it is open`() = runTest(main) {
        val pantry = pantry(egg)
        coEvery { pantry.freeze("ada", "egg-row", 3, any()) } throws refused("PORTIONS_TOO_MANY")
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.openFreeze(egg)
        model.typeFreezePortions("7")
        assertNull(model.state.value.freezeParts)
        model.freeze(LocalDate.parse("2026-11-18"))
        runCurrent()
        coVerify(exactly = 0) { pantry.freeze(any(), any(), any(), any()) }

        model.typeFreezePortions("3")
        model.freeze(LocalDate.parse("2026-11-18"))
        runCurrent()
        assertEquals("PORTIONS_TOO_MANY", (model.state.value.freezeFailure as ApiException).code)
        assertEquals(egg, model.state.value.freezing)
        assertNull(model.state.value.failed)

        model.closeFreeze()
        assertEquals(null to null, model.state.value.freezing to model.state.value.freezeFailure)
    }

    @Test
    fun `a freezing that fails after its window is closed is said over the pantry`() = runTest(main) {
        val pantry = pantry(milk)
        coEvery { pantry.freeze("ada", "milk-row", 1, any()) } coAnswers {
            kotlinx.coroutines.delay(1_000)
            throw refused("NOT_FOUND")
        }
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.openFreeze(milk)
        model.freeze(LocalDate.parse("2026-11-18"))
        runCurrent()
        model.closeFreeze()
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(PantryViewModel.Failure.FREEZE, model.state.value.failed?.first)
        assertNull(model.state.value.freezeFailure)
    }

    @Test
    fun `without a network a row put into the freezer is shown frozen until the day it will have, marked waiting`() = runTest(main) {
        val pantry = pantry(milk)
        coEvery { pantry.freeze("ada", "milk-row", 2, LocalDate.parse("2026-11-18")) } coAnswers {
            queue.submit(QueuedChange.PantryFrozen("ada", "milk-row", "key", "2026-12-18", 2), offline)
        }
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.openFreeze(milk)
        model.typeFreezePortions("2")
        model.freeze(LocalDate.parse("2026-11-18"))
        runCurrent()

        assertEquals(listOf(milk.copy(bestBefore = LocalDate.parse("2026-12-18"), frozen = true)), model.state.value.shownRows)
        assertEquals(setOf("milk-row"), model.state.value.waitingRows)
        assertTrue(model.state.value.offline)
        assertNull(model.state.value.freezing)
    }

    @Test
    fun `a food's packages stand after its fresh row, the one going off first first, each told by its number`() = runTest(main) {
        val fresh = PantryRow("chicken-fresh", "chicken", "Chicken breast", "meat", 100.0, "g", LocalDate.parse("2026-11-16"))
        val later = PantryRow("chicken-later", "chicken", "Chicken breast", "meat", 300.0, "g", LocalDate.parse("2027-01-10"), frozen = true)
        val sooner = later.copy(id = "chicken-sooner", shown = 286.0, bestBefore = LocalDate.parse("2026-12-14"))
        val fish = PantryRow("fish-frozen", "fish", "Cod", "meat", 200.0, "g", LocalDate.parse("2026-12-20"), frozen = true)
        val model = PantryViewModel(pantry(later, fish, fresh, sooner), users, queue, application, SavedStateHandle())
        runCurrent()

        assertEquals(listOf("meat" to listOf(fresh, sooner, later, fish)), model.state.value.groups)
        // One package of a food is told by its food alone.
        assertEquals(mapOf("chicken-sooner" to 1, "chicken-later" to 2), model.state.value.packageNumbers)
    }

    @Test
    fun `an ingredient picked is put in in the unit it is counted in at first, the search emptied`() = runTest(main) {
        val model = PantryViewModel(pantry(), users, queue, application, SavedStateHandle())
        runCurrent()
        model.chooseUnit("ml")

        model.pick(eggs)

        assertEquals(eggs, model.state.value.form.picked)
        assertEquals("piece", model.state.value.form.unit)
        assertEquals("", model.state.value.form.search)
    }

    @Test
    fun `an amount is typed with a comma or a point, and nothing else is taken, nor a number under nothing`() = runTest(main) {
        val model = PantryViewModel(pantry(), users, queue, application, SavedStateHandle())
        runCurrent()

        model.typeQuantity("1,5")
        model.typeQuantity("1,5x")
        assertEquals("1,5", model.state.value.form.quantity)
        assertEquals(1.5, model.state.value.form.amount)

        model.typeQuantity("-2")
        assertEquals("1,5", model.state.value.form.quantity)

        model.typeQuantity("0")
        assertNull("Nothing is not put in", model.state.value.form.amount)
        assertFalse(model.state.value.canAdd)
    }

    @Test
    fun `what the form holds is put in for the profile shown, the row it is kept in shown, the form emptied and the pantry read again`() = runTest(main) {
        val pantry = pantry()
        coEvery { pantry.add("ada", eggs, 6.5, "piece", LocalDate.parse("2026-10-22")) } returns Submitted.Sent(egg)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()
        model.pick(eggs)
        model.typeQuantity("6,5")
        model.chooseBestBefore(LocalDate.parse("2026-10-22"))
        assertTrue(model.state.value.canAdd)

        model.add()
        assertTrue(model.state.value.adding)
        coEvery { pantry.rows("ada") } returns listOf(egg)
        runCurrent()

        assertEquals(listOf(egg), model.state.value.rows)
        assertEquals(PantryViewModel.AddForm(), model.state.value.form)
        assertNull(model.state.value.failed)
        coVerify(exactly = 2) { pantry.rows("ada") }
    }

    @Test
    fun `the window Android ends the app with comes back for its profile, with what was picked and typed in it`() = runTest(main) {
        val pantry = pantry()
        val state = SavedStateHandle()
        val before = PantryViewModel(pantry, users, queue, application, state)
        runCurrent()
        before.pickProfile("bo")
        before.openAdd()
        before.pick(eggs)
        before.typeQuantity("6")
        before.freezeIt(true, LocalDate.parse("2026-11-23"))
        runCurrent()

        // The process is ended: the view model goes, the saved state stays.
        val after = PantryViewModel(pantry, users, queue, application, state)
        runCurrent()

        assertEquals("bo", after.state.value.profileId)
        assertTrue(after.state.value.addWindow)
        assertEquals(eggs, after.state.value.form.picked)
        assertEquals("6", after.state.value.form.quantity)
        assertTrue(after.state.value.form.frozen)
        assertEquals(before.state.value.form.bestBefore, after.state.value.form.bestBefore)

        // Put away, it does not come back.
        after.closeAdd()
        runCurrent()
        assertFalse(PantryViewModel(pantry, users, queue, application, state).state.value.addWindow)
    }

    @Test
    fun `something refused stays in the form to be put right, and the refusal is said with what could not be done`() = runTest(main) {
        val pantry = pantry()
        coEvery { pantry.add(any(), any(), any(), any(), any()) } throws refused("QUANTITY_TOO_LARGE")
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()
        model.pick(eggs)
        model.typeQuantity("2000")

        model.add()
        runCurrent()

        assertEquals(PantryViewModel.Failure.ADD, model.state.value.failed?.first)
        assertEquals("QUANTITY_TOO_LARGE", (model.state.value.failed?.second as ApiException).code)
        assertEquals(eggs, model.state.value.form.picked)
        assertEquals("2000", model.state.value.form.quantity)
        assertFalse(model.state.value.adding)
    }

    @Test
    fun `the window things are put in with stays open for the next, saying what went in, until it is closed`() = runTest(main) {
        val pantry = pantry()
        coEvery { pantry.add("ada", eggs, 6.5, "piece", null) } returns Submitted.Sent(egg)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.openAdd()
        assertTrue(model.state.value.addWindow)
        assertNull(model.state.value.lastAdded)
        model.pick(eggs)
        model.typeQuantity("6,5")
        model.add()
        runCurrent()

        assertTrue("The window stays open", model.state.value.addWindow)
        assertEquals(PantryViewModel.Added("Large egg", 6.5, "piece", frozen = false, portions = 1), model.state.value.lastAdded)
        assertEquals(PantryViewModel.AddForm(), model.state.value.form)
        model.closeAdd()
        assertFalse(model.state.value.addWindow)
        assertNull("What it said goes with it", model.state.value.lastAdded)
        model.openAdd()
        assertNull(model.state.value.lastAdded)
    }

    @Test
    fun `what goes into the freezer is said with the portions it goes in, the next thing put in saying nothing of it until it is in`() = runTest(main) {
        val pantry = pantry()
        val sent = CompletableDeferred<Submitted<PantryRow>>()
        coEvery { pantry.add("ada", eggplant, 500.0, "g", any(), true, 3) } returns Submitted.Sent(oats.copy(frozen = true))
        coEvery { pantry.add("ada", eggs, 2.0, "piece", any(), false, 1) } coAnswers { sent.await() }
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()
        model.openAdd()
        model.pick(eggplant)
        model.typeQuantity("500")
        model.freezeIt(true, LocalDate.parse("2026-11-19"))
        model.typePortions("3")

        model.add()
        runCurrent()
        assertEquals(PantryViewModel.Added("Eggplant", 500.0, "g", frozen = true, portions = 3), model.state.value.lastAdded)

        model.pick(eggs)
        model.typeQuantity("2")
        model.add()
        runCurrent()
        assertNull("Nothing is said while the next is on its way", model.state.value.lastAdded)
        sent.complete(Submitted.Sent(egg))
        runCurrent()
        assertEquals(PantryViewModel.Added("Large egg", 2.0, "piece", frozen = false, portions = 1), model.state.value.lastAdded)
    }

    @Test
    fun `a refusal is said in the window, not over the pantry, and goes when the window is closed`() = runTest(main) {
        val pantry = pantry()
        coEvery { pantry.add(any(), any(), any(), any(), any()) } throws refused("QUANTITY_TOO_LARGE")
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()
        model.openAdd()
        model.pick(eggs)
        model.typeQuantity("2000")

        model.add()
        runCurrent()
        assertEquals(PantryViewModel.Failure.ADD, model.state.value.failed?.first)
        assertNull(model.state.value.lastAdded)

        model.closeAdd()
        assertNull(model.state.value.failed)
        assertEquals("What was typed stays for the next time", "2000", model.state.value.form.quantity)
    }

    @Test
    fun `an amount left as shown is not sent, so that the exact one is not rounded, and another amount or day is`() {
        // The oats are kept to the tenth of a gram and shown to the gram: the 212 shown must not overwrite them.
        assertNull(PantryViewModel.changeOf(oats, "212", null))
        assertEquals(PantryChange(quantity = 200.0), PantryViewModel.changeOf(oats, "200", null))
        assertEquals(PantryChange(quantity = 4.5), PantryViewModel.changeOf(egg, "4,5", egg.bestBefore))
        assertEquals(PantryChange(newDay = true, bestBefore = null), PantryViewModel.changeOf(egg, "6", null))
        assertEquals(PantryChange(newDay = true, bestBefore = LocalDate.parse("2026-10-30")), PantryViewModel.changeOf(oats, "212", LocalDate.parse("2026-10-30")))
        assertEquals(PantryChange(quantity = 0.0), PantryViewModel.changeOf(egg, "0", egg.bestBefore))
        assertNull("An empty field holds no amount", PantryViewModel.changeOf(egg, "", null))
    }

    @Test
    fun `a row changed is shown as the API keeps it, and a change refused is said and leaves the row as it was`() = runTest(main) {
        val pantry = pantry(garlic, egg)
        val halved = egg.copy(shown = 3.0, bestBefore = null)
        coEvery { pantry.change("ada", "egg-row", PantryChange(quantity = 3.0, newDay = true, bestBefore = null)) } returns Submitted.Sent(halved)
        coEvery { pantry.change("ada", "garlic-row", PantryChange(quantity = 2000.0)) } throws refused("QUANTITY_TOO_LARGE")
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.change(egg, PantryChange(quantity = 3.0, newDay = true, bestBefore = null))
        assertEquals("egg-row", model.state.value.changing)
        coEvery { pantry.rows("ada") } returns listOf(garlic, halved)
        runCurrent()
        assertEquals(listOf(garlic, halved), model.state.value.rows)

        model.change(garlic, PantryChange(quantity = 2000.0))
        runCurrent()
        assertEquals(PantryViewModel.Failure.CHANGE, model.state.value.failed?.first)
        assertEquals(listOf(garlic, halved), model.state.value.rows)
        assertNull(model.state.value.changing)
    }

    @Test
    fun `a row taken out leaves the list, and one the API would not take out stays and is said`() = runTest(main) {
        val pantry = pantry(garlic, egg)
        coEvery { pantry.remove("ada", "egg-row") } returns Submitted.Sent(Unit)
        coEvery { pantry.remove("ada", "garlic-row") } throws ApiException(404, "INVENTORY_ITEM_NOT_FOUND", "")
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.remove(egg)
        coEvery { pantry.rows("ada") } returns listOf(garlic)
        runCurrent()
        assertEquals(listOf(garlic), model.state.value.rows)

        model.remove(garlic)
        runCurrent()
        assertEquals(PantryViewModel.Failure.REMOVE, model.state.value.failed?.first)
        assertEquals(listOf(garlic), model.state.value.rows)
    }

    @Test
    fun `nothing is put in while something is on its way`() = runTest(main) {
        val pantry = pantry(egg)
        coEvery { pantry.change(any(), any(), any()) } returns Submitted.Sent(egg)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()
        model.pick(eggplant)
        model.typeQuantity("300")

        model.change(egg, PantryChange(quantity = 5.0))
        assertFalse(model.state.value.canAdd)
        model.add()
        runCurrent()

        coVerify(exactly = 0) { pantry.add(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `without a network something put in, a row changed and a row taken out are shown over the rows read, marked waiting, the screen offline`() = runTest(main) {
        val pantry = pantry(garlic, egg)
        val added = QueuedChange.PantryAdded("ada", "eggplant", "Eggplant", "vegetables", 300.0, "g", null, "key")
        coEvery { pantry.add("ada", eggplant, 300.0, "g", null) } coAnswers { queue.submit(added, offline) }
        coEvery { pantry.change("ada", "egg-row", PantryChange(quantity = 4.0)) } coAnswers {
            queue.submit(QueuedChange.PantryChanged("ada", "egg-row", quantity = 4.0), offline)
        }
        coEvery { pantry.remove("ada", "garlic-row") } coAnswers { queue.submit(QueuedChange.PantryRemoved("ada", "garlic-row"), offline) }
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.pick(eggplant)
        model.typeQuantity("300")
        model.add()
        runCurrent()
        model.change(egg, PantryChange(quantity = 4.0))
        runCurrent()
        model.remove(garlic)
        runCurrent()

        val put = PantryRow("key", "eggplant", "Eggplant", "vegetables", 300.0, "g", null)
        assertEquals(listOf(egg.copy(shown = 4.0), put), model.state.value.shownRows)
        assertEquals(setOf("key", "egg-row"), model.state.value.waitingRows)
        assertEquals(PantryViewModel.AddForm(), model.state.value.form)
        assertTrue(model.state.value.offline)
        // What was read stays as read, and is not read again without a network.
        assertEquals(listOf(garlic, egg), model.state.value.rows)
        coVerify(exactly = 1) { pantry.rows("ada") }
    }

    @Test
    fun `something put in that waits is taken out of the queue, not changed, and once a change is sent the pantry is read again`() = runTest(main) {
        val pantry = pantry(egg)
        coEvery { pantry.add("ada", eggplant, 300.0, "g", null) } coAnswers {
            queue.submit(QueuedChange.PantryAdded("ada", "eggplant", "Eggplant", "vegetables", 300.0, "g", null, "key"), offline)
        }
        coEvery { pantry.change("ada", "egg-row", PantryChange(quantity = 4.0)) } coAnswers {
            queue.submit(QueuedChange.PantryChanged("ada", "egg-row", quantity = 4.0), offline)
        }
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()
        model.pick(eggplant)
        model.typeQuantity("300")
        model.add()
        runCurrent()
        val put = model.state.value.shownRows.single { it.id == "key" }

        model.change(put, PantryChange(quantity = 200.0))
        runCurrent()
        coVerify(exactly = 0) { pantry.change(any(), "key", any()) }

        model.remove(put)
        runCurrent()
        assertEquals(listOf(egg), model.state.value.shownRows)
        assertTrue(queue.waiting.first().isEmpty())
        coVerify(exactly = 1) { pantry.rows("ada") }

        model.change(egg, PantryChange(quantity = 4.0))
        runCurrent()
        coEvery { pantry.rows("ada") } returns listOf(egg.copy(shown = 4.0))
        queue.drain { }
        runCurrent()

        assertEquals(listOf(egg.copy(shown = 4.0)), model.state.value.shownRows)
        assertEquals(emptySet<String>(), model.state.value.waitingRows)
        assertFalse(model.state.value.offline)
        coVerify(exactly = 2) { pantry.rows("ada") }
    }

    @Test
    fun `a row whose amount waits to be sent shows the new amount without the weight the server gave the old one`() {
        val garlic = PantryRow("g", "garlic", "Garlic", "spices", 3.0, "clove", null, shownGrams = 15.0)
        val egg = PantryRow("e", "egg", "Egg", "dairy", 6.0, "piece", null, shownGrams = 330.0)
        val waiting = listOf(
            QueuedChange.PantryChanged("p", "g", quantity = 2.0),
            QueuedChange.PantryChanged("p", "e", newDay = true, bestBefore = "2026-11-20"),
        )

        val shown = listOf(garlic, egg).withWaiting("p", waiting)

        assertEquals(garlic.copy(shown = 2.0, shownGrams = null), shown[0])
        // A new day alone leaves the amount, and what it weighs, as the server said.
        assertEquals(egg.copy(bestBefore = LocalDate.parse("2026-11-20")), shown[1])
    }

    // ── A row taken out, held to be taken back ──────────────────────────

    @Test
    fun `a row taken out is gone at once and sent once the moments to take it back are over`() = runTest(main) {
        val pantry = pantry(garlic, egg)
        coEvery { pantry.remove("ada", "egg-row") } returns Submitted.Sent(Unit)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.remove(egg, after = 6_000)
        runCurrent()

        assertEquals("egg-row", model.removedLater.value?.row?.id)
        coVerify(exactly = 0) { pantry.remove(any(), any()) }
        advanceTimeBy(6_000)
        runCurrent()
        coVerify(exactly = 1) { pantry.remove("ada", "egg-row") }
        assertNull(model.removedLater.value)
    }

    @Test
    fun `a row taken out and taken back is never sent`() = runTest(main) {
        val pantry = pantry(garlic, egg)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.remove(egg, after = 6_000)
        model.keepRemoved()
        advanceTimeBy(12_000)
        runCurrent()

        assertNull(model.removedLater.value)
        coVerify(exactly = 0) { pantry.remove(any(), any()) }
    }

    @Test
    fun `a row still held when the screen is closed is sent all the same`() = runTest(main) {
        val pantry = pantry(garlic, egg)
        coEvery { pantry.remove("ada", "egg-row") } returns Submitted.Sent(Unit)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.remove(egg, after = 6_000)
        model.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(model)
        runCurrent()

        coVerify(exactly = 1) { pantry.remove("ada", "egg-row") }
    }

    @Test
    fun `something put in that waits leaves the queue at once, with nothing held to take back`() = runTest(main) {
        val pantry = pantry(egg)
        coEvery { pantry.add("ada", eggplant, 300.0, "g", null) } coAnswers {
            queue.submit(QueuedChange.PantryAdded("ada", "eggplant", "Eggplant", "vegetables", 300.0, "g", null, "key"), offline)
        }
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()
        model.pick(eggplant)
        model.typeQuantity("300")
        model.add()
        runCurrent()

        model.remove(model.state.value.shownRows.single { it.id == "key" }, after = 6_000)
        runCurrent()

        // Held, it could be sent meanwhile, once the network is back, and stay after its removal.
        assertNull(model.removedLater.value)
        assertEquals(listOf(egg), model.state.value.shownRows)
        assertTrue(queue.waiting.first().isEmpty())
    }

    @Test
    fun `rows taken out one after another while the first is answered are all sent, in turn`() = runTest(main) {
        val pantry = pantry(garlic, egg, oats, milk)
        val answer = CompletableDeferred<Unit>()
        coEvery { pantry.remove("ada", any()) } coAnswers { answer.await(); Submitted.Sent(Unit) }
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        listOf(garlic, egg, oats, milk).forEach { model.remove(it, after = 6_000); runCurrent() }
        coVerify(exactly = 1) { pantry.remove("ada", "garlic-row") }
        coVerify(exactly = 0) { pantry.remove("ada", "egg-row") }
        answer.complete(Unit)
        advanceTimeBy(6_000)
        runCurrent()

        listOf(garlic, egg, oats, milk).forEach { row -> coVerify(exactly = 1) { pantry.remove("ada", row.id) } }
    }

    @Test
    fun `a row goes from the pantry of the profile it was taken out of, whichever is chosen when it is sent`() = runTest(main) {
        val pantry = pantry(egg)
        coEvery { pantry.remove("ada", "egg-row") } returns Submitted.Sent(Unit)
        val model = PantryViewModel(pantry, users, queue, application, SavedStateHandle())
        runCurrent()

        model.remove(egg, after = 6_000)
        model.pickProfile("bo")
        advanceTimeBy(6_000)
        runCurrent()

        coVerify(exactly = 1) { pantry.remove("ada", "egg-row") }
        coVerify(exactly = 0) { pantry.remove("bo", any()) }
    }

    @Test
    fun `a row taken out without a network stays out of sight until the changes waiting say so`() = runTest(main) {
        val held = HeldChanges()
        val heldQueue = changeQueue(held)
        val pantry = pantry(garlic, egg)
        coEvery { pantry.remove("ada", "egg-row") } coAnswers { heldQueue.submit(QueuedChange.PantryRemoved("ada", "egg-row"), offline) }
        val model = PantryViewModel(pantry, users, heldQueue, application, SavedStateHandle())
        runCurrent()
        held.open.value = false

        model.remove(egg, after = 6_000)
        advanceTimeBy(6_000)
        runCurrent()

        // Kept to be sent, and the queue has not said it waits yet: the pantry still holds it, and it is not shown.
        assertTrue(model.state.value.shownRows.any { it.id == "egg-row" })
        assertTrue("egg-row" in model.hidden.value)
        held.open.value = true
        runCurrent()
        assertEquals(listOf(garlic), model.state.value.shownRows)
        assertTrue(model.hidden.value.isEmpty())
    }
}
