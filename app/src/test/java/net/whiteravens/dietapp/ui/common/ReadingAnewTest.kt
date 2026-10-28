// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A screen shown again reads anew at once after another screen of the application, and after the
 * application came back to the front only when its last read began thirty seconds before or more.
 */
class ReadingAnewTest {
    private var now = 1_000_000L
    private val reading = ReadingAnew { now }

    @Test
    fun `after another screen of the application the screen reads at once, however recent its last read`() {
        reading.begun()
        now += 1

        assertTrue(reading.due(cameToTheFront = false))
    }

    @Test
    fun `after the application came back to the front the screen reads only thirty seconds after its last read began`() {
        reading.begun()

        now += ReadingAnew.AT_MOST_EVERY_MILLIS - 1
        assertFalse(reading.due(cameToTheFront = true))

        now += 1
        assertTrue(reading.due(cameToTheFront = true))
    }

    @Test
    fun `the thirty seconds count from the last read that began, whatever asked for it`() {
        reading.begun()
        now += ReadingAnew.AT_MOST_EVERY_MILLIS
        reading.begun()

        now += 1
        assertFalse(reading.due(cameToTheFront = true))
    }

    @Test
    fun `a screen that never read reads when it is shown again`() {
        assertTrue(reading.due(cameToTheFront = true))
    }

    @Test
    fun `the application comes back to the front each time its window starts after it was stopped, and not when it only starts`() {
        // Whatever another test left, the window is started from here on.
        Returns.started()
        val before = Returns.count

        Returns.started()
        assertEquals(before, Returns.count)

        Returns.stopped()
        Returns.started()
        assertEquals(before + 1, Returns.count)

        Returns.started()
        assertEquals(before + 1, Returns.count)
    }
}
