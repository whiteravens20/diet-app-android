// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How well the catalogue serves a diet at a number of meals a day: what the API said of the pair. */
class InstanceRulesTest {
    private val thin = Supply(SupplyLevel.THIN, recipes = 2, fewerMeals = 2)
    private val rules = InstanceRules(maxProfiles = 2, catalogueSupply = mapOf("vegan" to mapOf(3 to thin)))

    @Test
    fun `is what the API said of the diet at that number of meals`() {
        assertEquals(thin, rules.supplyOf("vegan", 3))
    }

    @Test
    fun `is nothing for a diet or a number of meals the API said nothing of`() {
        assertNull(rules.supplyOf("keto", 3))
        assertNull(rules.supplyOf("vegan", 6))
    }
}
