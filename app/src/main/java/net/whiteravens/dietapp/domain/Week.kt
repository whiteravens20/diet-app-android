// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import java.time.LocalDate

/** The days of a week in sum. */
const val WEEK_DAYS = 7

/** The water of a week: the [counted] days that hold some, their average, the target a day, and how many reached it. */
data class WeekWater(val counted: Int, val averageMl: Int?, val target: Int?, val reached: Int)

/** Where a profile's weights lead: the pace a week of the fitted line, the weight worked towards, the day it is reached. */
data class WeightTrend(val kgPerWeek: Double?, val targetKg: Double?, val reachesTargetOn: LocalDate?)

/**
 * A profile's week in sum, [from] to [to]: what its [counted] days ate on average against their
 * targets, how many came within a tenth of them, the [empty] days no average counts, what the
 * days drank and where the weight leads.
 */
data class WeekSummary(
    val from: LocalDate,
    val to: LocalDate,
    val counted: Int,
    val empty: Int,
    val eaten: Nutrition?,
    val target: Nutrition?,
    val withinTarget: Int,
    val water: WeekWater,
    val weight: WeightTrend,
)
