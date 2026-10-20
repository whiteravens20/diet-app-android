// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import okhttp3.HttpUrl.Companion.toHttpUrl
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

    @Test
    fun `a page opened signed in is the sign-in page with the code and the page to go on to in the fragment`() {
        assertEquals(
            "https://diet.example/from-the-app#code=c0de&next=%2Fsettings",
            signedInWebPage("https://diet.example/", "c0de", "settings"),
        )
        assertEquals(
            "https://diet.example/from-the-app#code=c0de&next=%2Fmeal-plans%3Fprofile%3Da%2520b%26plan%3Dp%25261",
            signedInWebPage("https://diet.example/", "c0de", "meal-plans", "profile" to "a b", "plan" to "p&1"),
        )
    }

    @Test
    fun `the page to go on to is named from the web application's root, wherever the web application is`() {
        assertEquals(
            "https://example.org/diet/from-the-app#code=c0de&next=%2Fcalculator",
            signedInWebPage("https://example.org/diet/", "c0de", "calculator"),
        )
    }

    @Test
    fun `nothing of the code or the page to go on to is in the path or the query`() {
        val address = signedInWebPage("https://diet.example/", "c0de", "meal-plans", "profile" to "p1").toHttpUrl()

        assertEquals("/from-the-app", address.encodedPath)
        assertNull(address.query)
        assertEquals("code=c0de&next=/meal-plans?profile=p1", address.fragment)
    }
}
