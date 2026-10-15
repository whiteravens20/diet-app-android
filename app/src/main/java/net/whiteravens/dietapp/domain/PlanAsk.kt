// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import java.time.LocalDate

/**
 * A plan asked for: a profile's days from [startDate], with what the form chose. What is null or
 * empty follows the profile, so that a plan made again follows it as well.
 */
data class PlanAsk(
    val profileId: String,
    val startDate: LocalDate,
    val durationDays: Int,
    val dietType: String? = null,
    val mealCount: Int? = null,
    val calorieTarget: Int? = null,
    /** A dish cooked once for two days running. */
    val mealPrepFriendly: Boolean = false,
    val respectExclusions: Boolean = true,
    val respectFavorites: Boolean = true,
    val respectInventory: Boolean = true,
    val maxRepeatsPerRecipe: Int? = null,
    /** The limits that may give way when no recipe is left inside them, in the order they do. */
    val allowRelaxations: List<String> = emptyList(),
    /** The days that differ from the plan; the others follow it. */
    val days: List<DayAsk> = emptyList(),
)

/** One day of a plan asked for, where it differs from the plan. */
data class DayAsk(
    val date: LocalDate,
    val mealCount: Int? = null,
    /** Left empty, and off the shopping list. */
    val skip: Boolean = false,
    val calorieTarget: Int? = null,
    /** `rest` | `training`; null for a normal day. */
    val dayType: String? = null,
    val cookTimeMinutes: Int? = null,
    /** Recipes using what expires by the day come first. */
    val useUpBy: Boolean = false,
    /** The recipe each of the day's meals is given before the plan is made, by meal. */
    val locks: Map<String, String> = emptyMap(),
)

/** The meals a day has at each number of meals a day, in the order of the day. */
object MealSlots {
    val BY_COUNT: Map<Int, List<String>> = mapOf(
        2 to listOf("breakfast", "dinner"),
        3 to listOf("breakfast", "lunch", "dinner"),
        4 to listOf("breakfast", "lunch", "dinner", "snack"),
        5 to listOf("breakfast", "second_breakfast", "lunch", "snack", "dinner"),
    )
}

/**
 * The limits of a plan that can give way when no recipe is left for a meal inside them, in the
 * order they do: the most a recipe appears in the plan, the day's cooking time, a recipe twice on
 * one day, days in a row, the times a recipe is eaten in seven days, and the most of one
 * ingredient in seven days last.
 */
val PLAN_RELAXATIONS = listOf("variety_floor", "cook_time", "same_day", "consecutive_days", "weekly_cap", "ingredient_cap")

/** A plan that cannot be filled inside its limits: those that would have to give way, in their order, and the meals that would go outside. */
data class RelaxationNeeded(val needs: List<String>, val meals: List<RelaxedMeal>)

/** A meal that would go outside limits of its plan, and which. */
data class RelaxedMeal(val date: LocalDate, val slot: String, val gaveWay: List<String>)

/** A recipe a profile marked (`marked`, `often` or `avoid`), as a form or a swap offers it for a meal. */
data class MarkedRecipe(val recipeId: String, val title: String, val mealTypes: List<String>, val sentiment: String, val dietTags: List<String>)

/** The diets that are tags of recipes: a plan on one of them takes only recipes that carry it, a plan on any other takes all. */
val RECIPE_DIET_TAGS = setOf("vegetarian", "vegan", "mediterranean", "low_carb", "keto")

/** Whether a recipe with [dietTags] may be planned for somebody on [diet]. */
fun fitsDiet(dietTags: List<String>, diet: String): Boolean = diet !in RECIPE_DIET_TAGS || diet in dietTags

/** A profile's favourite set: the recipe it has for each meal it names. */
data class FavoriteSet(val id: String, val label: String, val slots: Map<String, String>)

/**
 * What the list of plans is narrowed to: those [status] names (`active` | `upcoming` | `past`;
 * null for all) and those holding a day from [from] to [to].
 */
data class PlanFilter(val status: String? = null, val from: LocalDate? = null, val to: LocalDate? = null) {
    val isEmpty: Boolean get() = status == null && from == null && to == null
}

/**
 * A day of a profile's plans, for a calendar: the plan made last that holds it ([planId]), how many
 * plans hold it, what it holds and aims at in kcal, whether it is more than a tenth off
 * (`over` | `under`), the meals eaten out of the day's meals, and whether the plan skips it.
 */
data class PlanCalendarDay(
    val date: LocalDate,
    val planId: String,
    val plans: Int,
    val calories: Double,
    val target: Double,
    val offTarget: String?,
    val mealsEaten: Int,
    val meals: Int,
    val skipped: Boolean,
)

/**
 * A plan made again, or a part of it: one day ([dayId]), or the days from [from] to [to] (the whole
 * plan where all three are left out). Days that have passed, meals marked as eaten and the user's
 * own meals stay unless they are let go; a lock whose recipe may no longer go into the plan is
 * dropped where [dropIneligibleLocks]; [allowRelaxations] are the limits that may give way this
 * time, those the plan may already let give way where it is null.
 */
data class Reroll(
    val dayId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val includePast: Boolean = false,
    val replaceEaten: Boolean = false,
    val replaceCustom: Boolean = false,
    val dropIneligibleLocks: Boolean = false,
    val allowRelaxations: List<String>? = null,
)
