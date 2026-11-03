// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.products

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.ProductRepository
import net.whiteravens.dietapp.domain.OwnProduct
import net.whiteravens.dietapp.domain.OwnProductState
import net.whiteravens.dietapp.domain.Product
import net.whiteravens.dietapp.domain.ProductSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The account's products, as the screen keeps them. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductsViewModelTest {
    private val products = mockk<ProductRepository>()
    private fun aProduct(code: String? = "5900512320359", protein: Double? = 8.0, fat: Double? = 3.0, carbs: Double? = 45.0) =
        Product("p", code, "Chleb", null, null, "g", null, null, 245.0, protein, fat, carbs, null, null, null, null, false, false, ProductSource.OWN)
    private val own = OwnProduct(aProduct(), OwnProductState.OWN, null)

    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model(vararg items: OwnProduct): ProductsViewModel {
        coEvery { products.mine() } returns items.toList()
        return ProductsViewModel(products)
    }

    @Test
    fun `reads the list on opening`() {
        val model = model(own)

        assertFalse(model.state.value.loading)
        assertEquals(listOf(own), model.state.value.items)
    }

    @Test
    fun `says why the list could not be read, and reads it on a word`() {
        coEvery { products.mine() } throws IOException("no") andThen listOf(own)
        val model = ProductsViewModel(products)
        assertTrue(model.state.value.failed is IOException)

        model.read()

        assertNull(model.state.value.failed)
        assertEquals(listOf(own), model.state.value.items)
    }

    @Test
    fun `a product goes to the base with its code and all its figures`() {
        assertTrue(own.sendable)
        assertFalse(own.copy(product = aProduct(code = null)).sendable)
        assertFalse(own.copy(product = aProduct(protein = null)).sendable)
        assertFalse(own.copy(product = aProduct(fat = null)).sendable)
        assertFalse(own.copy(product = aProduct(carbs = null)).sendable)
    }

    @Test
    fun `asks before it sends, sends on the word, and reads the list again`() {
        val waiting = own.copy(state = OwnProductState.SENT)
        val model = model(own)
        model.ask("p", ProductsViewModel.Ask.SEND)
        assertEquals("p" to ProductsViewModel.Ask.SEND, model.state.value.asking)
        coVerify(exactly = 0) { products.send(any()) }
        coEvery { products.send("p") } returns waiting
        coEvery { products.mine() } returns listOf(waiting)

        model.send("p")

        assertNull(model.state.value.asking)
        assertEquals(listOf(waiting), model.state.value.items)
    }

    @Test
    fun `lets a question go unanswered`() {
        val model = model(own)
        model.ask("p", ProductsViewModel.Ask.REMOVE)

        model.notNow()

        assertNull(model.state.value.asking)
        coVerify(exactly = 0) { products.remove(any()) }
    }

    @Test
    fun `says why something was refused, and shows the list as it now is`() {
        val model = model(own)
        val limit = ApiException(429, "OWN_PRODUCT_SEND_LIMIT", "Too many.")
        coEvery { products.send("p") } throws limit

        model.send("p")

        assertEquals(limit, model.state.value.refused)
        assertEquals("p", model.state.value.refusedOn)
        assertFalse(model.state.value.busy)
        coVerify(exactly = 2) { products.mine() }
    }

    @Test
    fun `removes and takes back`() {
        val model = model(own)
        coEvery { products.withdraw("p") } returns own
        coEvery { products.remove("p") } returns Unit

        model.withdraw("p")
        model.remove("p")

        coVerify { products.withdraw("p") }
        coVerify { products.remove("p") }
    }

    @Test
    fun `writes a new product or changes one in a window, and reads the list again when it is saved`() {
        val model = model(own)

        model.openNew()
        assertTrue(model.state.value.writing)
        assertNull(model.state.value.writingOn)

        model.openOn(own)
        assertEquals(own, model.state.value.writingOn)

        model.saved()
        assertFalse(model.state.value.writing)
        coVerify(exactly = 2) { products.mine() }
    }

    @Test
    fun `saves the file where it was asked to, and says so`() {
        val model = model(own)
        coEvery { products.additions() } returns """{"license":"ODbL-1.0","products":[]}"""
        var written = ""

        model.saveAdditions { written = it }

        assertEquals("""{"license":"ODbL-1.0","products":[]}""", written)
        assertTrue(model.state.value.fileSaved)
    }

    @Test
    fun `says why the file was not given, and writes nothing`() {
        val model = model(own)
        val limit = ApiException(429, "RATE_LIMITED", "Wait.")
        coEvery { products.additions() } throws limit
        var written = false

        model.saveAdditions { written = true }

        assertFalse(written)
        assertEquals(limit, model.state.value.refused)
        assertNull(model.state.value.refusedOn)
        assertFalse(model.state.value.fileSaved)
    }
}
