// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The list of plans puts those under way today first, and keeps the rest in the order the API gives them. */
class UnderWayFirstTest {
    private fun plan(id: String, start: String, days: Int) =
        MealPlanSummary(id, start, days, "balanced", Nutrition(2000.0, 100.0, 70.0, 250.0), 0.5, byModel = false)

    @Test
    fun `the plans under way today come first, the rest in the list's order`() {
        // The list gives the plan that begins last first: one to come, one under way, one that is over, one ending today.
        val listed = listOf(
            plan("to-come", "2026-01-20", 5),
            plan("under-way", "2026-01-08", 7),
            plan("over", "2026-01-01", 3),
            plan("ends-today", "2026-01-04", 7),
        )

        assertEquals(
            listOf("under-way", "ends-today", "to-come", "over"),
            underWayFirst(listed, LocalDate.parse("2026-01-10")).map { it.id },
        )
    }

    @Test
    fun `a plan that begins today is under way, and one that begins tomorrow is not`() {
        val listed = listOf(plan("tomorrow", "2026-01-11", 3), plan("today", "2026-01-10", 3))

        assertEquals(listOf("today", "tomorrow"), underWayFirst(listed, LocalDate.parse("2026-01-10")).map { it.id })
    }
}
