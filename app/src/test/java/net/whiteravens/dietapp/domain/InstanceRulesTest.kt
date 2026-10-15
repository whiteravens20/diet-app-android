// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How well the catalogue serves a diet at a number of meals a day, as the web application marks it. */
class InstanceRulesTest {
    private val rules = InstanceRules(
        maxProfiles = 2,
        catalogueDepth = mapOf(
            "balanced" to mapOf(2 to 32, 3 to 32, 4 to 30, 5 to 30),
            "vegan" to mapOf(2 to 6, 3 to 2, 4 to 0, 5 to 0),
            "keto" to mapOf(2 to 0, 3 to 0, 4 to 0, 5 to 0),
        ),
    )

    @Test
    fun `a diet with enough recipes for every meal is served`() {
        assertEquals(Supply(SupplyLevel.ENOUGH, recipes = 30, fewerMeals = 3), rules.supplyOf("balanced", 4))
    }

    @Test
    fun `a meal with fewer recipes than a week asks for is thin`() {
        assertEquals(SupplyLevel.THIN, rules.supplyOf("vegan", 3)?.level)
        assertEquals(2, rules.supplyOf("vegan", 3)?.recipes)
    }

    @Test
    fun `a meal with no recipe names the most meals a day the catalogue can plan, or none`() {
        assertEquals(Supply(SupplyLevel.NONE, recipes = 0, fewerMeals = 3), rules.supplyOf("vegan", 5))
        assertEquals(Supply(SupplyLevel.NONE, recipes = 0, fewerMeals = null), rules.supplyOf("keto", 4))
    }

    @Test
    fun `a diet or a number of meals the catalogue has not said nothing of is not marked`() {
        assertNull(rules.supplyOf("mediterranean", 3))
        assertNull(rules.supplyOf("balanced", 6))
    }
}
