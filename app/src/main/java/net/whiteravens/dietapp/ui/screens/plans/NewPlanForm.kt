// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.DayAsk
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.MealSlots
import net.whiteravens.dietapp.domain.PlanAsk
import java.time.LocalDate

/** The bounds the API holds a new plan to. */
object PlanBounds {
    val DURATION_DAYS = 1..28
    val CALORIE_TARGET = 800..6000
    val MAX_REPEATS = 1..28
    val COOK_TIME_MINUTES = 0..600
    val MEAL_COUNTS = 2..5
    const val FIRST_DURATION = 7

    /** The kinds of a day: a rest day aims at fewer calories, a training day at more, a skipped day is left empty. */
    val DAY_KINDS = listOf("normal", "rest", "training", "skip")
}

/** A field of the form that holds something the API would refuse. */
enum class PlanField { DURATION, CALORIE_TARGET, MAX_REPEATS }

/** A field of a day that holds something the API would refuse. */
enum class DayField { CALORIE_TARGET, COOK_TIME }

/** One day of the form as it is chosen; a day nobody touched follows the plan. */
data class DayDraft(
    val mealCount: Int? = null,
    val calorieTarget: String = "",
    val kind: String = "normal",
    val cookTime: String = "",
    val useUpBy: Boolean = false,
    /** The favourite set whose recipes fill the meals of the day that it has a recipe for. */
    val setId: String? = null,
    val lockSlot: String? = null,
    val lockRecipeId: String? = null,
) {
    val skipped: Boolean get() = kind == "skip"

    /** A skipped day shows no field, and none of its fields is held against the plan. */
    fun problems(): Set<DayField> = if (skipped) emptySet() else buildSet {
        if (calorieTarget.isNotBlank() && !calorieTarget.within(PlanBounds.CALORIE_TARGET)) add(DayField.CALORIE_TARGET)
        if (cookTime.isNotBlank() && !cookTime.within(PlanBounds.COOK_TIME_MINUTES)) add(DayField.COOK_TIME)
    }

    /**
     * The recipes the day is given before the plan is made: those of its favourite set for the
     * meals the day has, and the meal locked by hand, which goes before the set's recipe for the
     * same meal. A skipped day has none.
     */
    fun locks(sets: List<FavoriteSet>, planMealCount: Int): Map<String, String> {
        if (skipped) return emptyMap()
        val meals = MealSlots.BY_COUNT[mealCount ?: planMealCount].orEmpty()
        val set = sets.find { it.id == setId }
        val locked = LinkedHashMap<String, String>()
        for (meal in meals) set?.slots?.get(meal)?.let { locked[meal] = it }
        if (lockSlot != null && lockRecipeId != null) locked[lockSlot] = lockRecipeId
        return locked
    }
}

/**
 * A new plan's form as it is chosen: its days, what it is made for where that differs from the
 * profile (empty: as the profile has it), the preferences it applies, and each day's own options.
 */
data class NewPlanForm(
    val startDate: LocalDate,
    val durationDays: String = PlanBounds.FIRST_DURATION.toString(),
    val mealPrepFriendly: Boolean = false,
    val dietType: String? = null,
    val mealCount: Int? = null,
    val calorieTarget: String = "",
    val respectExclusions: Boolean = true,
    val respectFavorites: Boolean = true,
    val respectInventory: Boolean = true,
    val maxRepeats: String = "",
    /** The limits that may give way when no recipe is left inside them, in the order they do. */
    val mayGiveWay: List<String> = emptyList(),
    val days: Map<LocalDate, DayDraft> = emptyMap(),
) {
    /** The plan's days, as many as the duration says while it is one the API takes. */
    val dates: List<LocalDate>
        get() {
            val count = durationDays.trim().toIntOrNull()?.takeIf { it in PlanBounds.DURATION_DAYS } ?: return emptyList()
            return (0 until count).map { startDate.plusDays(it.toLong()) }
        }

    fun day(date: LocalDate): DayDraft = days[date] ?: DayDraft()

    fun problems(): Set<PlanField> = buildSet {
        if (!durationDays.within(PlanBounds.DURATION_DAYS)) add(PlanField.DURATION)
        if (calorieTarget.isNotBlank() && !calorieTarget.within(PlanBounds.CALORIE_TARGET)) add(PlanField.CALORIE_TARGET)
        if (maxRepeats.isNotBlank() && !maxRepeats.within(PlanBounds.MAX_REPEATS)) add(PlanField.MAX_REPEATS)
    }

    /** The days of the plan whose fields hold something the API would refuse. */
    fun dayProblems(): Map<LocalDate, Set<DayField>> =
        dates.associateWith { day(it).problems() }.filterValues { it.isNotEmpty() }

    /**
     * The plan as the API is asked for it, or null while a field is refused. Only a day that
     * differs from the plan in something is sent. [profileMealCount] is how many meals a day the
     * plan has where the form leaves it to the profile; [sets] are the profile's favourite sets.
     */
    fun ask(profileId: String, profileMealCount: Int, sets: List<FavoriteSet>): PlanAsk? {
        if (problems().isNotEmpty() || dayProblems().isNotEmpty()) return null
        val planMealCount = mealCount ?: profileMealCount
        val asked = dates.mapNotNull { date ->
            val draft = day(date)
            val day = DayAsk(
                date = date,
                mealCount = draft.mealCount.takeUnless { draft.skipped },
                skip = draft.skipped,
                calorieTarget = draft.calorieTarget.trim().toIntOrNull().takeUnless { draft.skipped },
                dayType = draft.kind.takeIf { it == "rest" || it == "training" },
                // An empty field and a nought both leave the day's cooking time to the plan, as the web's form does.
                cookTimeMinutes = draft.cookTime.trim().toIntOrNull()?.takeIf { it > 0 && !draft.skipped },
                useUpBy = draft.useUpBy && !draft.skipped,
                locks = draft.locks(sets, planMealCount),
            )
            day.takeIf { it != DayAsk(date) }
        }
        return PlanAsk(
            profileId = profileId,
            startDate = startDate,
            durationDays = durationDays.trim().toInt(),
            dietType = dietType,
            mealCount = mealCount,
            calorieTarget = calorieTarget.trim().toIntOrNull(),
            mealPrepFriendly = mealPrepFriendly,
            respectExclusions = respectExclusions,
            respectFavorites = respectFavorites,
            respectInventory = respectInventory,
            maxRepeatsPerRecipe = maxRepeats.trim().toIntOrNull(),
            allowRelaxations = mayGiveWay,
            days = asked,
        )
    }
}

/** Whether the field holds a whole number within [range]. */
private fun String.within(range: IntRange): Boolean = trim().toIntOrNull()?.let { it in range } == true
