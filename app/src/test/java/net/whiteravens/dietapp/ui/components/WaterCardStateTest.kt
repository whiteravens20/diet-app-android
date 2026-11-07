// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Waiting
import net.whiteravens.dietapp.domain.WaterDay
import net.whiteravens.dietapp.domain.WaterEntry
import net.whiteravens.dietapp.domain.WaterPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** What the water card shows of a day with additions and removals that wait to be sent over it. */
class WaterCardStateTest {
    private val profileId = "5c52d79a-1aa4-4614-8005-ff8210b3bc3b"
    private val today = LocalDate.parse("2026-11-07")
    private val earlier = WaterEntry("0f8c2a4e-5b1d-4c3e-9a7f-2d6e8b1c4a90", 250, Instant.parse("2026-11-07T13:05:00Z"))
    private val read = WaterDay(
        day = today,
        ml = 600,
        target = 2000,
        entries = 2,
        last = earlier,
        days = (6 downTo 0).map { WaterPoint(today.minusDays(it.toLong()), if (it == 0) 600 else 2100) },
    )
    private val madeAt = Instant.parse("2026-11-07T16:20:00Z")
    private fun waiting(id: Long, change: QueuedChange) = Waiting(id, change, madeAt.toEpochMilli())
    private val added = QueuedChange.WaterAdded(profileId, today.toString(), 330, "key-330")

    @Test
    fun `an addition that waits is in the day's sum, its entries and its bar, and is its last entry`() {
        val state = WaterViewModel.UiState(profileId = profileId, day = today, read = read, loading = false, waiting = listOf(waiting(1, added)))

        val shown = state.shown!!
        assertEquals(930, shown.ml)
        assertEquals(3, shown.entries)
        assertEquals(WaterPoint(today, 930), shown.days.last())
        assertEquals(WaterEntry("key-330", 330, madeAt), state.last)
        assertTrue(state.lastWaits)
    }

    @Test
    fun `a removal that waits leaves the entry out of the sum, and no last entry to name until the server says the next`() {
        val removed = QueuedChange.WaterDeleted(profileId, earlier.id, today.toString(), earlier.ml)
        val state = WaterViewModel.UiState(profileId = profileId, day = today, read = read, loading = false, waiting = listOf(waiting(1, removed)))

        assertEquals(350, state.shown!!.ml)
        assertEquals(1, state.shown!!.entries)
        assertNull(state.last)
        assertFalse(state.lastWaits)
    }

    @Test
    fun `a day never read shows only what waits to be added, and can take it back`() {
        val twice = listOf(waiting(1, added), waiting(2, added.copy(ml = 200, key = "key-200")))
        val state = WaterViewModel.UiState(profileId = profileId, day = today, read = null, loading = false, error = java.io.IOException("offline"), waiting = twice)

        assertNull(state.shown)
        assertEquals(530, state.waitingOnly!!.ml)
        assertNull(state.waitingOnly!!.target)
        assertEquals("key-200", state.last!!.id)
        assertTrue(state.lastWaits)
        assertTrue(state.ready)
    }

    @Test
    fun `nothing is added before the first reading is over, nor while a change is on its way`() {
        assertFalse(WaterViewModel.UiState(profileId = profileId, day = today, loading = true).ready)
        assertFalse(WaterViewModel.UiState(profileId = profileId, day = today, read = read, loading = false, busy = true).ready)
        assertTrue(WaterViewModel.UiState(profileId = profileId, day = today, read = read, loading = false).ready)
    }
}
