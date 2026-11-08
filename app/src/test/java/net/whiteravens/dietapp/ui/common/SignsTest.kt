// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/** A negative number is written with the minus sign, as print sets it, in every format and in no code by hand. */
class SignsTest {
    private val polish = Locale.forLanguageTag("pl-PL")
    private val english = Locale.forLanguageTag("en-US")

    @Test
    fun `every format writes a negative number with the minus sign`() {
        assertEquals("\u22125", numberIn(english, -5))
        assertEquals("\u22121,25", numberIn(polish, -1.25, 2))
        assertEquals("2150", numberIn(polish, 2150))
        assertEquals("\u221280", signedIn(polish, -80.0))
        assertEquals("+120", signedIn(english, 120.0))
        assertEquals("+0", signedIn(english, 0.0))
        assertEquals("\u221210%", percentIn(english, -0.1))
    }

    /** A sign written by hand with a hyphen before a number, where the formats give the minus sign. */
    private val hyphenSign = Regex("\"-\\$|\"-\" *\\+|\\? *\"-\"|else \"-\"|\\) \"-\"")

    @Test
    fun `no code writes the sign of a number with a hyphen`() {
        val loose = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" && hyphenSign.containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), loose)
    }

    @Test
    fun `finds a sign written by hand with a hyphen`() {
        assertTrue(hyphenSign.containsMatchIn("val sign = if (diff < 0) \"-\" else \"+\""))
        assertTrue(hyphenSign.containsMatchIn("\"-\${formatNumber(abs(diff))}\""))
        assertTrue(hyphenSign.containsMatchIn("\"-\" + formatNumber(abs(diff))"))
        assertFalse(hyphenSign.containsMatchIn("val sign = if (pace > 0) \"+\" else if (pace < 0) \"\\u2212\" else \"\""))
        assertFalse(hyphenSign.containsMatchIn("\"${'$'}{from}-${'$'}{to}\""))
    }
}
