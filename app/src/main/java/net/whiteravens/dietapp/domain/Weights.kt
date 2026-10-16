// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** A weight logged for a profile: [kg] as the API keeps it, taken at [recordedAt]. */
data class WeightEntry(val id: String, val kg: Double, val recordedAt: Instant)

/** A day of the chart of weights: what was logged on [date], and [mean] of the seven days that end with it, both in kilograms. */
data class WeightPoint(val date: LocalDate, val kg: Double, val mean: Double)

/**
 * The weights of a profile as a chart draws them: one point a day, the last entry of the day where there are
 * several, oldest first, each with the mean of the seven days that end with it. The seven days are days of the
 * calendar, not the last seven entries: somebody who steps on the scales once a week has a mean of one entry,
 * not of seven weeks. A day is the phone's own ([zone]): an entry made late in the evening belongs to that
 * evening, not to the day it already is in UTC.
 */
fun weightSeries(entries: List<WeightEntry>, zone: ZoneId): List<WeightPoint> {
    val byDay = entries
        .groupBy { it.recordedAt.atZone(zone).toLocalDate() }
        .mapValues { (_, ofTheDay) -> ofTheDay.maxBy { it.recordedAt }.kg }
        .toSortedMap()
    return byDay.map { (day, kg) ->
        val week = byDay.subMap(day.minusDays(6), day.plusDays(1)).values
        WeightPoint(day, hundredths(kg), hundredths(week.average()))
    }
}

private fun hundredths(kg: Double): Double = (kg * 100).roundToLong() / 100.0
