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
 * What the diary and the weights say of what a profile really uses ([estimate], kcal a day) against
 * the formula's [maintenance], and the [correction] of the target proposed or kept: [state] is
 * `too_little`, `near`, `proposed`, `later` or `kept`.
 */
data class Expenditure(
    val state: String,
    val estimate: Int?,
    val maintenance: Int,
    val correction: Int?,
    val target: Int,
    val targetCorrected: Int?,
) {
    /** A correction is proposed, with what it comes of. */
    val proposed: Boolean get() = state == "proposed" && estimate != null && correction != null
}

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
