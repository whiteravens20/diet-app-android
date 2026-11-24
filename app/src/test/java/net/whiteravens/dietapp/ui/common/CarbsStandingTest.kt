// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CarbsStandingTest {
    @Test
    fun `an amount is over the ceiling, under the floor or in the band, in the whole grams it is shown in`() {
        // Low-carb's band, from its floor to its ceiling.
        assertEquals(CarbsStanding.OVER, carbsStanding(140.0, 120.0, 60.0))
        assertEquals(CarbsStanding.IN, carbsStanding(120.4, 120.0, 60.0))
        assertEquals(CarbsStanding.OVER, carbsStanding(120.6, 120.0, 60.0))
        assertEquals(CarbsStanding.IN, carbsStanding(59.6, 120.0, 60.0))
        assertEquals(CarbsStanding.UNDER, carbsStanding(59.4, 120.0, 60.0))
        assertEquals(CarbsStanding.IN, carbsStanding(90.0, 120.0, 60.0))
        // Keto's band is a ceiling alone: nothing is under it.
        assertEquals(CarbsStanding.IN, carbsStanding(0.0, 30.0, 0.0))
        assertEquals(CarbsStanding.OVER, carbsStanding(31.0, 30.0, 0.0))
        // A diet without a band says the target, not a standing.
        assertEquals(CarbsStanding.IN, carbsStanding(400.0, 250.0, null))
    }
}
