// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** The weights of a profile as its chart draws them, the way the web application's chart does. */
class WeightSeriesTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")

    private fun at(moment: String, kg: Double) = WeightEntry(moment, kg, Instant.parse(moment))

    @Test
    fun `a day is one point, the last weight of the day, and the days run oldest first`() {
        val series = weightSeries(
            listOf(at("2026-10-16T17:00:00Z", 80.4), at("2026-10-16T05:00:00Z", 81.0), at("2026-10-14T05:00:00Z", 81.2)),
            warsaw,
        )

        assertEquals(listOf(LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-16")), series.map { it.date })
        assertEquals(listOf(81.2, 80.4), series.map { it.kg })
    }

    @Test
    fun `the mean is of the seven days of the calendar that end with the day, not of the last seven weights`() {
        val weekly = weightSeries(
            listOf(at("2026-10-15T05:00:00Z", 80.0), at("2026-10-08T05:00:00Z", 82.0), at("2026-10-01T05:00:00Z", 84.0)),
            warsaw,
        )
        assertEquals(listOf(84.0, 82.0, 80.0), weekly.map { it.mean })

        val daily = weightSeries((1..8).map { day -> at("2026-10-0${day}T05:00:00Z", 80.0 + day) }, warsaw)
        assertEquals(82.0, daily[2].mean, 0.0)
        assertEquals(85.0, daily.last().mean, 0.0)
    }

    @Test
    fun `a weight logged late in the evening belongs to that evening on the phone, not to the day it already is in UTC`() {
        val lateInNewYork = at("2026-10-16T02:30:00Z", 80.0)

        assertEquals(LocalDate.parse("2026-10-15"), weightSeries(listOf(lateInNewYork), ZoneId.of("America/New_York")).single().date)
        assertEquals(LocalDate.parse("2026-10-16"), weightSeries(listOf(lateInNewYork), ZoneOffset.UTC).single().date)
    }

    @Test
    fun `the weights and their means are kept to the hundredth`() {
        val series = weightSeries(
            listOf(at("2026-10-14T05:00:00Z", 80.1), at("2026-10-15T05:00:00Z", 80.2), at("2026-10-16T05:00:00Z", 80.404)),
            ZoneOffset.UTC,
        )

        assertEquals(80.4, series.last().kg, 0.0)
        assertEquals(80.23, series.last().mean, 0.0)
    }
}
