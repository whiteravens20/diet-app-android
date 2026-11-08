// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import net.whiteravens.dietapp.ui.components.DOT
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

    /**
     * A number of a text with a plain space before the word or sign after it, which a narrow screen may
     * leave alone at the end of a line: a placeholder of a number (`%d`, `%1$d`) or one written out.
     */
    private val looseCount = Regex("""(%(?:\d+\$)?d|(?<![\p{L}\p{N}.,%$])\d+(?:[.,]\d+)?) (?=[\p{L}%])""")

    @Test
    fun `no number of any language is parted from the word after it`() {
        for (folder in folders) {
            assertEquals(folder, emptyList<String>(), texts(folder).filterValues { looseCount.containsMatchIn(it) }.keys.toList())
        }
    }

    @Test
    fun `finds a number apart from its word, whether a placeholder or written out`() {
        assertTrue(looseCount.containsMatchIn("Cele kaloryczne dla %d profili."))
        assertTrue(looseCount.containsMatchIn("%1\$d z %2\$d dni"))
        assertTrue(looseCount.containsMatchIn("Co najmniej 12 znaków"))
        assertTrue(looseCount.containsMatchIn("1,5 l"))
        assertFalse(looseCount.containsMatchIn("Co najmniej 12\u00A0znaków, %d\u00A0dni, 7 · 8"))
        assertFalse(looseCount.containsMatchIn("Krok2 i %1\$s z %2\$s"))
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

    /** A text as the app shows it: a no-break space written as `\u00A0` read as the space it is. */
    private fun shown(text: String): String = text.replace("\\u00A0", "\u00A0")

    @Test
    fun `every line of macronutrients of any language breaks only after a dot, its opening words aside`() {
        for (folder in folders) {
            val loose = texts(folder)
                .filterKeys { "macros" in it }
                .mapValues { (_, text) -> shown(text) }
                .filterValues { DOT in it && it.substringAfterLast(": ").split(DOT).any { pair -> ' ' in pair } }
                .keys.toList()
            assertEquals(folder, emptyList<String>(), loose)
        }
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

    /** The units and counted words that follow a value put in as text. */
    private val unit = """(?:g|kg|mg|µg|kcal|ml|l|lb|cm|min|h|%|dni|dnia|dzień|days?|sztuk[ai]?|porcj[ie]|porcji|servings?|pieces?)"""

    /** A value put in as text, `%1$s kcal`, with a plain space before its unit: a narrow screen may leave the number at a line's end. */
    private val looseUnit = Regex("""%(?:\d+\$)?s $unit(?=[\s.,;:)!?\u00A0<]|$)""")

    @Test
    fun `no value put in as text in any language is parted from its unit`() {
        for (folder in folders) {
            assertEquals(folder, emptyList<String>(), texts(folder).filterValues { looseUnit.containsMatchIn(shown(it)) }.keys.toList())
        }
    }

    @Test
    fun `finds a value put in as text apart from its unit`() {
        assertTrue(looseUnit.containsMatchIn("Średnio, przy celu %1\$s kcal."))
        assertTrue(looseUnit.containsMatchIn("Na %1\$s porcji"))
        assertFalse(looseUnit.containsMatchIn("Średnio, przy celu %1\$s\u00A0kcal, %2\$s gotuje."))
    }

    /** A figure the code joins to its unit with a plain space: in a string template, or a unit kept with a space before it. */
    private val looseFigure =
        Regex("""\$\{[^{}\n]*\} (?:g|kg|mg|kcal|ml|l|lb|min|%)(?![\p{L}\p{N}])|\$\{[^{}\n]*\} \$\{?(?:unit|kcal)\b|\$\w+ \$\{?(?:unit|kcal)\b|" (?:g|kg|kcal|ml)""" + "\"")

    @Test
    fun `the code holds a figure to its unit`() {
        val loose = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" && looseFigure.containsMatchIn(withoutComments(it.readText())) }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), loose)
    }

    /** A figure a journey writes for itself with a plain space before its unit, where the app holds them. */
    private val looseJourneyFigure = Regex("""\$\{?[\w.()]+\}? (?:kg|kcal|g|ml|lb|min)\b|\$\{?[\w.()]+\}? \$\{text\(R\.string\.unit_""")

    @Test
    fun `no journey waits for a figure parted from its unit`() {
        val loose = File("src/androidTest/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file -> file.readLines().filter { looseJourneyFigure.containsMatchIn(it) && "heldTogether(" !in it }.map { "${file.name}: ${it.trim()}" } }
            .toList()
        assertEquals(emptyList<String>(), loose)
    }

    @Test
    fun `finds a figure the code parts from its unit`() {
        assertTrue(looseFigure.containsMatchIn("\"\${formatNumber(kcal)} kcal\""))
        assertTrue(looseFigure.containsMatchIn("\"\${plainNumber(amount)} \$unit\""))
        assertTrue(looseFigure.containsMatchIn("\"\$number \$unit\""))
        assertTrue(looseFigure.containsMatchIn("val unit = if (grams) \" g\" else \"\""))
        assertFalse(looseFigure.containsMatchIn("\"\${formatNumber(kcal)}\\u00A0kcal\""))
        assertFalse(looseFigure.containsMatchIn("\"\${name} gotuje\""))
    }

    /** A sign that ends a sentence with a plain space before it: a narrow screen may begin a line with it alone. */
    private val looseSign = Regex("""(?<=\S) [≈≥≤](?=[.,;:!?)<]|$)""")

    @Test
    fun `no sign of any language that ends a sentence is parted from the word before it`() {
        for (folder in folders) {
            assertEquals(folder, emptyList<String>(), texts(folder).filterValues { looseSign.containsMatchIn(shown(it)) }.keys.toList())
        }
    }

    @Test
    fun `finds a sign left alone at the end of a sentence`() {
        assertTrue(looseSign.containsMatchIn("Dziennik oszacuje i oznaczy ≈."))
        assertFalse(looseSign.containsMatchIn("Dziennik oznaczy\u00A0≈. Suma ≥ 12 g"))
    }

    /** A Polish word of one letter with a plain space after it: a narrow screen may leave it alone at a line's end. */
    private val looseLetter = Regex("""(?:^|[\s(„"»>\u00A0]|\\n)[wziaouWZIAOU] (?=\S)""")

    @Test
    fun `no Polish word of one letter is parted from the word after it`() {
        assertEquals(emptyList<String>(), texts("values-pl").filterValues { looseLetter.containsMatchIn(shown(it)) }.keys.toList())
    }

    @Test
    fun `finds a Polish word of one letter left alone`() {
        assertTrue(looseLetter.containsMatchIn("Wyłączysz ją w profilu."))
        assertTrue(looseLetter.containsMatchIn("W tym przepisie"))
        assertTrue(looseLetter.containsMatchIn("Koniec.\\nW tym"))
        assertFalse(looseLetter.containsMatchIn("Wyłączysz ją w\u00A0profilu i\u00A0tyle, B 12 g"))
    }
}
