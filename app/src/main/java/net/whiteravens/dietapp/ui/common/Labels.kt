package net.whiteravens.dietapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.remote.ApiException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
fun difficultyLabel(code: String): String = when (code) {
    "easy" -> stringResource(R.string.difficulty_easy)
    "medium" -> stringResource(R.string.difficulty_medium)
    "hard" -> stringResource(R.string.difficulty_hard)
    else -> humanise(code)
}

/**
 * Human message for a failed operation. [ApiException] codes the app can
 * actually trigger get proper translations (same wording as the web client);
 * anything else falls back to the server's message, and transport failures
 * to the offline message.
 */
@Composable
fun errorMessage(error: Throwable): String = when (error) {
    is ApiException -> when (error.code) {
        "INVALID_CREDENTIALS" -> stringResource(R.string.error_invalid_credentials)
        "EMAIL_TAKEN" -> stringResource(R.string.error_email_taken)
        "VALIDATION", "VALIDATION_FAILED" -> stringResource(R.string.error_validation)
        "INVALID_REFRESH", "INVALID_ACCESS_TOKEN" -> stringResource(R.string.error_session_expired)
        "NO_ALTERNATIVE" -> stringResource(R.string.error_no_alternative)
        "NO_ELIGIBLE_RECIPE" -> stringResource(R.string.error_no_eligible_recipe)
        "CUSTOM_MEAL_NOT_SWAPPABLE" -> stringResource(R.string.error_custom_meal_not_swappable)
        else -> error.message.ifBlank { stringResource(R.string.error_generic) }
    }
    is IOException -> stringResource(R.string.error_network)
    else -> stringResource(R.string.error_generic)
}

// ── Value formatting ─────────────────────────────────────────────────────────

/** `2026-07-12` → `12 Jul 2026` in the device locale. */
fun formatDate(iso: String): String =
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

/** `2026-07-12` → `Sun, 12 Jul` in the device locale. */
fun formatDayDate(iso: String): String =
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

/** Cache timestamp → `12 Jul, 14:03` for the offline banner. */
fun formatSyncedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))

/** Quantity without noise: 250.0 → `250`, 1.5 stays `1.5`. */
fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.1f", value)
