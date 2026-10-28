// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import net.whiteravens.dietapp.domain.DayAsk
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.PlanAsk
import java.time.LocalDate

/** What the form offers and begins with. */
object PlanChoices {
    val MEAL_COUNTS = 2..5
    const val FIRST_DURATION = 7

    /** The kinds of a day: a rest day aims at fewer calories, a training day at more, a skipped day is left empty. */
    val DAY_KINDS = listOf("normal", "rest", "training", "skip")

    /**
     * The most days the form draws a day's options for. It holds no plan to a length, which is the
     * API's to say; it keeps a number typed by mistake from drawing that many days.
     */
    const val MOST_DAYS_DRAWN = 60
}

/** A field of the form, by the path the API names it by when it refuses what it holds. */
enum class PlanField(val path: String) {
    DURATION("durationDays"),
    CALORIE_TARGET("calorieTargetOverride"),
    MAX_REPEATS("maxRepeatsPerRecipe"),
}

/** A field of a day, by the name the API gives it under the day's place among the days sent (`dayOverrides.0.calorieTarget`). */
enum class DayField(val key: String) {
    CALORIE_TARGET("calorieTarget"),
    COOK_TIME("cookTimeBudgetMinutes"),
}

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

    /** The fields that hold what is no whole number; a skipped day shows no field, and none of its fields counts. */
    fun problems(): Set<DayField> = if (skipped) emptySet() else buildSet {
        if (!calorieTarget.emptyOrWhole()) add(DayField.CALORIE_TARGET)
        if (!cookTime.emptyOrWhole()) add(DayField.COOK_TIME)
    }

    /**
     * The recipes the day is given before the plan is made: those of its favourite set for the
     * meals the day has, and the meal locked by hand, which goes before the set's recipe for the
     * same meal. A skipped day has none. [mealSlots] are the meals of a day at each number of
     * meals, as the instance says them.
     */
    fun locks(sets: List<FavoriteSet>, planMealCount: Int, mealSlots: Map<Int, List<String>>): Map<String, String> {
        if (skipped) return emptyMap()
        val meals = mealSlots[mealCount ?: planMealCount].orEmpty()
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
 * Its numbers are whole numbers; what the API takes of them is the API's to say.
 */
data class NewPlanForm(
    val startDate: LocalDate,
    val durationDays: String = PlanChoices.FIRST_DURATION.toString(),
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
    /** The plan's days, as many as the duration says, while that is a number of days the form draws. */
    val dates: List<LocalDate>
        get() {
            val count = durationDays.trim().toIntOrNull()?.takeIf { it in 1..PlanChoices.MOST_DAYS_DRAWN } ?: return emptyList()
            return (0 until count).map { startDate.plusDays(it.toLong()) }
        }

    fun day(date: LocalDate): DayDraft = days[date] ?: DayDraft()

    /** The fields that hold what is no whole number: the duration is needed, the others may be empty. */
    fun problems(): Set<PlanField> = buildSet {
        if (durationDays.trim().toIntOrNull() == null) add(PlanField.DURATION)
        if (!calorieTarget.emptyOrWhole()) add(PlanField.CALORIE_TARGET)
        if (!maxRepeats.emptyOrWhole()) add(PlanField.MAX_REPEATS)
    }

    /** The days of the plan whose fields hold what is no whole number. */
    fun dayProblems(): Map<LocalDate, Set<DayField>> =
        dates.associateWith { day(it).problems() }.filterValues { it.isNotEmpty() }

    /**
     * The plan as the API is asked for it, or null while a field holds no whole number. Only a day that
     * differs from the plan in something is sent. [profileMealCount] is how many meals a day the
     * plan has where the form leaves it to the profile; [sets] are the profile's favourite sets;
     * [mealSlots] the meals of a day at each number of meals, as the instance says them.
     */
    fun ask(profileId: String, profileMealCount: Int, sets: List<FavoriteSet>, mealSlots: Map<Int, List<String>>): PlanAsk? {
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
                locks = draft.locks(sets, planMealCount, mealSlots),
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

/**
 * The fields of a plan the API refused, by its answer to [ask]: the plan's own, and a day's by the
 * day's place among the days [ask] sent. A path the form has no field for is in [elsewhere].
 */
data class PlanRefusals(
    val plan: Map<PlanField, FieldRefusal> = emptyMap(),
    val days: Map<LocalDate, Map<DayField, FieldRefusal>> = emptyMap(),
    val elsewhere: Set<String> = emptySet(),
) {
    /** What is left of them once the form is [now] as it was [before]: a field changed since is no longer refused. */
    fun after(before: NewPlanForm, now: NewPlanForm): PlanRefusals = copy(
        plan = plan.filterKeys { field -> before.valueOf(field) == now.valueOf(field) },
        days = days.mapValues { (date, fields) -> fields.filterKeys { field -> before.day(date).valueOf(field) == now.day(date).valueOf(field) } }
            .filterValues { it.isNotEmpty() },
    )

    companion object {
        private val DAY_PATH = Regex("""dayOverrides\.(\d+)\.(\w+)""")

        fun of(refused: Map<String, FieldRefusal>, ask: PlanAsk): PlanRefusals {
            val plan = PlanField.entries.mapNotNull { field -> refused[field.path]?.let { field to it } }.toMap()
            val days = mutableMapOf<LocalDate, MutableMap<DayField, FieldRefusal>>()
            val elsewhere = mutableSetOf<String>()
            for ((path, refusal) in refused) {
                if (PlanField.entries.any { it.path == path }) continue
                val match = DAY_PATH.matchEntire(path)
                val date = match?.groupValues?.get(1)?.toInt()?.let { ask.days.getOrNull(it)?.date }
                val field = match?.groupValues?.get(2)?.let { name -> DayField.entries.firstOrNull { it.key == name } }
                if (date != null && field != null) days.getOrPut(date) { mutableMapOf() }[field] = refusal else elsewhere += path
            }
            return PlanRefusals(plan, days, elsewhere)
        }
    }
}

private fun NewPlanForm.valueOf(field: PlanField): String = when (field) {
    PlanField.DURATION -> durationDays
    PlanField.CALORIE_TARGET -> calorieTarget
    PlanField.MAX_REPEATS -> maxRepeats
}

private fun DayDraft.valueOf(field: DayField): String = when (field) {
    DayField.CALORIE_TARGET -> calorieTarget
    DayField.COOK_TIME -> cookTime
}

/** Whether the field is empty or holds a whole number. */
private fun String.emptyOrWhole(): Boolean = isBlank() || trim().toIntOrNull() != null
