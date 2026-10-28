// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** How near a pantry row's best-before day is. */
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
}
