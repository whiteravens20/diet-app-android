// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The split of energy the macro ring and its legend show, as the web application shows it. */
class EnergySplitTest {
    @Test
    fun `is what each macronutrient gives at 4, 9 and 4 kcal a gram, not what it weighs`() {
        // 150 g, 70 g and 200 g weigh 36%, 17% and 48% of the total; their energy is 600, 630 and 800 kcal.
        assertEquals(listOf(600.0, 630.0, 800.0), energyOf(listOf(150.0, 70.0, 200.0)))
        assertEquals(listOf(30, 31, 39), energyPercents(listOf(150.0, 70.0, 200.0)))
    }

    @Test
    fun `gives whole percents that add up to a hundred, the remainders going to the largest`() {
        // A third each: 33% three times would add up to 99.
        assertEquals(listOf(34, 33, 33), energyPercents(listOf(100.0, 400.0 / 9, 100.0)))
        // A keto target of 1680 kcal: 105 g, 131 g and 21 g are its 25/70/5.
        assertEquals(listOf(25, 70, 5), energyPercents(listOf(105.0, 131.0, 21.0)))
    }

    @Test
    fun `is nothing for nothing`() {
        assertNull(energyPercents(listOf(0.0, 0.0, 0.0)))
    }
}
