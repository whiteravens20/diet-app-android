// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import net.whiteravens.dietapp.domain.OverviewDay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the plan's card draws its days: where they differ, as the web's card draws them. */
class DaysDifferTest {
    private fun day(calories: Double, target: Double = 1900.0, skipped: Boolean = false) =
        OverviewDay("2026-01-05", calories, target, skipped)

    @Test
    fun `days that hold the same are not drawn, and a day skipped, targets of their own or a twentieth apart are`() {
        assertFalse(daysDiffer(listOf(day(1890.0), day(1905.0), day(1898.0))))
        assertFalse(daysDiffer(listOf(day(1900.0))))
        assertTrue(daysDiffer(listOf(day(1868.0), day(0.0, skipped = true), day(1911.0))))
        assertTrue(daysDiffer(listOf(day(1900.0), day(1900.0, target = 1700.0))))
        assertTrue(daysDiffer(listOf(day(1860.0), day(1955.0), day(1900.0))))
    }
}
