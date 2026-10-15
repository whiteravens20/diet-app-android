// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R

/** The name of a limit of a plan that can give way. */
@Composable
internal fun limitName(limit: String): String = when (limit) {
    "variety_floor" -> stringResource(R.string.plans_limit_variety_floor)
    "cook_time" -> stringResource(R.string.plans_limit_cook_time)
    "same_day" -> stringResource(R.string.plans_limit_same_day)
    "consecutive_days" -> stringResource(R.string.plans_limit_consecutive_days)
    "weekly_cap" -> stringResource(R.string.plans_limit_weekly_cap)
    "ingredient_cap" -> stringResource(R.string.plans_limit_ingredient_cap)
    else -> limit
}

/** Limits by name, one after another, as the web application names them. */
@Composable
internal fun limitNames(limits: List<String>): String = limits.map { limitName(it) }.joinToString(", ")
