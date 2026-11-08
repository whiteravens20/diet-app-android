// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Buttons side by side take a second line on a narrow phone: three of them are wider than a phone of
 * 360 dp has room for once their words are longer than a few letters, and a `Row` squeezes the last of
 * them where a `FlowRow` moves it under the others. Every card that offers three buttons or more puts
 * them in a `FlowRow`.
 */
class ButtonRowsTest {
    private val button = Regex("""(?<![\w.])(?:Button|TextButton|OutlinedButton|FilledTonalButton|ElevatedButton)\s*\(""")
    private val row = Regex("""(?<![\w.])Row\s*\(""")

    /** The rows of [code] that hold three buttons or more, as `name:line`. */
    private fun crowdedRows(name: String, code: String): List<String> =
        row.findAll(code).mapNotNull { call ->
            val body = blockAfter(code, call.range.last + 1) ?: return@mapNotNull null
            if (button.findAll(body).count() >= 3) "$name:${code.take(call.range.first).count { it == '\n' } + 1}" else null
        }.toList()

    /** The block that follows the parentheses of a call opened just before [from]: its content, or null where none follows. */
    private fun blockAfter(code: String, from: Int): String? {
        var at = from
        var depth = 1
        while (at < code.length && depth > 0) {
            when (code[at]) {
                '(' -> depth++
                ')' -> depth--
            }
            at++
        }
        while (at < code.length && code[at].isWhitespace()) at++
        if (at >= code.length || code[at] != '{') return null
        val start = at
        depth = 0
        do {
            when (code[at]) {
                '{' -> depth++
                '}' -> depth--
            }
            at++
        } while (at < code.length && depth > 0)
        return code.substring(start, at)
    }

    @Test
    fun `no row of the app holds three buttons or more`() {
        val crowded = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { crowdedRows(it.name, it.readText()) }
            .toList()
        assertEquals(emptyList<String>(), crowded)
    }

    @Test
    fun `finds three buttons in a row, and none in a row that flows`() {
        val three = "Button(onClick = a) { Text(\"A\") }\n TextButton(onClick = b) { Text(\"B\") }\n OutlinedButton(onClick = c) { Text(\"C\") }"
        assertEquals(listOf("A.kt:2"), crowdedRows("A.kt", "Column {\nRow(horizontalArrangement = x) {\n $three\n}\n}"))
        assertEquals(emptyList<String>(), crowdedRows("A.kt", "FlowRow(horizontalArrangement = x) {\n $three\n}"))
        assertEquals(emptyList<String>(), crowdedRows("A.kt", "Row(Modifier) {\n Button(onClick = a) { Text(\"A\") }\n TextButton(onClick = b) { Text(\"B\") }\n}"))
    }
}
