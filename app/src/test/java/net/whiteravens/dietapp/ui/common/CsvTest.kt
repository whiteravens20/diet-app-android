// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** A table written as a file that a spreadsheet opens, in the dialect of the reader's language. */
class CsvTest {
    @Test
    fun `numbers are written to the hundredth without grouping, with the decimal sign of the language, and nothing is written for nothing`() {
        val row = listOf(listOf(1.5, 2.0, 1234.567, 0.004, null, Double.NaN, -0.0, 3))

        assertEquals("\uFEFF1.5,2,1234.57,0,,,0,3\r\n", csvOf(row, CsvDialect.of("en")))
        assertEquals("\uFEFF1,5;2;1234,57;0;;;0;3\r\n", csvOf(row, CsvDialect.of("pl")))
    }

    @Test
    fun `a text is quoted where it holds the separator, a quote or a line's end, or begins with a space, and a text a spreadsheet would work out is kept as text`() {
        val row = listOf(listOf("Soup, thick", "Say \"hi\"", "two\nlines", " padded", "=SUM(A1)", "+48 123", "-5", "@home", "plain"))

        assertEquals(
            "\uFEFF\"Soup, thick\",\"Say \"\"hi\"\"\",\"two\nlines\",\" padded\",'=SUM(A1),'+48 123,'-5,'@home,plain\r\n",
            csvOf(row, CsvDialect.of("en")),
        )
        // A comma parts nothing where a semicolon parts the cells.
        assertEquals("\uFEFFSoup, thick\r\n", csvOf(listOf(listOf("Soup, thick")), CsvDialect.of("pl")))
    }

    @Test
    fun `each row ends as such files end them, after the mark that says the file is UTF-8`() {
        assertEquals("\uFEFFa,b\r\nc,d\r\n", csvOf(listOf(listOf("a", "b"), listOf("c", "d")), CsvDialect.of("en")))
    }

    @Test
    fun `a language the app has no texts in is written as English is`() {
        assertEquals(CsvDialect.of("en"), CsvDialect.of("de"))
    }
}
