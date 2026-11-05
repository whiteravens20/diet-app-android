// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import net.whiteravens.dietapp.data.remote.DisplayAmountDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What an amount in pieces weighs, read as the API writes it (packages/shared enums.ts, shopping-list.ts). */
class AmountMappingTest {
    private val json = AppModule.json()

    @Test
    fun `an amount in pieces carries what it weighs, and one of an instance that does not say it carries none`() {
        assertEquals(110.0, json.decodeFromString<DisplayAmountDto>("""{"quantity":2,"unit":"piece","grams":110}""").grams)
        assertNull(json.decodeFromString<DisplayAmountDto>("""{"quantity":120,"unit":"g"}""").grams)
    }

    @Test
    fun `a row of a shopping list weighs its pieces, and nothing where it is weighed or comes to nothing`() {
        val eggs = ShoppingItem(
            id = "r", name = "Eggs", totalQuantity = 6.0, toBuyQuantity = 6.0, unit = "piece", displayUnit = "piece",
            atHome = 0.0, purchasedQuantity = null, pantryBestBefore = null, estimatedCalories = 0.0, packs = null,
            checked = false, gramsPerPiece = 55.0,
        )

        assertEquals(330.0, eggs.gramsOf(6.0)!!, 0.0)
        assertNull(eggs.gramsOf(0.0))
        assertNull(eggs.copy(gramsPerPiece = null).gramsOf(6.0))
    }
}
