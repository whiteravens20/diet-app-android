// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** The line under "Dziś" says a deficit, a surplus, or the weight kept where the target is the expenditure. */
class TargetGapTest {
    @Test
    fun `a deficit and a surplus by the whole kcal, and the weight kept where the gap rounds to nothing`() {
        assertEquals(TargetGap.Deficit(545), targetGap(545.2))
        assertEquals(TargetGap.Surplus(300), targetGap(-300.0))
        assertEquals(TargetGap.Keep, targetGap(0.0))
        assertEquals(TargetGap.Keep, targetGap(0.4))
        assertEquals(TargetGap.Keep, targetGap(-0.4))
    }
}
