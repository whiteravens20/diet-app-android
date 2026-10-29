// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import net.whiteravens.dietapp.domain.Palette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The colour schemes held to WCAG 2.2 level AA in every palette, light and dark, as the web holds its
 * tokens: text 4.5:1 against what it is written on, the edge of a control 3:1 against what surrounds it.
 */
class PalettesTest {
    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    /** Every pair under [least], as `<palette> <mode>: <pair> <ratio>`. */
    private fun short(least: Double, pairs: (ColorScheme) -> List<Triple<String, Color, Color>>): List<String> =
        Palette.entries.flatMap { palette ->
            listOf(false, true).flatMap { dark ->
                pairs(colorSchemeOf(palette, dark))
                    .filter { (_, text, behind) -> contrast(text, behind) < least }
                    .map { (name, text, behind) -> "${palette.wire} ${if (dark) "dark" else "light"}: $name ${"%.2f".format(contrast(text, behind))}" }
            }
        }

    @Test
    fun `the web's tokens come out as the colours the web shows`() {
        // The default scheme's green, dark and light, and the dark page behind it.
        assertEquals(Color(0xFF3BB974), Oklch(0.7, 0.15, 155.0).color)
        assertEquals(Color(0xFF007F3E), Oklch(0.515, 0.15, 155.0).color)
        assertEquals(Color(0xFF0C140F), Oklch(0.18, 0.015, 160.0).color)
    }

    @Test
    fun `text is readable on every surface it is written on`() {
        val surfaces = { s: ColorScheme -> listOf("background" to s.background, "card" to s.surfaceContainer, "muted" to s.surfaceVariant) }
        assertEquals(
            emptyList<String>(),
            short(4.5) { s ->
                surfaces(s).flatMap { (surface, behind) ->
                    listOf(
                        Triple("text on $surface", s.onSurface, behind),
                        Triple("muted text on $surface", s.onSurfaceVariant, behind),
                        Triple("primary on $surface", s.primary, behind),
                        Triple("a note on $surface", s.tertiary, behind),
                        Triple("an error on $surface", s.error, behind),
                    )
                }
            },
        )
    }

    @Test
    fun `the text of a button, a chip and a container is readable on it`() {
        assertEquals(
            emptyList<String>(),
            short(4.5) { s ->
                listOf(
                    Triple("on primary", s.onPrimary, s.primary),
                    Triple("on the primary's container", s.onPrimaryContainer, s.primaryContainer),
                    Triple("on the secondary container", s.onSecondaryContainer, s.secondaryContainer),
                    Triple("on an error", s.onError, s.error),
                    Triple("on an error's container", s.onErrorContainer, s.errorContainer),
                    Triple("a notice on its tint", s.onTertiaryContainer, s.tertiaryContainer),
                )
            },
        )
    }

    @Test
    fun `the edge of a control stands out from what surrounds it`() {
        assertEquals(
            emptyList<String>(),
            short(3.0) { s -> listOf(Triple("edge on background", s.outline, s.background), Triple("edge on card", s.outline, s.surfaceContainer)) },
        )
    }
}
