// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The units the pantry offers an ingredient in, held to what the API converts (engine/units.ts:
 * a piece needs the weight of one, grams and millilitres the weight of a millilitre), the unit
 * offered first, and how near a row's best-before day is.
 */
class PantryRulesTest {

    @Test
    fun `an ingredient is kept in the unit it is weighed in, and in pieces where a piece has a weight`() {
        assertEquals(listOf("g"), pantryUnits("g", gramsPerPiece = null, density = null))
        assertEquals(listOf("g", "piece"), pantryUnits("g", gramsPerPiece = 55.0, density = null))
        assertEquals(listOf("g", "ml", "piece"), pantryUnits("g", gramsPerPiece = 55.0, density = 1.03))
    }

    @Test
    fun `what is measured is kept in grams where a millilitre has a weight, and in pieces only where a piece has one too`() {
        assertEquals(listOf("ml"), pantryUnits("ml", gramsPerPiece = null, density = null))
        assertEquals(listOf("g", "ml"), pantryUnits("ml", gramsPerPiece = null, density = 1.03))
        assertEquals(listOf("ml"), pantryUnits("ml", gramsPerPiece = 200.0, density = null))
        assertEquals(listOf("g", "ml", "piece"), pantryUnits("ml", gramsPerPiece = 200.0, density = 1.03))
    }

    @Test
    fun `what is counted is kept in grams where a piece has a weight, and in millilitres where a millilitre has one too`() {
        assertEquals(listOf("piece"), pantryUnits("piece", gramsPerPiece = null, density = 1.0))
        assertEquals(listOf("g", "piece"), pantryUnits("piece", gramsPerPiece = 30.0, density = null))
        assertEquals(listOf("g", "ml", "piece"), pantryUnits("piece", gramsPerPiece = 30.0, density = 1.0))
    }

    @Test
    fun `a weight of nothing converts nothing`() {
        assertEquals(listOf("g"), pantryUnits("g", gramsPerPiece = 0.0, density = 0.0))
    }

    @Test
    fun `what is counted is put in in pieces of its own, where a piece of it can be kept, and the rest as it is weighed or measured`() {
        assertEquals("piece", firstPantryUnit(listOf("g", "piece"), "g", displayUnit = "clove"))
        assertEquals("g", firstPantryUnit(listOf("g", "piece"), "g", displayUnit = null))
        assertEquals("ml", firstPantryUnit(listOf("ml"), "ml", displayUnit = "piece"))
    }

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
