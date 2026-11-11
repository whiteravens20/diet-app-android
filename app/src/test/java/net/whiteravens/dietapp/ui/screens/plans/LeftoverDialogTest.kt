// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the leftover's window makes of what is said of a portion: the part of it that is left. */
class LeftoverDialogTest {
    @Test
    fun `nothing eaten leaves the whole portion, and a part chosen is that part`() {
        assertEquals(1.0, Left(eaten = false, part = 0.25).share!!, 0.0)
        assertNull(Left(eaten = true).share)
        assertEquals(0.75, Left(eaten = true, part = 0.75).share!!, 0.0)
    }

    @Test
    fun `two weights give the part that is left, a comma taken for a point, and none that cannot be`() {
        assertEquals(0.25, Left(eaten = true, byWeight = true, whole = "400", left = "100").share!!, 1e-9)
        assertEquals(0.5, Left(eaten = true, byWeight = true, whole = "420,5", left = "210,25").share!!, 1e-9)
        assertNull(Left(eaten = true, byWeight = true, whole = "400", left = "").share)
        assertNull(Left(eaten = true, byWeight = true, whole = "400", left = "400").share)
        assertTrue(Left(eaten = true, byWeight = true, whole = "400", left = "500").wrongWeights)
        assertFalse(Left(eaten = true, byWeight = true, whole = "400", left = "100").wrongWeights)
        // Weighed, nothing eaten still leaves it all.
        assertEquals(1.0, Left(eaten = false, byWeight = true, whole = "400", left = "500").share!!, 0.0)
        assertFalse(Left(eaten = false, byWeight = true, whole = "400", left = "500").wrongWeights)
    }

    @Test
    fun `why the next day cannot take it is said by its own words`() {
        assertEquals(net.whiteravens.dietapp.R.string.plans_leftover_off_no_day, leftoverOff("no_day"))
        assertEquals(net.whiteravens.dietapp.R.string.plans_leftover_off_skipped, leftoverOff("skipped"))
        assertEquals(net.whiteravens.dietapp.R.string.plans_leftover_off_no_meal, leftoverOff("no_meal"))
    }
}
