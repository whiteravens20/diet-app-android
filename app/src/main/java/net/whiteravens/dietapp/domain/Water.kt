// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import java.time.Instant
import java.time.LocalDate

/** What the water card's buttons and a reminder of water add, in millilitres: a small glass, a glass and a can. */
val WATER_AMOUNTS_ML = listOf(200, 250, 330)

/** The most one entry of water may hold, in millilitres, as the API takes it. */
const val MAX_WATER_ENTRY_ML = 5000

/** An entry of water: what "Usuń ostatni wpis" names and removes. */
data class WaterEntry(val id: String, val ml: Int, val at: Instant)

/** What one of the days up to a shown one holds. */
data class WaterPoint(val day: LocalDate, val ml: Int)

/**
 * The water of a profile's [day]: what was drunk in all against the profile's [target] (none where it
 * names none), how many entries it holds, its [last] one, and the seven [days] that end with it, the
 * oldest first.
 */
data class WaterDay(
    val day: LocalDate,
    val ml: Int,
    val target: Int?,
    val entries: Int,
    val last: WaterEntry?,
    val days: List<WaterPoint>,
) {
    /** The days of the seven that reached the target: none without one. */
    val reached: List<WaterPoint> get() = target?.let { aim -> days.filter { it.ml >= aim } } ?: emptyList()
}
