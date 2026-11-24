// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R
import kotlin.math.roundToInt

/**
 * A day's [carbs] against what it aims at, as a line of its macronutrients says it: on a diet whose
 * carbohydrate is a ceiling (low-carb, keto; [floor] not null) against the diet's band, from [floor] up
 * to [ceiling], or below [ceiling] alone where the floor is none; elsewhere against its target [ceiling].
 */
@Composable
internal fun carbsAgainst(carbs: Double, ceiling: Double, floor: Double?): String {
    val grams = formatNumber(carbs.roundToInt())
    val most = formatNumber(ceiling.roundToInt())
    val standing = carbsStanding(carbs, ceiling, floor)
    return when {
        floor == null -> stringResource(R.string.macros_carbs_of, grams, most)
        floor > 0 -> stringResource(
            when (standing) {
                CarbsStanding.OVER -> R.string.macros_carbs_band_over
                CarbsStanding.UNDER -> R.string.macros_carbs_band_under
                CarbsStanding.IN -> R.string.macros_carbs_band
            },
            grams,
            formatNumber(floor.roundToInt()),
            most,
        )
        else -> stringResource(if (standing == CarbsStanding.OVER) R.string.macros_carbs_ceiling_over else R.string.macros_carbs_ceiling, grams, most)
    }
}

/** Where an amount of carbohydrate stands against the band of its diet, in the whole grams it is shown in. */
internal enum class CarbsStanding { OVER, UNDER, IN }

/**
 * Whether [carbs] is over the band's [ceiling], under its [floor] or in it, rounded as they are shown; [IN][CarbsStanding.IN]
 * on a diet without a band ([floor] null), whose target is no band to stand against.
 */
internal fun carbsStanding(carbs: Double, ceiling: Double, floor: Double?): CarbsStanding {
    if (floor == null) return CarbsStanding.IN
    val grams = carbs.roundToInt()
    return when {
        grams > ceiling.roundToInt() -> CarbsStanding.OVER
        grams < floor.roundToInt() -> CarbsStanding.UNDER
        else -> CarbsStanding.IN
    }
}

/** What a day aims at of carbohydrate alone: the diet's band, or its ceiling, on a diet whose carbohydrate is one, else its grams. */
@Composable
internal fun carbsTarget(ceiling: Double, floor: Double?): String {
    val most = formatNumber(ceiling.roundToInt())
    return when {
        floor == null -> stringResource(R.string.macros_carbs_target, most)
        floor > 0 -> stringResource(R.string.macros_carbs_target_band, formatNumber(floor.roundToInt()), most)
        else -> stringResource(R.string.macros_carbs_target_ceiling, most)
    }
}

/**
 * Under a figure of [carbs] in a grid, what it is held against and where it stands: the diet's band, or its ceiling,
 * else the target.
 */
@Composable
internal fun carbsAim(carbs: Double, ceiling: Double, floor: Double?): String {
    val most = formatNumber(ceiling.roundToInt())
    val standing = carbsStanding(carbs, ceiling, floor)
    return when {
        floor == null -> stringResource(R.string.diary_of_target, most)
        floor > 0 -> stringResource(
            when (standing) {
                CarbsStanding.OVER -> R.string.diary_over_band
                CarbsStanding.UNDER -> R.string.diary_under_band
                CarbsStanding.IN -> R.string.diary_in_band
            },
            formatNumber(floor.roundToInt()),
            most,
        )
        else -> stringResource(if (standing == CarbsStanding.OVER) R.string.diary_over_ceiling else R.string.diary_in_band_ceiling, most)
    }
}
