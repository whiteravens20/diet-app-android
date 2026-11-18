// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** How near a pantry row's best-before day is, and how what goes into the freezer is split into packages. */
class PantryRulesTest {

    @Test
    fun `a row goes soon from two days before its day to the day itself, and is past it from the day after`() {
        val today = LocalDate.parse("2026-10-17")

        assertEquals(Freshness.FINE, freshness(null, today))
        assertEquals(Freshness.FINE, freshness(LocalDate.parse("2026-10-20"), today))
        assertEquals(Freshness.SOON, freshness(LocalDate.parse("2026-10-19"), today))
        assertEquals(Freshness.SOON, freshness(today, today))
        assertEquals(Freshness.PAST, freshness(LocalDate.parse("2026-10-16"), today))
    }

    @Test
    fun `what is weighed or measured is split into equal parts, what rounding leaves going with the last`() {
        assertEquals(listOf(250.0, 250.0, 250.0, 250.0), portionsOf(1000.0, "g", 4))
        assertEquals(listOf(333.333333, 333.333333, 333.333334), portionsOf(1000.0, "g", 3))
        assertEquals(listOf(750.0), portionsOf(750.0, "ml", 1))
    }

    @Test
    fun `what is counted is split into whole pieces as evenly as they go, the larger parts first`() {
        assertEquals(listOf(2.0, 2.0, 1.0, 1.0), portionsOf(6.0, "piece", 4))
        assertEquals(listOf(2.0, 2.0, 2.0), portionsOf(6.0, "piece", 3))
        assertEquals(listOf(1.0, 1.5), portionsOf(2.5, "piece", 2))
    }

    @Test
    fun `nothing is split into parts of less than one of its unit`() {
        assertNull(portionsOf(3.0, "piece", 4))
        assertNull(portionsOf(5.0, "g", 10))
    }

    @Test
    fun `the freezer takes from one to ten portions, typed as a whole number`() {
        assertEquals(1, portionCount("1"))
        assertEquals(10, portionCount(" 10 "))
        assertNull(portionCount("0"))
        assertNull(portionCount("11"))
        assertNull(portionCount("2.5"))
        assertNull(portionCount(""))
    }
}
