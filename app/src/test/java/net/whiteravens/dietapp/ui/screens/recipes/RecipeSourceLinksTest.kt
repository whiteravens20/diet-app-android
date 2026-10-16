// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a recipe is from and its licence: only an address of the web becomes a link, read by its site. */
class RecipeSourceLinksTest {
    @Test
    fun `only an address of the web is a link`() {
        assertEquals("https://recipes.example/soup", webAddress("https://recipes.example/soup"))
        assertEquals("HTTP://recipes.example", webAddress("HTTP://recipes.example"))
        assertNull(webAddress("javascript:alert(1)"))
        assertNull(webAddress("intent://open#Intent;end"))
        assertNull(webAddress("CC BY 4.0"))
        assertNull(webAddress(null))
    }

    @Test
    fun `a link is read by the site it is on, and an address that is none by itself`() {
        assertEquals("recipes.example", siteOf("https://recipes.example/soup?id=1"))
        assertEquals("creativecommons.org", siteOf("https://creativecommons.org/licenses/by/4.0/"))
        assertEquals("not an address", siteOf("not an address"))
    }
}
