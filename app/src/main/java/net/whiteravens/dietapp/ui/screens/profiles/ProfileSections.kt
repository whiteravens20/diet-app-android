// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.domain.LabelLimit
import net.whiteravens.dietapp.ui.common.activityLevelLabel
import net.whiteravens.dietapp.ui.common.allergenLabel
import net.whiteravens.dietapp.ui.common.dietTypeLabel
import net.whiteravens.dietapp.ui.common.formatList
import net.whiteravens.dietapp.ui.common.formatNumber
import net.whiteravens.dietapp.ui.common.weightLabel
import net.whiteravens.dietapp.ui.components.DOT

/** A part of a profile's form, under its heading; folded, it is one line of what it holds. */
enum class ProfileSection(@StringRes val title: Int) {
    BODY(R.string.profile_section_body),
    GOAL(R.string.profile_section_goal),
    FOOD(R.string.profile_section_food),
    VARIETY(R.string.profile_section_variety),
    LABELS(R.string.profile_label_limits),
}

/** The section [field] stands in. */
internal fun sectionOf(field: ProfileField): ProfileSection = when (field) {
    ProfileField.NAME, ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT -> ProfileSection.BODY
    ProfileField.TARGET_WEIGHT, ProfileField.WATER_TARGET, ProfileField.OWN_TARGET -> ProfileSection.GOAL
    ProfileField.SPLIT, ProfileField.SPLIT_PROTEIN, ProfileField.SPLIT_FAT, ProfileField.SPLIT_CARBS, ProfileField.PER_KG,
    ProfileField.GRAMS_PROTEIN, ProfileField.GRAMS_FAT, ProfileField.GRAMS_CARBS,
    -> ProfileSection.FOOD
    ProfileField.CONSECUTIVE_DAYS, ProfileField.TIMES_PER_WEEK, ProfileField.KIND_PER_DAY, ProfileField.KIND_PER_WEEK,
    ProfileField.PANTRY_COOL_DOWN,
    -> ProfileSection.VARIETY
    ProfileField.LABEL_SALT, ProfileField.LABEL_FIBER, ProfileField.LABEL_SATURATED_FAT, ProfileField.LABEL_SUGARS -> ProfileSection.LABELS
}

/**
 * The sections that need an answer, which open by themselves: a field that holds no number where one is needed or
 * that the API refused, and a kind of protein's limit of either.
 */
internal fun sectionsNeeding(marks: ProfileMarks): Set<ProfileSection> =
    (marks.notNumbers + marks.refusals.fields.keys).map(::sectionOf).toSet() +
        (if (marks.kindNotNumbers.isNotEmpty() || marks.refusals.kinds.isNotEmpty()) setOf(ProfileSection.VARIETY) else emptySet())

/** What [section] of [form] holds, in one line, as the form holds it now; a figure not typed yet is left out. */
@Composable
internal fun sectionLine(section: ProfileSection, form: ProfileForm, unit: WeightUnit): String = when (section) {
    ProfileSection.BODY -> listOfNotNull(
        form.age.trim().toIntOrNull()?.let { pluralStringResource(R.plurals.profiles_age, it, it) },
        decimal(form.height)?.let { "${formatNumber(it, 1)} cm" },
        form.bodyWeightKg(unit)?.let { weightLabel(it, unit.code) },
        activityLevelLabel(form.activityLevel),
    )

    ProfileSection.GOAL -> listOfNotNull(
        paceLabel(form.weeklyLossTarget, unit),
        decimal(form.targetWeight)?.let { stringResource(R.string.profile_summary_target, "${formatNumber(it, 1)} ${unit.code}") },
        form.ownTarget.trim().toIntOrNull()?.let { stringResource(R.string.profile_summary_own_target, formatNumber(it)) },
        form.waterTarget.trim().toIntOrNull()?.takeIf { form.waterTracking }?.let {
            stringResource(R.string.profile_summary_water, formatNumber(it / 1000.0, 1))
        },
    )

    ProfileSection.FOOD -> listOfNotNull(
        dietTypeLabel(form.dietType),
        pluralStringResource(R.plurals.dashboard_figures_meals, form.mealCount, form.mealCount.toString()),
        form.allergens.takeIf { it.isNotEmpty() }?.let { allergens ->
            stringResource(R.string.profile_summary_without, formatList(allergens.sorted().map { allergenLabel(it) }))
        },
    )

    ProfileSection.VARIETY -> listOfNotNull(
        form.limits.consecutiveDays.trim().toIntOrNull()?.let { pluralStringResource(R.plurals.profile_summary_days_in_row, it, it) },
        form.limits.timesPerWeek.trim().toIntOrNull()?.let { pluralStringResource(R.plurals.profile_summary_times_a_week, it, it) },
    )

    ProfileSection.LABELS -> LabelLimit.entries.mapNotNull { limit ->
        val typed = form.labels.getValue(limit)
        decimal(typed.grams)?.takeIf { typed.on }?.let { "${labelLimitName(limit)} ${formatNumber(it, 1)} g" }
    }.ifEmpty { listOf(stringResource(R.string.profile_summary_none)) }
}.joinToString(DOT)

/** A figure typed with a comma or a dot; null where it is none. */
private fun decimal(typed: String): Double? = typed.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
