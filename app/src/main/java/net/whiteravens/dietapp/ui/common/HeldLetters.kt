// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import java.util.Locale

/** A Polish word of one letter, as a word between two plain spaces is written: after an opening quote or bracket too. */
private val ONE_LETTER = Regex("""^[(„"»]?[wziaouWZIAOU]$""")

/** A space of any kind, the no-break space among them, which Java's `\s` leaves out. */
private val ANY_SPACE = Regex("""[\s\u00A0]""")

/** Whether a language keeps its words of one letter off a line's end: Polish. */
fun holdsLetters(locale: Locale): Boolean = locale.language == "pl"

/**
 * A text as a screen shows it: in Polish every word of one letter ("w", "z", "i", "a", "o", "u") held to the word
 * after it with a no-break space, so that a narrow screen never leaves it alone at a line's end, as the app's own
 * texts are written. Names that come from the data (a recipe's title, an ingredient's name) and the lists the app joins
 * pass through it where they are shown; the data, a form's fields and what is shared keep their plain spaces. Other
 * languages are left as they are. The web holds them the same way.
 */
fun holdLetters(text: String, locale: Locale): String {
    if (!holdsLetters(locale) || ' ' !in text) return text
    val words = text.split(' ')
    return buildString {
        append(words[0])
        for (index in 1 until words.size) {
            // What ends the word before: after a no-break space or a new line the last of it is the word itself.
            val before = words[index - 1].split(ANY_SPACE).last()
            append(if (ONE_LETTER.matches(before) && words[index].isNotEmpty()) '\u00A0' else ' ')
            append(words[index])
        }
    }
}
