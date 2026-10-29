// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.theme

import androidx.compose.ui.graphics.toArgb
import net.whiteravens.dietapp.domain.Palette
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The window shows the interface's own background before the interface does, in the light theme and
 * the dark: the default scheme's, since the window is drawn before the scheme chosen is read.
 */
class WindowColoursTest {
    /** The window's colour in the resources of [folder], as Android reads it: opaque. */
    private fun windowColour(folder: String): Int {
        val xml = File("src/main/res/$folder/colors.xml").readText()
        val hex = checkNotNull(Regex("""<color name="window_background">#([0-9A-Fa-f]{6})</color>""").find(xml)) { "$folder has no window_background" }
        return (0xFF000000L or hex.groupValues[1].toLong(16)).toInt()
    }

    @Test
    fun `the window is the colour of the default scheme's background in either theme`() {
        assertEquals(colorSchemeOf(Palette.DEFAULT, dark = false).background.toArgb(), windowColour("values"))
        assertEquals(colorSchemeOf(Palette.DEFAULT, dark = true).background.toArgb(), windowColour("values-night"))
    }
}
