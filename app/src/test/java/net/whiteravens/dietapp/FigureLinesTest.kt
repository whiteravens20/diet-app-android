// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A line of figures joined by dots, `Białko 136 z 134 g · tłuszcz 82 z 71 g`, breaks only after a
 * dot on a narrow screen: a name keeps its figure and unit, and the dot keeps a no-break space before
 * it, so no line starts with a lone unit, figure or dot. A longer phrase may still break between its
 * words. The strings of every folder are read as they stand, and the code is held to the same dot.
 */
class FigureLinesTest {
    private fun Element.children(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    /** Every string and plural form of a folder, by its name (`plans_plan_summary/few` for a form). */
    private fun texts(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/strings.xml")).documentElement
        return root.children("string").associate { it.getAttribute("name") to it.textContent } +
            root.children("plurals").flatMap { plural ->
                plural.children("item").map { "${plural.getAttribute("name")}/${it.getAttribute("quantity")}" to it.textContent }
            }
    }

    private val folders = File("src/main/res").listFiles().orEmpty().filter { File(it, "strings.xml").isFile }.map { it.name }

    /** Where a narrow screen may break a text: its pieces between plain spaces, a held space shown as one. */
    private fun pieces(text: String): List<String> = text.split(' ').map { it.replace('\u00A0', ' ') }

    @Test
    fun `no dot of any language starts a line`() {
        for (folder in folders) {
            assertEquals(folder, emptyList<String>(), texts(folder).filterValues { it.contains(" ·") }.keys.toList())
        }
    }

    @Test
    fun `the average day of a plan breaks only after a dot, while its opening words may break`() {
        val polish = texts("values-pl")
        assertEquals(
            listOf("Dzień", "tego", "planu", "ma", "średnio:", "białko 136 z 134 g ·", "tłuszcz 82 z 71 g ·", "węglowodany 250 z 249 g."),
            pieces(polish.getValue("plans_plan_macros").format("136", "134", "82", "71", "250", "249")),
        )
        assertEquals(
            listOf("Protein 136 of 134 g ·", "fat 82 of 71 g ·", "carbs 250 of 249 g"),
            pieces(texts("values").getValue("plans_day_macros_of").format("136", "134", "82", "71", "250", "249")),
        )
    }

    @Test
    fun `the letters of a diary entry keep their grams, and a counted wording its words`() {
        val polish = texts("values-pl")
        assertEquals(listOf("B 12 g ·", "T 5 g ·", "W 30 g"), pieces(polish.getValue("diary_entry_macros").format("12", "5", "30")))
        assertEquals(listOf("Plan na 7 dni ·", "Zbilansowana"), pieces(polish.getValue("plans_plan_summary/few").format(7, "Zbilansowana")))
    }

    /** A dot with a plain space before it, which a narrow screen may put at the start of a line: `"$day · $meal"`. */
    private val looseDot = Regex("""[^\s] ·""")

    /** The code without its comments, whose examples show a line as the eye reads it. */
    private fun withoutComments(code: String): String =
        code.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(^|[^:])//.*$""", RegexOption.MULTILINE), "$1")

    @Test
    fun `the code joins with the held dot, not with a plain space before it`() {
        val loose = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" && looseDot.containsMatchIn(withoutComments(it.readText())) }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), loose)
    }

    @Test
    fun `finds a plain space before a dot in a template, and none in a comment`() {
        assertTrue(looseDot.containsMatchIn("\"\${day} · \${meal}\""))
        assertTrue(looseDot.containsMatchIn("\"\$name · \${provider.model}\""))
        assertFalse(looseDot.containsMatchIn("\"\${day}\$DOT\${meal}\""))
        assertFalse(looseDot.containsMatchIn(withoutComments("/** `Protein 25 g · Fat 10 g` */\n// a · b\n")))
    }
}
