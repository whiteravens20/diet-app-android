// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The figures of a meal of one's own stand two in a row, as on the web, wherever two fields of the phone's text fit. */
class FigureColumnsTest {
    @Test
    fun `two fields stand side by side on a phone at its usual size of text`() {
        // A window on a phone 411 dp wide, less its margins.
        assertEquals(2, figureColumns(363.dp, 1f))
        assertEquals(2, figureColumns(FIGURE_FIELD_WIDTH * 2 + 8.dp, 1f))
    }

    @Test
    fun `one field a row where the phone's text is large enough for two not to fit`() {
        assertEquals(1, figureColumns(363.dp, 1.3f))
        assertEquals(1, figureColumns(FIGURE_FIELD_WIDTH * 2 + 7.dp, 1f))
    }

    @Test
    fun `two fields a row on a wide screen even at a large size of text`() {
        assertEquals(2, figureColumns(600.dp, 1.3f))
    }
}
