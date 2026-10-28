// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import net.whiteravens.dietapp.testing.refusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The library's figures as typed into its window of filters, and the API's refusal of them. */
class RecipeFiltersTest {
    @Test
    fun `a figure is read with a comma or a point, and nothing else is one`() {
        assertEquals(12.5, decimal("12,5")!!, 0.0)
        assertEquals(12.5, decimal(" 12.5 ")!!, 0.0)
        assertNull(decimal("much"))
        assertNull(decimal(""))
        assertNull(decimal("NaN"))
    }

    @Test
    fun `each figure is the one the API names when it refuses it`() {
        // The API's answer to every figure past its bounds.
        val refused = refusal("recipes-refused").fieldRefusals.keys

        assertEquals(FilterFigure.entries.map { it.key }, refused.toList())
    }
}
