// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.products

import net.whiteravens.dietapp.domain.Product
import net.whiteravens.dietapp.domain.ProductSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a form says of a product of one's own, which is what the API holds it to. */
class OwnProductDraftTest {
    private val enough = OwnProductDraft(name = "Chleb babci", calories = "245")

    private fun problemOf(draft: OwnProductDraft, field: OwnField) = draft.problems()[field]

    @Test
    fun `needs no more than a name and the calories, and sends what is left empty as nothing`() {
        val product = enough.copy(name = "  Chleb babci ", calories = "245,5", brand = " ").toNew()

        assertNotNull(product)
        assertEquals("Chleb babci", product!!.name)
        assertEquals(245.5, product.calories, 0.0)
        assertNull(product.code)
        assertNull(product.brand)
        assertNull(product.protein)
        assertEquals("g", product.unit)
    }

    @Test
    fun `says that a name holds a letter or a digit and that the calories are needed`() {
        val problems = OwnProductDraft(name = " - ").problems()

        assertEquals(OwnProblem.NAME, problems[OwnField.NAME])
        assertEquals(OwnProblem.CALORIES, problems[OwnField.CALORIES])
        assertNull(OwnProductDraft(name = " - ").toNew())
    }

    @Test
    fun `takes a code however it is read, and says of one that is none that it is none`() {
        assertNull(problemOf(enough.copy(code = "590-0512-320359"), OwnField.CODE))
        assertEquals(OwnProblem.CODE, problemOf(enough.copy(code = "123"), OwnField.CODE))
        assertEquals(OwnProblem.CODE, problemOf(enough.copy(code = "5900512320358"), OwnField.CODE))
    }

    @Test
    fun `holds a figure to what can be in 100, and the calories to what food has`() {
        assertEquals(OwnProblem.CALORIES, problemOf(enough.copy(calories = "901"), OwnField.CALORIES))
        assertNull(problemOf(enough.copy(calories = "900"), OwnField.CALORIES))
        assertEquals(OwnProblem.FIGURE, problemOf(enough.copy(protein = "101"), OwnField.PROTEIN))
        assertEquals(OwnProblem.FIGURE, problemOf(enough.copy(salt = "dużo"), OwnField.SALT))
        assertEquals(OwnProblem.FIGURE, problemOf(enough.copy(fiber = "-1"), OwnField.FIBER))
        assertNull(problemOf(enough.copy(fiber = "0"), OwnField.FIBER))
    }

    @Test
    fun `holds an amount of a pack or a serving above nothing and to what a pack can hold`() {
        assertEquals(OwnProblem.AMOUNT, problemOf(enough.copy(packAmount = "0"), OwnField.PACK_AMOUNT))
        assertEquals(OwnProblem.AMOUNT, problemOf(enough.copy(servingAmount = "100001"), OwnField.SERVING_AMOUNT))
        assertNull(problemOf(enough.copy(packAmount = "500", servingAmount = "50"), OwnField.PACK_AMOUNT))
    }

    @Test
    fun `refuses figures that cannot be true together, and what merely comes close`() {
        val tooMuch = enough.copy(protein = "50", fat = "10", saturatedFat = "11", carbs = "50", sugars = "51").problems()

        assertEquals(OwnProblem.MACROS, tooMuch[OwnField.CARBS])
        assertEquals(OwnProblem.SATURATED, tooMuch[OwnField.SATURATED])
        assertEquals(OwnProblem.SUGARS, tooMuch[OwnField.SUGARS])

        val closeEnough = enough.copy(protein = "50", fat = "10", saturatedFat = "10,5", carbs = "45", sugars = "45,5").problems()
        assertTrue(closeEnough.toString(), closeEnough.isEmpty())
    }

    @Test
    fun `a change starts from the product as it stands, and a new one from the code it is written for`() {
        val product = Product(
            "p", "5900512320359", "Mleko", "Marka", "1 l", "ml", 1000.0, 250.0,
            60.0, 3.2, null, 4.7, null, null, null, null, false, false, ProductSource.OWN,
        )

        val draft = OwnProductDraft.of(product, "ignored")

        assertEquals("5900512320359", draft.code)
        assertEquals("ml", draft.unit)
        assertEquals("", draft.fat)
        assertEquals(OwnProductDraft(code = "123"), OwnProductDraft.of(null, "123"))
    }
}
