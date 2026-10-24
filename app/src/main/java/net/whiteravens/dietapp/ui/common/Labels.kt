// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import android.icu.text.DateFormat
import android.icu.text.DateIntervalFormat
import android.icu.text.ListFormatter
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.domain.TargetNote
import java.io.IOException
import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Wire-code → localized-label mapping, mirroring the web's `enums.*` message
 * namespace. Unknown codes (a value added on the platform later) fall back to
 * a humanised form of the code itself, so nothing ever renders blank.
 */

private fun humanise(code: String) =
    code.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
fun dietTypeLabel(code: String): String = when (code) {
    "balanced" -> stringResource(R.string.diet_balanced)
    "high_protein" -> stringResource(R.string.diet_high_protein)
    "low_carb" -> stringResource(R.string.diet_low_carb)
    "vegetarian" -> stringResource(R.string.diet_vegetarian)
    "vegan" -> stringResource(R.string.diet_vegan)
    "keto" -> stringResource(R.string.diet_keto)
    "mediterranean" -> stringResource(R.string.diet_mediterranean)
    "custom" -> stringResource(R.string.diet_custom)
    else -> humanise(code)
}

@Composable
fun mealTypeLabel(code: String): String = when (code) {
    "breakfast" -> stringResource(R.string.meal_breakfast)
    "second_breakfast" -> stringResource(R.string.meal_second_breakfast)
    "lunch" -> stringResource(R.string.meal_lunch)
    "snack" -> stringResource(R.string.meal_snack)
    "dinner" -> stringResource(R.string.meal_dinner)
    else -> humanise(code)
}

@Composable
fun categoryLabel(code: String): String = when (code) {
    "vegetables" -> stringResource(R.string.category_vegetables)
    "fruits" -> stringResource(R.string.category_fruits)
    "dairy" -> stringResource(R.string.category_dairy)
    "meat" -> stringResource(R.string.category_meat)
    "fish" -> stringResource(R.string.category_fish)
    "grains" -> stringResource(R.string.category_grains)
    "legumes" -> stringResource(R.string.category_legumes)
    "nuts_seeds" -> stringResource(R.string.category_nuts_seeds)
    "fats_oils" -> stringResource(R.string.category_fats_oils)
    "spices" -> stringResource(R.string.category_spices)
    "pantry" -> stringResource(R.string.category_pantry)
    "beverages" -> stringResource(R.string.category_beverages)
    "other" -> stringResource(R.string.category_other)
    else -> humanise(code)
}

@Composable
fun activityLevelLabel(code: String): String = when (code) {
    "sedentary" -> stringResource(R.string.activity_sedentary)
    "light" -> stringResource(R.string.activity_light)
    "moderate" -> stringResource(R.string.activity_moderate)
    "active" -> stringResource(R.string.activity_active)
    "very_active" -> stringResource(R.string.activity_very_active)
    else -> humanise(code)
}

@Composable
fun sexLabel(code: String?): String = when (code) {
    "male" -> stringResource(R.string.sex_male)
    "female" -> stringResource(R.string.sex_female)
    null -> stringResource(R.string.profile_prefer_not_to_say)
    else -> humanise(code)
}

@Composable
fun allergenLabel(code: String): String = when (code) {
    "gluten" -> stringResource(R.string.allergen_gluten)
    "dairy" -> stringResource(R.string.allergen_dairy)
    "eggs" -> stringResource(R.string.allergen_eggs)
    "nuts" -> stringResource(R.string.allergen_nuts)
    "peanuts" -> stringResource(R.string.allergen_peanuts)
    "soy" -> stringResource(R.string.allergen_soy)
    "fish" -> stringResource(R.string.allergen_fish)
    "shellfish" -> stringResource(R.string.allergen_shellfish)
    "sesame" -> stringResource(R.string.allergen_sesame)
    else -> humanise(code)
}

@Composable
fun cuisineLabel(code: String): String = when (code) {
    "polish" -> stringResource(R.string.cuisine_polish)
    "italian" -> stringResource(R.string.cuisine_italian)
    "asian" -> stringResource(R.string.cuisine_asian)
    "mediterranean" -> stringResource(R.string.cuisine_mediterranean)
    "middle_eastern" -> stringResource(R.string.cuisine_middle_eastern)
    "mexican" -> stringResource(R.string.cuisine_mexican)
    "american" -> stringResource(R.string.cuisine_american)
    "indian" -> stringResource(R.string.cuisine_indian)
    else -> humanise(code)
}

@Composable
fun reminderCadenceLabel(code: String): String = when (code) {
    "off" -> stringResource(R.string.profile_reminder_off)
    "daily" -> stringResource(R.string.profile_reminder_daily)
    "weekly" -> stringResource(R.string.profile_reminder_weekly)
    else -> humanise(code)
}

@Composable
fun difficultyLabel(code: String): String = when (code) {
    "easy" -> stringResource(R.string.difficulty_easy)
    "medium" -> stringResource(R.string.difficulty_medium)
    "hard" -> stringResource(R.string.difficulty_hard)
    else -> humanise(code)
}

/**
 * What a person is shown for a failure, worded as the web application words it. A request held up
 * says for how long, where the server said; a refused form names the fields it refused; a refusal
 * is said in the API's own words for its code, in the language of the app's texts
 * (contract/errors.json, written into resources by the build); a code this version has no words for
 * is shown as the API put it, which is English but says something; an answer that is not the API's
 * own, such as a proxy's page, is named as that; no answer at all is a connection that failed.
 */
@Composable
fun errorMessage(error: Throwable): String = when (error) {
    is ApiException -> refusalText(error)
    is IOException -> stringResource(R.string.error_network)
    else -> stringResource(R.string.error_generic)
}

@Composable
private fun refusalText(error: ApiException): String {
    val wait = error.retryAfterSeconds?.takeIf { error.statusCode == 429 && it > 0 }
    if (wait != null) {
        if (wait <= 90) return pluralStringResource(R.plurals.error_held_up_seconds, wait, wait)
        val minutes = (wait + 59) / 60
        return pluralStringResource(R.plurals.error_held_up_minutes, minutes, minutes)
    }
    if (error.refusedFields.isNotEmpty()) {
        return stringResource(R.string.error_validation_failed_fields, error.refusedFields.joinToString(", "))
    }
    unfilledMeal(error)?.let { return it }
    ERROR_TEXTS[error.code]?.let { return stringResource(it) }
    if (error.code.startsWith(ApiException.NOT_THE_API)) return stringResource(R.string.error_not_the_api, error.statusCode)
    return error.message.ifBlank { stringResource(R.string.error_generic) }
}

// ── Value formatting ─────────────────────────────────────────────────────────

/** A number in the language of the app's texts, to at most [fractionDigits] decimals: `2,150`, `0,25`. */
@Composable
fun formatNumber(value: Number, fractionDigits: Int = 0): String =
    NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply { maximumFractionDigits = fractionDigits }.format(value)

/** A part of one as the language writes a share of a hundred: `45%`, `45 %`. */
@Composable
fun formatPercent(part: Double): String =
    NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).apply { maximumFractionDigits = 0 }.format(part)

/** A whole number that always says which way it goes: `+120`, `-80`, `+0`. */
@Composable
fun formatSigned(value: Double): String =
    (NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]) as DecimalFormat).apply { positivePrefix = "+" }.format(value.roundToInt())

/** Several things named one after another, as the language joins them: `rice, oats and tofu`. */
@Composable
fun formatList(items: List<String>): String = ListFormatter.getInstance(LocalConfiguration.current.locales[0]).format(items)

/** A weight kept in kilograms, in [unit] (`kg` or `lb`) to at most [fractionDigits] decimals: `64.5 kg`. */
@Composable
fun weightLabel(kg: Double, unit: String, fractionDigits: Int = 1): String =
    "${formatNumber(if (unit == "lb") kg / KG_PER_LB else kg, fractionDigits)} $unit"

/** One pound in kilograms, as the pound is defined. */
private const val KG_PER_LB = 0.45359237

/** `2026-07-12` → `12 Jul 2026` in the device locale. */
fun formatDate(iso: String): String =
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

/** The days from [from] to [to] (yyyy-MM-dd) as one span, the way the language writes one: `15–21 Oct 2026`. */
@Composable
fun formatDates(from: String, to: String): String {
    val format = DateIntervalFormat.getInstance(DateFormat.YEAR_ABBR_MONTH_DAY, LocalConfiguration.current.locales[0])
    format.timeZone = TimeZone.GMT_ZONE
    fun midnight(day: String) = LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    return format.format(DateInterval(midnight(from), midnight(to)), StringBuffer(), FieldPosition(0)).toString()
}

/** `2026-07-12` → `Sun, 12 Jul` in the device locale. */
fun formatDayDate(iso: String): String =
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

/** Cache timestamp → `12 Jul, 14:03` for the offline banner. */
fun formatSyncedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))

/** A moment in the phone's time zone, as the language of the app's texts writes a day and an hour: `17 Oct 2026, 10:42`. */
@Composable
fun formatMoment(at: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(LocalConfiguration.current.locales[0])
        .format(at.atZone(ZoneId.systemDefault()))

/** An amount as the server rounds it and names its unit: `120 g`, `2 slices`, `0.5 pieces`. */
@Composable
fun amountLabel(quantity: Double, unit: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(quantity)
    return when (unit) {
        "g" -> stringResource(R.string.amount_g, number)
        "ml" -> stringResource(R.string.amount_ml, number)
        else -> {
            val (counted, named) = when (unit) {
                "slice" -> R.plurals.amount_slice to R.string.unit_name_slice
                "clove" -> R.plurals.amount_clove to R.string.unit_name_clove
                "handful" -> R.plurals.amount_handful to R.string.unit_name_handful
                else -> R.plurals.amount_piece to R.string.unit_name_piece
            }
            // A part of a piece takes the unit's name; whole ones are counted in the language's own forms.
            if (quantity % 1.0 == 0.0) pluralStringResource(counted, quantity.toInt(), number)
            else "$number ${stringResource(named)}"
        }
    }
}

/** What a unit is called beside a number somebody types: `g`, `ml`, `pieces`, `slices`. */
@Composable
fun unitName(unit: String): String = when (unit) {
    "g", "ml" -> unit
    "slice" -> stringResource(R.string.unit_name_slice)
    "clove" -> stringResource(R.string.unit_name_clove)
    "handful" -> stringResource(R.string.unit_name_handful)
    else -> stringResource(R.string.unit_name_piece)
}

/** What a note of the calorie target says, as the web application's profile summary says it. */
@Composable
fun noteText(note: TargetNote): String = when (note) {
    TargetNote.GoalHeld -> stringResource(R.string.target_note_goal_held)
    TargetNote.Minor -> stringResource(R.string.target_note_minor)
    TargetNote.Underweight -> stringResource(R.string.target_note_underweight)
    is TargetNote.MaintenanceUnderFloor ->
        stringResource(R.string.target_note_maintenance_under_floor, note.floor.roundToInt())
    is TargetNote.SafetyFloorApplied ->
        stringResource(R.string.target_note_safety_floor_applied, note.floor.roundToInt(), note.deficit.roundToInt())
    is TargetNote.DeficitCapped ->
        stringResource(R.string.target_note_deficit_capped, note.requested.roundToInt(), note.deficit.roundToInt())
    is TargetNote.BelowFloor -> stringResource(R.string.target_note_below_floor, note.floor.roundToInt())
    is TargetNote.BelowBmr -> stringResource(R.string.target_note_below_bmr, note.bmr.roundToInt())
}

/**
 * A plan that could not be made names the meal no recipe was left for (`NO_ELIGIBLE_RECIPE` with its
 * `details`), and tells a catalogue without one from a profile that rules out what there is.
 */
@Composable
private fun unfilledMeal(error: ApiException): String? {
    if (error.code != "NO_ELIGIBLE_RECIPE") return null
    val details = error.details ?: return null
    val slot = details["slot"]?.jsonPrimitive?.contentOrNull ?: return null
    val diet = details["dietType"]?.jsonPrimitive?.contentOrNull ?: return null
    val ruledOut = details["ruledOut"]?.jsonPrimitive?.intOrNull ?: return null
    val meal = mealTypeLabel(slot)
    val dietName = dietTypeLabel(diet)
    return when {
        ruledOut > 0 -> pluralStringResource(R.plurals.plans_no_recipe_left, ruledOut, ruledOut, meal, dietName)
        // Fewer meals a day help only with a meal that the shortest day does not have.
        slot !in SHORTEST_DAY -> stringResource(R.string.plans_no_recipe_in_catalogue_fewer, meal, dietName)
        else -> stringResource(R.string.plans_no_recipe_in_catalogue, meal, dietName)
    }
}

/** The meals of a day of two meals, the fewest a plan has. */
private val SHORTEST_DAY = setOf("breakfast", "dinner")

/** Where a value goes in a line, to be set apart in it: no text holds it. */
const val MARK = "\u0000"

/** [line], made with [MARK] in the place of [value], with [value] set apart in it. */
fun emphasized(line: String, value: String): AnnotatedString {
    val (head, tail) = line.split(MARK, limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return buildAnnotatedString {
        append(head)
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value) }
        append(tail)
    }
}

/** Why the engine stood in where a model was asked; null for a reason this version does not know. */
@Composable
fun fallbackText(reason: String): String? = when (reason) {
    "quota_exhausted" -> stringResource(R.string.ai_fallback_quota_exhausted)
    "instance_quota_exhausted" -> stringResource(R.string.ai_fallback_instance_quota_exhausted)
    "new_accounts_quota_exhausted" -> stringResource(R.string.ai_fallback_new_accounts_quota_exhausted)
    "not_permitted" -> stringResource(R.string.ai_fallback_not_permitted)
    "no_provider" -> stringResource(R.string.ai_fallback_no_provider)
    "all_providers_failed" -> stringResource(R.string.ai_fallback_all_providers_failed)
    "provider_timeout" -> stringResource(R.string.ai_fallback_provider_timeout)
    "key_unreadable" -> stringResource(R.string.ai_fallback_key_unreadable)
    "invalid_output" -> stringResource(R.string.ai_fallback_invalid_output)
    else -> null
}
