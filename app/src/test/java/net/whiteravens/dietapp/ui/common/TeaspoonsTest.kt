// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** Teaspoons as the API rounds them, written as a cook writes them, with the form of the noun that follows. */
class TeaspoonsTest {
    @Test
    fun `a part of a teaspoon, a whole one and a part, and whole ones each take their own form`() {
        assertEquals("¼" to SpoonForm.PART, teaspoons(0.25))
        assertEquals("½" to SpoonForm.PART, teaspoons(0.5))
        assertEquals("1" to SpoonForm.WHOLE, teaspoons(1.0))
        assertEquals("1½" to SpoonForm.MIXED, teaspoons(1.5))
        assertEquals("4" to SpoonForm.WHOLE, teaspoons(4.0))
    }
}
