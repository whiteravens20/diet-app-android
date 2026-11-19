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
    fun `what is weighed or measured is split into whole grams or millilitres as evenly as they go, the larger parts first`() {
        assertEquals(listOf(250.0, 250.0, 250.0, 250.0), portionsOf(1000.0, 4))
        assertEquals(listOf(167.0, 167.0, 166.0), portionsOf(500.0, 3))
        assertEquals(listOf(164.0, 163.0, 163.0), portionsOf(490.0, 3))
        assertEquals(listOf(750.0), portionsOf(750.0, 1))
    }

    @Test
    fun `what is counted is split into whole pieces as evenly as they go, the larger parts first`() {
        assertEquals(listOf(2.0, 2.0, 1.0, 1.0), portionsOf(6.0, 4))
        assertEquals(listOf(2.0, 2.0, 2.0), portionsOf(6.0, 3))
    }

    @Test
    fun `what is less than one of the unit goes with the last part`() {
        assertEquals(listOf(1.0, 1.5), portionsOf(2.5, 2))
        assertEquals(listOf(167.0, 167.0, 166.4), portionsOf(500.4, 3))
        // The noise of converting units is no part of a gram.
        assertEquals(listOf(167.0, 167.0, 166.0), portionsOf(499.9999999, 3))
    }

    @Test
    fun `every gram is kept, the parts adding up to the whole`() {
        listOf(1000.0 to 3, 737.25 to 7, 10.0 to 10, 12.5 to 4).forEach { (quantity, portions) ->
            val parts = portionsOf(quantity, portions)!!
            assertEquals(portions, parts.size)
            assertEquals(quantity, parts.sum(), 1e-6)
        }
    }

    @Test
    fun `nothing is split into parts of less than one of its unit`() {
        assertNull(portionsOf(3.0, 4))
        assertNull(portionsOf(5.0, 10))
        assertNull(portionsOf(9.5, 10))
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
