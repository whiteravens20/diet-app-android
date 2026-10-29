// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page of the anti-abuse check asks Cloudflare for a widget that fits the dialog. */
class AntiAbuseCheckPageTest {
    @Test
    fun `the widget as wide as its place where that is 300 dp or more, the compact one where it is narrower`() {
        assertEquals(CheckSize.FLEXIBLE, checkSizeFor(300.dp))
        assertEquals(CheckSize.FLEXIBLE, checkSizeFor(331.dp))
        assertEquals(CheckSize.COMPACT, checkSizeFor(299.dp))
        // The place a dialog of the platform's width left on a phone 411 dp wide.
        assertEquals(CheckSize.COMPACT, checkSizeFor(272.dp))
    }

    @Test
    fun `the page asks for the widget of the size chosen, with the site key as a string`() {
        val compact = checkPage("1x00000000000000000000AA", CheckSize.COMPACT)
        assertTrue(compact, compact.contains("""sitekey: "1x00000000000000000000AA","""))
        assertTrue(compact, compact.contains("""size: "compact","""))
        // A key is written into the script as a string, a quote in it escaped.
        val quoted = checkPage("""a"b""", CheckSize.FLEXIBLE)
        assertTrue(quoted, quoted.contains("""sitekey: "a\"b","""))
        assertTrue(quoted, quoted.contains("""size: "flexible","""))
    }
}
