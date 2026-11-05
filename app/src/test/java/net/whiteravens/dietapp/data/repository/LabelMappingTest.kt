// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import net.whiteravens.dietapp.data.remote.LabelTotalsDto
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.LabelFigure
import net.whiteravens.dietapp.domain.LabelNutrient
import net.whiteravens.dietapp.domain.LabelTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fibre, sugars, salt and saturated fat, read as the API writes them (packages/shared nutrition.ts).
 */
class LabelMappingTest {
    private val json = AppModule.json()

    @Test
    fun `a sum is read with what is only at least so much and what is not known`() {
        val totals = json.decodeFromString<LabelTotalsDto>(
            """{"fiber":{"grams":12,"atLeast":false},"sugars":{"grams":26.2,"atLeast":true},"salt":null,"saturatedFat":{"grams":0,"atLeast":false}}""",
        ).toDomain()

        assertEquals(LabelTotals(LabelFigure(12.0, false), LabelFigure(26.2, true), null, LabelFigure(0.0, false)), totals)
        assertTrue(totals.anyAtLeast)
        assertTrue(totals.anyUnknown)
        assertEquals(LabelFigure(26.2, true), totals[LabelNutrient.SUGARS])
    }

    @Test
    fun `a sum every part of which is known says neither at least nor not known`() {
        val totals = LabelTotals(LabelFigure(1.0, false), LabelFigure(2.0, false), LabelFigure(0.3, false), LabelFigure(0.0, false))

        assertFalse(totals.anyAtLeast)
        assertFalse(totals.anyUnknown)
    }
}
