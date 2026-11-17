// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import org.junit.Assert.assertEquals
import org.junit.Test

/** A meal is said to hold its recipe's servings where the two are the same as the note shows them, and only then. */
class SameServingsTest {
    @Test
    fun `the same as shown to a tenth, the meal's note`() {
        assertEquals(true, sameServings(1, 1.0))
        // Shown as 1: the same number twice is what the note says once.
        assertEquals(true, sameServings(1, 1.04))
        assertEquals(false, sameServings(1, 1.25))
        assertEquals(false, sameServings(2, 1.5))
        assertEquals(true, sameServings(4, 3.96))
    }

    @Test
    fun `the same as shown to a hundredth, the pot's note`() {
        assertEquals(true, sameServings(2, 2.0, fractionDigits = 2))
        assertEquals(false, sameServings(2, 1.95, fractionDigits = 2))
        assertEquals(false, sameServings(4, 3.9, fractionDigits = 2))
        assertEquals(true, sameServings(4, 3.999, fractionDigits = 2))
    }
}
