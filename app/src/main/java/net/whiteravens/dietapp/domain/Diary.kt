// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import java.time.LocalDate
import java.time.LocalTime

/** The first and the last day a request may name, as the API has them. */
val FIRST_DIARY_DAY: LocalDate = LocalDate.of(2000, 1, 1)
val LAST_DIARY_DAY: LocalDate = LocalDate.of(2100, 12, 31)

/** The most entries a day of a profile's diary holds, as the API has it. */
const val MOST_DIARY_ENTRIES = 40

/** How many days back a day of the diary still takes a new entry, as the API has it. */
const val DIARY_DAYS_BACK = 366L

/**
 * What the API takes of an entry, so that one written without a network is not refused only once it
 * is sent. A unit test holds them to the contract, but for the most pieces, which it does not name.
 */
object DiaryLimits {
    const val name = 120
    const val leastServings = 0.25
    const val mostServings = 20.0
    const val mostWeighed = 5000.0
    const val mostPieces = 50.0
    const val calories = 5000.0
    const val protein = 500.0
    const val fat = 500.0
    const val carbs = 1000.0
}

/**
 * The meal something eaten at [time] goes under, as the window that adds it opens: by the phone's
 * clock, and changed there where it is another.
 */
fun mealAt(time: LocalTime): String = when {
    time.hour < 10 -> "breakfast"
    time.hour < 12 -> "second_breakfast"
    time.hour < 15 -> "lunch"
    time.hour < 18 -> "snack"
    else -> "dinner"
}

/**
 * A day of a profile's diary: what it aims at ([target], that of the plan's day where [targetFromPlan],
 * else the profile's own), the plan that holds it, what was written for it, and what was eaten, the
 * plan's meals marked as eaten and every entry, as the server worked it out.
 */
data class DiaryDay(
    val profileId: String,
    /** ISO date (yyyy-MM-dd). */
    val date: String,
    val target: Nutrition,
    val targetFromPlan: Boolean,
    val plan: DiaryDayPlan?,
    val entries: List<DiaryEntry>,
    val eaten: Nutrition,
    /** A figure of [eaten] is estimated. */
    val estimated: Boolean,
    /** Epoch millis of the copy this came from; null when fresh off the network. */
    val syncedAt: Long? = null,
)

/** The plan that holds a day: its meals of the day, and where the day stands with the diary counted as eaten. */
data class DiaryDayPlan(
    val id: String,
    val skipped: Boolean,
    val meals: List<DiaryPlanMeal>,
    val calorieDelta: Double,
    /** `over` | `under` when the day is outside a tenth either side of its target; null inside it. */
    val offTarget: String?,
)

/** A meal of the plan that holds a day, as the diary shows it. [calories] is null for one of one's own still on its way. */
data class DiaryPlanMeal(
    val id: String,
    val slot: String,
    val name: String,
    /** The recipe it is of; null for a meal of one's own. */
    val recipeId: String?,
    val eaten: Boolean,
    val calories: Double?,
)

/** An amount of an ingredient an entry holds: as it was given ([quantity] of [unit]), and as it is shown. */
data class DiaryAmount(val quantity: Double, val unit: String, val shown: Double, val shownUnit: String)

/**
 * An entry of a diary, under the meal [slot]. Its figures are those the server worked out when it was
 * written; a change of it that waits to be sent leaves them unknown ([calories], [macros]) until the
 * server has it, and so does an entry on its way, but for what is known of it there.
 */
data class DiaryEntry(
    val id: String,
    val slot: String,
    /** `recipe` | `ingredient` | `meal`. */
    val kind: String,
    val name: String,
    /** Servings of a recipe or of a meal of one's own; null for an ingredient. */
    val servings: Double?,
    val amount: DiaryAmount?,
    val calories: Double?,
    val macros: Macros?,
    /** Its macronutrients were estimated: a meal of one's own written by its energy. */
    val estimated: Boolean,
)

/** What is written for a day of the diary, under the meal [slot]. */
sealed interface NewDiaryEntry {
    val slot: String

    /** A recipe in [servings]; [name] and the [calories] of a serving are what is shown until the server has it. */
    data class OfRecipe(override val slot: String, val recipeId: String, val name: String, val calories: Double, val servings: Double) : NewDiaryEntry

    /** An ingredient in [quantity] of [unit]; [name] and [pieceName], what a piece of it is called, are shown until the server has it. */
    data class OfIngredient(override val slot: String, val ingredientId: String, val name: String, val pieceName: String, val quantity: Double, val unit: String) : NewDiaryEntry

    /** A meal of one's own: what a serving holds, the macronutrients left out estimated by the API, and how many servings it is. */
    data class OwnMeal(
        override val slot: String,
        val name: String,
        val servings: Double,
        val calories: Double,
        val protein: Double?,
        val fat: Double?,
        val carbs: Double?,
    ) : NewDiaryEntry
}

/** An ingredient of the catalogue as an entry takes it: the units the API converts, what a piece of it is called, and what 100 of [canonicalUnit] hold. */
data class DiaryIngredient(
    val id: String,
    val name: String,
    val units: List<String>,
    val pieceName: String,
    val canonicalUnit: String,
    val caloriesPer100: Double?,
)

/** What a copy of a day did: how many entries it wrote, and the day as it is now. */
data class DiaryCopied(val copied: Int, val day: DiaryDay)
