// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import java.time.Instant
import java.time.LocalDate

/** A weight logged for a profile: [kg] as the API keeps it, taken at [recordedAt]. */
data class WeightEntry(val id: String, val kg: Double, val recordedAt: Instant)

/**
 * A day of the chart of weights, as the API answers it: what was logged on [date], its last entry where
 * there are several, and [mean] of the seven days of the calendar that end with it, both in kilograms.
 */
data class WeightPoint(val date: LocalDate, val kg: Double, val mean: Double)
