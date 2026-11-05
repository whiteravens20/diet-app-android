// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class InOneLineTest {
    @Test
    fun aLineOfPairsBreaksOnlyAfterADot() {
        val line = inOneLine(listOf("Błonnik 0,3 g", "Cukry ≥ 1,4 g", "Sól 2,2 g", "Tłuszcze nasycone 18,9 g"))

        // A line breaks only at a plain space, so the pieces between them are what a narrow screen
        // may move to a line of its own: each a whole pair, with the dot after it.
        assertEquals(
            listOf(
                "Błonnik 0,3 g ·",
                "Cukry ≥ 1,4 g ·",
                "Sól 2,2 g ·",
                "Tłuszcze nasycone 18,9 g",
            ),
            line.split(' '),
        )
    }

    @Test
    fun aSinglePairHasNoDot() {
        assertEquals("Sól —", inOneLine(listOf("Sól —")))
    }
}
