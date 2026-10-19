// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebAppTest {
    @Test
    fun `the web application is the API's base without its last segment, under a path of its own where it has one`() {
        assertEquals("https://diet.example/", webAppAddress("https://diet.example/api/"))
        assertEquals("https://example.org/diet/", webAppAddress("https://example.org/diet/api/"))
        assertEquals("http://10.0.2.2:3000/", webAppAddress("http://10.0.2.2:3000/api/"))
    }

    @Test
    fun `no address is no web application`() {
        assertNull(webAppAddress(""))
        assertNull(webAppAddress("not an address"))
    }

    @Test
    fun `a page is its path under the web application, with what it is asked in its query, encoded`() {
        assertEquals("https://diet.example/settings", webPage("https://diet.example/", "settings"))
        assertEquals("https://example.org/diet/calculator", webPage("https://example.org/diet/", "calculator"))
        assertEquals(
            "https://diet.example/meal-plans?profile=a%20b&plan=p%261",
            webPage("https://diet.example/", "meal-plans", "profile" to "a b", "plan" to "p&1"),
        )
    }
}
