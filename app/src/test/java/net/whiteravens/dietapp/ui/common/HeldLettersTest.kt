// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import net.whiteravens.dietapp.ui.components.held
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import java.util.Locale

class HeldLettersTest {
    private val polish = Locale.forLanguageTag("pl-PL")

    /** A Polish word of one letter with a plain space after it, as the test of the app's texts finds one. */
    private val looseLetter = Regex("""(?:^|[\s(„"»\u00A0])[wziaouWZIAOU] (?=\S)""")

    @Test
    fun `holds each Polish word of one letter to the word after it`() {
        assertEquals("Pierś z\u00A0kurczaka", holdLetters("Pierś z kurczaka", polish))
        assertEquals(
            "Kasza jaglana z\u00A0tofu i\u00A0pieczarkami z\u00A0patelni",
            holdLetters("Kasza jaglana z tofu i pieczarkami z patelni", polish),
        )
        assertEquals("Owsianka o\u00A0smaku jabłka a\u00A0nie gruszki", holdLetters("Owsianka o smaku jabłka a nie gruszki", polish))
        assertEquals("Tuńczyk u\u00A0Ani", holdLetters("Tuńczyk u Ani", polish))
    }

    @Test
    fun `holds a word of one letter that begins the text, follows another or an opening sign`() {
        assertEquals("W\u00A0sosie własnym", holdLetters("W sosie własnym", polish))
        assertEquals("Jabłka a\u00A0i\u00A0w\u00A0cieście", holdLetters("Jabłka a i w cieście", polish))
        assertEquals("Ryż (w\u00A0woreczkach)", holdLetters("Ryż (w woreczkach)", polish))
        assertEquals("Pieczarki „z\u00A0lasu”", holdLetters("Pieczarki „z lasu”", polish))
        assertEquals("Tofu i\u00A0Cebula z\u00A0pieca", holdLetters("Tofu i\u00A0Cebula z pieca", polish))
        assertEquals("Koniec.\nW\u00A0tym", holdLetters("Koniec.\nW tym", polish))
    }

    @Test
    fun `leaves no word of one letter loose, as the test of the texts reads one`() {
        for (text in listOf("Pierś z kurczaka", "a i w domu", "Oliwa z oliwek i sól", "(o ile) u nas")) {
            assertFalse(text, looseLetter.containsMatchIn(holdLetters(text, polish)))
        }
    }

    @Test
    fun `changes nothing else`() {
        assertEquals("Witamina A", holdLetters("Witamina A", polish))
        assertEquals("Kasza jaglana", holdLetters("Kasza jaglana", polish))
        assertEquals("Zupa  z\u00A0dyni", holdLetters("Zupa  z dyni", polish))
        assertEquals("Sałatka z ", holdLetters("Sałatka z ", polish))
        assertEquals("", holdLetters("", polish))
    }

    @Test
    fun `leaves a text in another language as it is`() {
        assertEquals("A bowl of oats with a pear", holdLetters("A bowl of oats with a pear", Locale.ENGLISH))
        assertEquals("Pierś z kurczaka", holdLetters("Pierś z kurczaka", Locale.ENGLISH))
    }

    @Test
    fun `holds a text with styles of its parts, every style kept in its place`() {
        val text = buildAnnotatedString {
            append("Więcej: ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Pierś z kurczaka") }
            append(" i reszta")
        }

        val shown = held(text, polish)

        assertEquals("Więcej: Pierś z\u00A0kurczaka i\u00A0reszta", shown.text)
        assertEquals(text.spanStyles.map { it.start to it.end }, shown.spanStyles.map { it.start to it.end })
        assertEquals("Pierś z\u00A0kurczaka", shown.text.substring(shown.spanStyles[0].start, shown.spanStyles[0].end))
    }

    @Test
    fun `leaves a text with a link as it is, and one in another language`() {
        val linked = buildAnnotatedString {
            append("Wyłączysz ją w ")
            withLink(LinkAnnotation.Clickable(tag = "profile", linkInteractionListener = null)) { append("profilu") }
        }
        assertSame(linked, held(linked, polish))
        val english = AnnotatedString("A bowl of oats")
        assertSame(english, held(english, Locale.ENGLISH))
    }

    @Test
    fun `every screen writes through the app's Text, which holds the words of one letter`() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        val ownText = File("src/main/java/net/whiteravens/dietapp/ui/components/Text.kt")

        val material = sources.filter { it != ownText && it.readText().contains(Regex("""^import androidx\.compose\.material3\.(Text|\*)$""", RegexOption.MULTILINE)) }
            .filterNot { it.readText().contains("\nimport net.whiteravens.dietapp.ui.components.Text\n") }
            .map { it.name }
        assertEquals(emptyList<String>(), material)

        // Text drawn without it: only a line that never breaks, a calendar cell's figures and an address.
        val basic = sources.filter { it.readText().contains("BasicText(") }.map { it.name }.sorted()
        assertEquals(listOf("AboutScreen.kt", "PlanCalendar.kt"), basic)
    }
}
