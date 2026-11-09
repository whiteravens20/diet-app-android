// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** A dish cooked once split into its portions, as the web's calculator splits it. */
class SplitTest {
    @Test
    fun `gives each portion its share by the energy the plan gives it`() {
        val (monday, tuesday) = sharesOf(listOf(640.0, 560.0))
        assertEquals(640.0 / 1200, monday, 1e-9)
        assertEquals(560.0 / 1200, tuesday, 1e-9)
    }

    @Test
    fun `shares equally where no portion says what it holds, and nothing to one of unknown energy beside known ones`() {
        assertEquals(listOf(1.0 / 3, 1.0 / 3, 1.0 / 3), sharesOf(listOf(null, null, null)))
        assertEquals(listOf(0.5, 0.5), sharesOf(listOf(0.0, 0.0)))
        assertEquals(listOf(1.0, 0.0), sharesOf(listOf(500.0, null)))
    }

    @Test
    fun `splits into whole grams, the last taking what is left, adding up to what was weighed`() {
        assertEquals(listOf(667L, 583L), splitWhole(1250.0, sharesOf(listOf(640.0, 560.0))))
        assertEquals(listOf(333L, 334L, 333L), splitWhole(1000.0, sharesOf(listOf(null, null, null))))
    }

    @Test
    fun `keeps every portion within one of its exact part and the sum exact, whatever the shares`() {
        listOf(
            1000.0 to listOf(1.0, 1.0, 1.0),
            7.0 to listOf(3.0, 2.0, 2.0),
            3.0 to listOf(700.0, 450.0),
            1.0 to listOf(600.0, 400.0),
            2345.0 to listOf(512.0, 498.0, 731.0, 260.0),
        ).forEach { (whole, kcal) ->
            val shares = sharesOf(kcal)
            val portions = splitWhole(whole, shares)
            assertEquals(whole.toLong(), portions.sum())
            portions.forEachIndexed { index, portion ->
                assertTrue(portion >= 0)
                assertTrue(abs(portion - whole * shares[index]) <= 1.0)
            }
        }
    }

    @Test
    fun `splits three eggs between two portions as two and one, and counts a fraction of a gram as the scale shows it`() {
        assertEquals(listOf(2L, 1L), splitWhole(3.0, sharesOf(listOf(700.0, 450.0))))
        assertEquals(listOf(625L, 625L), splitWhole(1250.4, listOf(0.5, 0.5)))
    }

    @Test
    fun `weighs the food alone as what the scale shows less the empty pot, and as nothing where the pot weighs as much or more`() {
        assertEquals(1250.0, foodWeight(1250.0, null)!!, 0.0)
        assertEquals(1250.0, foodWeight(2660.0, 1410.0)!!, 0.0)
        assertNull(foodWeight(1410.0, 1410.0))
        assertNull(foodWeight(1000.0, 1410.0))
        assertNull(foodWeight(0.0, null))
    }
}
