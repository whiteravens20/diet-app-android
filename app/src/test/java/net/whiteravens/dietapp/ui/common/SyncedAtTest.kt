// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** The time an offline screen says it read what it shows: the hour alone today, with the day on another. */
class SyncedAtTest {
    private val before = Locale.getDefault()

    @Before
    fun english() = Locale.setDefault(Locale.UK)

    @After
    fun back() = Locale.setDefault(before)

    private fun at(time: LocalDateTime) = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `a read of today is said by its hour alone`() {
        val day = LocalDate.of(2026, 10, 28)

        assertEquals("14:03", formatSyncedAt(at(day.atTime(14, 3)), today = day))
    }

    @Test
    fun `a read of another day is said with its day`() {
        val day = LocalDate.of(2026, 10, 28)

        assertEquals("27 Oct, 21:40", formatSyncedAt(at(day.minusDays(1).atTime(21, 40)), today = day))
    }
}
