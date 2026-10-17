// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.pantry

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.PantryRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.PantryChange
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.StockableIngredient
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** A profile's pantry as the screen keeps it: read, something found and put in, and a row changed or taken out where it is shown. */
@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {
    private val main = StandardTestDispatcher()

    @Before
    fun mainThread() = Dispatchers.setMain(main)

    @After
    fun reset() = Dispatchers.resetMain()

    private val garlic = PantryRow("garlic-row", "garlic", "Garlic", "spices", 3.0, "clove", LocalDate.parse("2026-11-06"))
    private val egg = PantryRow("egg-row", "egg", "Large egg", "dairy", 6.0, "piece", LocalDate.parse("2026-10-22"))
    private val oats = PantryRow("oats-row", "oats", "Rolled oats", "grains", 212.0, "g", null)
    private val milk = PantryRow("milk-row", "milk", "Whole milk", "dairy", 1000.0, "ml", null)

    private val eggs = StockableIngredient("egg", "Large egg", listOf("g", "piece"), "piece", "piece")
    private val eggplant = StockableIngredient("eggplant", "Eggplant", listOf("g"), "g", "piece")

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
        val model = PantryViewModel(pantry, users)
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
        val model = PantryViewModel(pantry, users)
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
    fun `an ingredient picked is put in in the unit it is counted in at first, the search emptied`() = runTest(main) {
        val model = PantryViewModel(pantry(), users)
        runCurrent()
        model.chooseUnit("ml")

        model.pick(eggs)

        assertEquals(eggs, model.state.value.form.picked)
        assertEquals("piece", model.state.value.form.unit)
        assertEquals("", model.state.value.form.search)
    }

    @Test
    fun `an amount is typed with a comma or a point, and nothing else is taken, nor a number under nothing`() = runTest(main) {
        val model = PantryViewModel(pantry(), users)
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
        coEvery { pantry.add("ada", "egg", 6.5, "piece", LocalDate.parse("2026-10-22")) } returns egg
        val model = PantryViewModel(pantry, users)
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
    fun `something refused stays in the form to be put right, and the refusal is said with what could not be done`() = runTest(main) {
        val pantry = pantry()
        coEvery { pantry.add(any(), any(), any(), any(), any()) } throws refused("QUANTITY_TOO_LARGE")
        val model = PantryViewModel(pantry, users)
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
        coEvery { pantry.change("egg-row", PantryChange(quantity = 3.0, newDay = true, bestBefore = null)) } returns halved
        coEvery { pantry.change("garlic-row", PantryChange(quantity = 2000.0)) } throws refused("QUANTITY_TOO_LARGE")
        val model = PantryViewModel(pantry, users)
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
        coEvery { pantry.remove("egg-row") } just runs
        coEvery { pantry.remove("garlic-row") } throws ApiException(404, "INVENTORY_ITEM_NOT_FOUND", "")
        val model = PantryViewModel(pantry, users)
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
        coEvery { pantry.change(any(), any()) } returns egg
        val model = PantryViewModel(pantry, users)
        runCurrent()
        model.pick(eggplant)
        model.typeQuantity("300")

        model.change(egg, PantryChange(quantity = 5.0))
        assertFalse(model.state.value.canAdd)
        model.add()
        runCurrent()

        coVerify(exactly = 0) { pantry.add(any(), any(), any(), any(), any()) }
    }
}
