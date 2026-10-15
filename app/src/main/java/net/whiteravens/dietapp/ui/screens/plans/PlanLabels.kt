// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
    else -> limit
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

/** Where a value goes in a line, to be set apart in it: no text holds it. */
internal const val MARK = "\u0000"

/** [line], made with [MARK] in the place of [value], with [value] set apart in it. */
internal fun emphasized(line: String, value: String): AnnotatedString {
    val (head, tail) = line.split(MARK, limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return buildAnnotatedString {
        append(head)
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value) }
        append(tail)
    }
}

/** Why the engine stood in where a model was asked; null for a reason this version does not know. */
@Composable
internal fun fallbackText(reason: String): String? = when (reason) {
    "quota_exhausted" -> stringResource(R.string.ai_fallback_quota_exhausted)
    "instance_quota_exhausted" -> stringResource(R.string.ai_fallback_instance_quota_exhausted)
    "no_provider" -> stringResource(R.string.ai_fallback_no_provider)
    "all_providers_failed" -> stringResource(R.string.ai_fallback_all_providers_failed)
    "provider_timeout" -> stringResource(R.string.ai_fallback_provider_timeout)
    "key_unreadable" -> stringResource(R.string.ai_fallback_key_unreadable)
    "invalid_output" -> stringResource(R.string.ai_fallback_invalid_output)
    else -> null
}
