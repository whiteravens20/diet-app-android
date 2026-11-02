// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A barcode as it is read or typed, read as the API reads it. */
class ProductCodeTest {
    @Test
    fun `the spellings of one code come to one`() {
        assertEquals("5900512320359", productCodeOf("5900512320359"))
        assertEquals("20077242", productCodeOf("20077242"))
        assertEquals("0036000291452", productCodeOf("036000291452"))
        assertEquals("20077242", productCodeOf("0000020077242"))
        assertEquals("5900512320359", productCodeOf("05900512320359"))
        assertEquals("5900512320359", productCodeOf(" 5900512-320359 "))
    }

    @Test
    fun `what cannot be a code is none`() {
        assertNull(productCodeOf("5900512320358"))
        assertNull(productCodeOf("5900521320359"))
        assertNull(productCodeOf("590051232"))
        assertNull(productCodeOf("15900512320359"))
        assertNull(productCodeOf("59005123203a9"))
        assertNull(productCodeOf(""))
    }

    @Test
    fun `every digit of a code is checked`() {
        val code = "5900512320359"
        code.indices.forEach { place ->
            val other = (code[place] - '0' + 1) % 10
            val typed = code.replaceRange(place, place + 1, other.toString())
            assertNull(typed, productCodeOf(typed))
        }
    }

    @Test
    fun `digits are taken for a code, and words for a search`() {
        assertTrue(looksLikeProductCode("590 051-2"))
        assertFalse(looksLikeProductCode("7up"))
        assertFalse(looksLikeProductCode("  "))
    }

    @Test
    fun `a search needs a word its index can look for`() {
        assertTrue(canSearchProducts("mleko"))
        assertTrue(canSearchProducts("a ser"))
        assertFalse(canSearchProducts("ml"))
        assertFalse(canSearchProducts("a b"))
        assertFalse(canSearchProducts("%%%"))
        assertTrue(canSearchProducts("牛乳"))
        assertTrue(canSearchProducts("우유"))
        assertFalse(canSearchProducts("мо"))
    }
}
