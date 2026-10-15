// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.domain.RowPacks
import net.whiteravens.dietapp.domain.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a row of a shopping list sends for what is typed into it, as the web's row sends it. */
class ShoppingRowsTest {
    /** 500 g of feta needed, 100 g of it at home, and 150 g bought beyond that: 250 g still to buy. */
    private val feta = ShoppingItem(
        id = "feta",
        name = "Feta",
        totalQuantity = 500.0,
        toBuyQuantity = 400.0,
        unit = "g",
        displayUnit = "g",
        atHome = 100.0,
        purchasedQuantity = 250.0,
        pantryBestBefore = null,
        estimatedCalories = 1300.0,
        packs = null,
        checked = false,
    )

    @Test
    fun `a field is nothing where it is empty, never under nothing, and takes a comma for the decimal sign`() {
        assertNull(typed(" "))
        assertEquals(1.5, typed("1,5")!!, 0.0)
        assertEquals(2.25, typed("2.25")!!, 0.0)
        assertEquals(0.0, typed("-3")!!, 0.0)
        assertEquals(0.0, typed("much")!!, 0.0)
    }

    @Test
    fun `what is at home is sent alone where only it changed, so that what was bought beyond it stays`() {
        assertEquals(150.0, boughtOf(feta), 0.0)
        assertEquals(RowChange(atHome = 200.0), rowChange(feta, home = 200.0, bought = 150.0))
        assertTrue(rowChange(feta, home = 100.0, bought = 150.0).isEmpty)
    }

    @Test
    fun `what was bought is sent as everything the user has, the part at home no more than the need`() {
        assertEquals(RowChange(have = 300.0), rowChange(feta, home = 100.0, bought = 200.0))
        assertEquals(RowChange(atHome = 600.0, have = 550.0), rowChange(feta, home = 600.0, bought = 50.0))
        // Nothing at home and nothing bought: what the user has is cleared.
        assertEquals(RowChange(atHome = 0.0, have = 0.0), rowChange(feta, home = 0.0, bought = 0.0))
    }

    @Test
    fun `packs taken for bought give the row what was at home and what the packs hold, and tick it`() {
        val inPacks = feta.copy(purchasedQuantity = null, packs = RowPacks(2, 200.0, "g", 400.0))

        assertTrue(inPacks.packsStand)
        assertEquals(RowChange(checked = true, have = 500.0), packsBought(inPacks))
        // Once a part of the rest was bought, the packs say nothing of what is left.
        assertTrue(!inPacks.copy(purchasedQuantity = 150.0).packsStand)
    }
}
