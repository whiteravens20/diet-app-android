// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The split of energy the macro ring shows, as the web application shows it. */
class EnergySplitTest {
    @Test
    fun `is what each macronutrient gives at 4, 9 and 4 kcal a gram, not what it weighs`() {
        // 150 g, 70 g and 200 g weigh 36%, 17% and 48% of the total; their energy is 600, 630 and 800 kcal.
        assertEquals(listOf(600.0, 630.0, 800.0), energyOf(listOf(150.0, 70.0, 200.0)))
    }
}
