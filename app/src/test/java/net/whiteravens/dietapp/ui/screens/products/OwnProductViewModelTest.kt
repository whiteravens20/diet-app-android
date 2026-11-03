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

/** The window a product of one's own is written in. */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnProductViewModelTest {
    private val products = mockk<ProductRepository>()
    private val base = Product("b", "5900512320359", "Mleko", null, null, "ml", null, null, 60.0, null, null, null, null, null, null, null, false, false)
    private val mine = OwnProduct(base.copy(id = "m", source = ProductSource.OWN, name = "Moje"), OwnProductState.OWN, null)

    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model(code: String = "5900512320359") = OwnProductViewModel(products).apply {
        open(null, code)
        type { it.copy(name = "Moje", calories = "60") }
    }

    @Test
    fun `says what is wrong only once it was sent, and sends nothing wrong`() {
        val model = OwnProductViewModel(products)
        model.open(null, "")
        assertFalse(model.state.value.showProblems)

        model.save()

        assertTrue(model.state.value.showProblems)
        assertEquals(1, model.state.value.refusals)
        assertNull(model.state.value.saved)
        coVerify(exactly = 0) { products.write(any()) }
    }

    @Test
    fun `writes a new product, and has done its work`() {
        coEvery { products.write(any()) } returns mine
        val model = model()

        model.save()

        assertEquals(mine, model.state.value.saved)
        assertFalse(model.state.value.saving)
        coVerify { products.write(match { it.code == "5900512320359" && it.name == "Moje" && it.calories == 60.0 }) }
    }

    @Test
    fun `changes the product it was opened on and no other`() {
        coEvery { products.change("m", any()) } returns mine
        val model = OwnProductViewModel(products)
        model.open(mine, "")

        model.save()

        assertEquals(mine, model.state.value.saved)
        coVerify(exactly = 0) { products.write(any()) }
    }

    @Test
    fun `starts a correction from what the base says of the product, its code included, and writes it as a product of one's own`() {
        coEvery { products.write(any()) } returns mine
        val model = OwnProductViewModel(products)
        model.open(null, "", from = base.copy(protein = 3.0, packAmount = 1000.0))

        assertEquals("5900512320359", model.state.value.draft.code)
        assertEquals("Mleko", model.state.value.draft.name)
        assertEquals("60", model.state.value.draft.calories)
        assertEquals("3", model.state.value.draft.protein)
        assertEquals("1000", model.state.value.draft.packAmount)
        assertEquals("", model.state.value.draft.fat)

        model.type { it.copy(calories = "64") }
        model.save()

        assertEquals(mine, model.state.value.saved)
        coVerify { products.write(match { it.code == "5900512320359" && it.calories == 64.0 }) }
        coVerify(exactly = 0) { products.change(any(), any()) }
    }

    @Test
    fun `says a refusal and keeps what was typed`() {
        val limit = ApiException(409, "OWN_PRODUCT_LIMIT", "Too many.")
        coEvery { products.write(any()) } throws limit
        val model = model()

        model.save()

        assertEquals(limit, model.state.value.refused)
        assertEquals("Moje", model.state.value.draft.name)
        assertNull(model.state.value.saved)
    }

    @Test
    fun `takes a refusal away when something is typed`() {
        coEvery { products.write(any()) } throws IOException("no")
        val model = model()
        model.save()
        assertTrue(model.state.value.refused is IOException)

        model.type { it.copy(brand = "Marka") }

        assertNull(model.state.value.refused)
    }

    @Test
    fun `opening again starts again`() {
        coEvery { products.write(any()) } returns mine
        val model = model()
        model.save()

        model.open(null, "")

        assertNull(model.state.value.saved)
        assertEquals(OwnProductDraft(), model.state.value.draft)
    }
}
