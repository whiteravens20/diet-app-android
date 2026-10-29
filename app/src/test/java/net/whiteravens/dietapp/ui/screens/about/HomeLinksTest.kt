// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.about

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import net.whiteravens.dietapp.ui.common.ATTRIBUTION
import net.whiteravens.dietapp.ui.common.COPYRIGHT
import net.whiteravens.dietapp.ui.common.HOME_URL
import org.junit.Assert.assertEquals
import org.junit.Test

/** In the legal notices each "White Ravens" leads to White Ravens' home page, and nothing else does. */
class HomeLinksTest {
    private fun linked(text: String): List<String> =
        withHomeLinks(text, TextLinkStyles()) {}.let { said ->
            said.getLinkAnnotations(0, said.length).map { said.text.substring(it.start, it.end) }
        }

    @Test
    fun `the name in the copyright line and in the attribution leads to the home page`() {
        assertEquals(listOf("White Ravens"), linked(COPYRIGHT))
        assertEquals(listOf("White Ravens"), linked("This application is $ATTRIBUTION. Its original source code is at:"))
        val said = withHomeLinks(COPYRIGHT, TextLinkStyles()) {}
        assertEquals(HOME_URL, (said.getLinkAnnotations(0, said.length).single().item as LinkAnnotation.Clickable).tag)
    }

    @Test
    fun `a text without the name has no link, and one that names it twice has two`() {
        assertEquals(emptyList<String>(), linked("It comes with no warranty of any kind."))
        assertEquals(listOf("White Ravens", "White Ravens"), linked("White Ravens and White Ravens"))
    }

    @Test
    fun `following the link opens what it was given`() {
        var opened = 0
        val said = withHomeLinks(COPYRIGHT, TextLinkStyles()) { opened += 1 }
        val link = said.getLinkAnnotations(0, said.length).single().item as LinkAnnotation.Clickable
        link.linkInteractionListener?.onClick(link)
        assertEquals(1, opened)
    }
}
