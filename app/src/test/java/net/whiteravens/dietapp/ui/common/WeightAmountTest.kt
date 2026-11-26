// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** A weight said in the account's unit, as the paces to choose from say it. */
class WeightAmountTest {
    private val polish = Locale.forLanguageTag("pl-PL")
    private val english = Locale.forLanguageTag("en-US")

    @Test
    fun `kilograms are said to the hundredth, so that a slowed pace is not the pace asked for`() {
        // Half a kilogram a week asked, a quarter of maintenance taken: 492 kcal a day lose 0.45 kg, not 0.5.
        assertEquals("0,45\u00A0kg", weightIn(polish, 0.45, WeightUnit.KG))
        assertEquals("0,5\u00A0kg", weightIn(polish, 0.5, WeightUnit.KG))
        assertEquals("0.25\u00A0kg", weightIn(english, 0.25, WeightUnit.KG))
    }

    @Test
    fun `pounds are said to the tenth`() {
        assertEquals("1.3\u00A0lb", weightIn(english, 0.57, WeightUnit.LB))
        assertEquals("1.1\u00A0lb", weightIn(english, 0.5, WeightUnit.LB))
    }
}
