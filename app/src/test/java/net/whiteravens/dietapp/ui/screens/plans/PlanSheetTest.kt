// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A plan taken out of the app as a sheet for the printer. */
class PlanSheetTest {
    @Test
    fun `the printed sheet writes every text as text, and a skipped day has no table`() {
        val sheet = PlanSheet(
            title = "Meal plan, 13–14 Oct 2026 · Balanced",
            average = "A day on average: 2,000 kcal",
            head = listOf("Meal", "Dish", "Servings", "kcal", "Protein (g)", "Fat (g)", "Carbohydrate (g)"),
            days = listOf(
                SheetDay("Tue, 13 Oct", "1,900 of 2,000 kcal", listOf(SheetMeal("Lunch", "<script>alert(1)</script> & 'soup'", "oats 40 g", listOf("1", "300", "12", "8", "40")))),
                SheetDay("Wed, 14 Oct", "Skipped", emptyList()),
            ),
        )

        val page = sheet.html("en-GB")

        assertTrue(page.startsWith("<!doctype html><html lang=\"en-GB\">"))
        assertFalse("<script>" in page)
        assertTrue("<span class=\"dish\">&lt;script&gt;alert(1)&lt;/script&gt; &amp; &#39;soup&#39;</span>" in page)
        assertEquals(1, Regex("<table>").findAll(page).count())
        assertTrue("<h2><span>Wed, 14 Oct</span><small>Skipped</small></h2></section>" in page)
    }
}
