// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import io.mockk.every
import io.mockk.mockk
import net.whiteravens.dietapp.domain.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The portion a meal's card says in words: from a tenth off its plan's portion, as the web's card says it. */
class PortionChipTest {
    private fun meal(servings: Double, scale: Double, leftover: Boolean = false, carried: Boolean = false) = mockk<Meal>().also { meal ->
        every { meal.servings } returns servings
        every { meal.quantityScale } returns scale
        every { meal.leftover } returns if (leftover) mockk() else null
        every { meal.carriedTo } returns if (carried) mockk() else null
    }

    @Test
    fun `says the portion of a meal sized a tenth or more off its plan's, to a tenth`() {
        assertEquals(1.2, portionChip(meal(1.0, 1.22))!!, 0.0)
        assertEquals(0.2, portionChip(meal(1.0, 0.2))!!, 0.0)
        assertEquals(2.2, portionChip(meal(2.0, 1.1))!!, 0.0)
    }

    @Test
    fun `says nothing of a smaller sizing`() {
        assertNull(portionChip(meal(2.0, 1.06)))
        assertNull(portionChip(meal(1.0, 0.95)))
    }

    @Test
    fun `says nothing of a leftover or of the meal it was left of`() {
        assertNull(portionChip(meal(1.0, 0.5, leftover = true)))
        assertNull(portionChip(meal(1.0, 0.5, carried = true)))
    }
}
