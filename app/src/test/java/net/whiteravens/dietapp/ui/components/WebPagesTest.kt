// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/** A page of the web application that anyone reads goes to the browser as it is: no code is asked for it. */
class WebPagesTest {
    private val shown = mutableListOf<Triple<String, String?, String>>()
    private var asked = 0

    private fun pages(webApp: String?) = WebPages(webApp, CoroutineScope(Dispatchers.Unconfined), { Browser("a.browser", safe = true) }, {
        asked += 1
        "code"
    }) { address, browser, plain -> shown += Triple(address, browser, plain) }

    @Test
    fun `a page anyone reads is opened at its own address, with no code and no browser of its own`() {
        pages("https://diet.example/").openPublic("terms")

        assertEquals(listOf(Triple("https://diet.example/terms", null, "https://diet.example/terms")), shown)
        assertEquals(0, asked)
    }

    @Test
    fun `nothing is opened while no server is chosen`() {
        pages(null).openPublic("terms")

        assertEquals(emptyList<Triple<String, String?, String>>(), shown)
    }
}
