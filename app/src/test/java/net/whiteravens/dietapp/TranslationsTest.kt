// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The app's strings in every language of its folders. A language the app ships (the build's
 * `languages`) has every string English has; a folder of another language, a translation still
 * being written, is left out of the package and may lack any of them. No string is empty in any
 * folder: Android shows an empty one as it is, where a missing one falls back to English. Lint holds
 * every folder to English's names, arguments and plural forms.
 */
class TranslationsTest {
    /** The strings of one folder that are translated: each string, and each plural with its forms. */
    private class Strings(val texts: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun Element.children(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    private fun read(file: File): Strings {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val translated = { element: Element -> element.getAttribute("translatable") != "false" }
        return Strings(
            texts = root.children("string").filter(translated).associate { it.getAttribute("name") to it.textContent },
            plurals = root.children("plurals").filter(translated).associate { plural ->
                plural.getAttribute("name") to plural.children("item").associate { it.getAttribute("quantity") to it.textContent }
            },
        )
    }

    private val res = File("src/main/res")
    private val english = read(File(res, "values/strings.xml"))

    /** Every other folder of strings by its language: `values-pl` is `pl`, `values-pt-rBR` is `pt`. */
    private val others: Map<String, Strings> = res.listFiles().orEmpty()
        .filter { it.name.startsWith("values-") && File(it, "strings.xml").isFile }
        .associate { it.name.removePrefix("values-").substringBefore("-r") to read(File(it, "strings.xml")) }

    private val languages = checkNotNull(System.getProperty("app.languages")) { "The build names the languages it ships." }.split(",")

    @Test
    fun `every language the app ships has every string English has`() {
        assertTrue(languages.contains("en"))
        for (language in languages - "en") {
            val own = others[language]
            assertNotNull("values-$language/strings.xml", own)
            assertEquals("strings missing in $language", emptySet<String>(), english.texts.keys - own!!.texts.keys)
            assertEquals("plurals missing in $language", emptySet<String>(), english.plurals.keys - own.plurals.keys)
        }
    }

    @Test
    fun `no string is empty, in any folder`() {
        val empty = (others + ("en" to english)).flatMap { (language, strings) ->
            strings.texts.filterValues { it.isBlank() }.keys.map { "$language: $it" } +
                strings.plurals.flatMap { (name, forms) -> forms.filterValues { it.isBlank() }.keys.map { "$language: $name/$it" } }
        }

        assertEquals(emptyList<String>(), empty)
    }
}
