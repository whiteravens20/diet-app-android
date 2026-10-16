// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The library's figures held to the bounds the API takes before anything is sent. */
class RecipeFilterBoundsTest {
    @Test
    fun `a figure past the API's bounds is outside them, and an empty field is no figure at all`() {
        assertTrue(outside("10001", CALORIES))
        assertTrue(outside("0", CALORIES))
        assertFalse(outside("600", CALORIES))
        assertTrue(outside("1441", MINUTES))
        assertTrue(outside("500,5", PROTEIN_OR_FAT))
        assertFalse(outside("1000", CARBS))
        assertFalse(outside("", CARBS))
    }

    @Test
    fun `a figure is read with a comma or a point, and nothing else is one`() {
        assertEquals(12.5, decimal("12,5")!!, 0.0)
        assertEquals(12.5, decimal(" 12.5 ")!!, 0.0)
        assertNull(decimal("much"))
        assertNull(decimal(""))
    }
}
