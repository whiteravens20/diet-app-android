// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatNumber
import kotlin.math.roundToInt

/** The name of a limit of a plan that can give way. */
@Composable
internal fun limitName(limit: String): String = when (limit) {
    "variety_floor" -> stringResource(R.string.plans_limit_variety_floor)
    "cook_time" -> stringResource(R.string.plans_limit_cook_time)
    "same_day" -> stringResource(R.string.plans_limit_same_day)
    "consecutive_days" -> stringResource(R.string.plans_limit_consecutive_days)
    "weekly_cap" -> stringResource(R.string.plans_limit_weekly_cap)
    "ingredient_cap" -> stringResource(R.string.plans_limit_ingredient_cap)
    "kind_same_day" -> stringResource(R.string.plans_limit_kind_same_day)
    else -> limit
}

/** Where a limit that may give way is set, as the plan form says it beside the limit: the form, a day, the profile, or nowhere. */
@Composable
internal fun limitWhere(limit: String): String? = when (limit) {
    "variety_floor" -> stringResource(R.string.plans_advanced_limit_where_variety_floor)
    "cook_time" -> stringResource(R.string.plans_advanced_limit_where_cook_time)
    "same_day" -> stringResource(R.string.plans_advanced_limit_where_same_day)
    "consecutive_days" -> stringResource(R.string.plans_advanced_limit_where_consecutive_days)
    "weekly_cap" -> stringResource(R.string.plans_advanced_limit_where_weekly_cap)
    "ingredient_cap" -> stringResource(R.string.plans_advanced_limit_where_ingredient_cap)
    "kind_same_day" -> stringResource(R.string.plans_advanced_limit_where_kind_same_day)
    else -> null
}

/** Limits by name, one after another, as the web application names them. */
@Composable
internal fun limitNames(limits: List<String>): String = limits.map { limitName(it) }.joinToString(", ")

/** What a plan is: `7-day plan · Balanced`. */
@Composable
internal fun planSummary(days: Int, dietType: String): String =
    pluralStringResource(R.plurals.plans_plan_summary, days, days, dietTypeLabel(dietType))

/** When a plan begins, what a day of it holds on average, and how much of its ingredients its recipes share. */
@Composable
internal fun planMeta(startDate: String, calories: Double, reuse: Double): String =
    stringResource(R.string.plans_plan_meta, formatDate(startDate), formatNumber(calories.roundToInt()), formatNumber((reuse * 100).roundToInt()))
