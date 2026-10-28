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

/** The meals there are, in the order of a day: what a list of meals offers to choose from. */
val MEAL_TYPES = listOf("breakfast", "second_breakfast", "lunch", "snack", "dinner")

/**
 * [kinds] of a plan's limits in [order], the order in which the instance says they give way; a kind
 * [order] does not name follows the others, as it came.
 */
fun inGiveWayOrder(order: List<String>, kinds: Collection<String>): List<String> =
    order.filter { it in kinds } + kinds.filter { it !in order }.distinct()

/** A plan that cannot be filled inside its limits: those that would have to give way, in their order, and the meals that would go outside. */
data class RelaxationNeeded(val needs: List<String>, val meals: List<RelaxedMeal>)

/** A meal that would go outside limits of its plan, and which. */
data class RelaxedMeal(val date: LocalDate, val slot: String, val gaveWay: List<String>)

/**
 * A recipe a profile marked: as a favourite (`favorite`), as eaten often (`often`) or to avoid
 * (`avoid`), under [tags] of the profile's own; a serving of it holds [calories] kcal.
 */
data class MarkedRecipe(
    val recipeId: String,
    val title: String,
    val mealTypes: List<String>,
    val sentiment: String,
    val dietTags: List<String>,
    val tags: List<String> = emptyList(),
    val calories: Double = 0.0,
)

/** The diets that are tags of recipes: a plan on one of them takes only recipes that carry it, a plan on any other takes all. */
val RECIPE_DIET_TAGS = setOf("vegetarian", "vegan", "mediterranean", "low_carb", "keto")

/** Whether a recipe with [dietTags] may be planned for somebody on [diet]. */
fun fitsDiet(dietTags: List<String>, diet: String): Boolean = diet !in RECIPE_DIET_TAGS || diet in dietTags

/** A profile's favourite set: the recipe it has for each meal it names. */
data class FavoriteSet(val id: String, val label: String, val slots: Map<String, String>)

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

/**
 * What a [Reroll] would do to a plan as it stands, as the API reads it before anything is drawn: the
 * [days] that get other meals and the meals [replaced] on them, what is [kept] as it is and why, what
 * the days asked for hold that could go as well ([couldGo]), and why the days from today on are no
 * choice of their own ([fromToday]: `not_begun` or `over`), null where they are one.
 */
data class RerollPreview(
    val days: Int,
    val replaced: Int,
    val kept: RerollKept,
    val couldGo: RerollCouldGo,
    val fromToday: String?,
)

/**
 * What a re-roll keeps as it is: the days not asked for ([outside]), those before today ([past]),
 * those the user skips, and on the days filled again the meals eaten, of the user's own ([custom])
 * and locked to their recipe.
 */
data class RerollKept(val outside: Int, val past: Int, val skipped: Int, val eaten: Int, val custom: Int, val locked: Int)

/** Whether the days asked for hold days before today, and on the days filled again meals eaten or of the user's own: what a tick could let go. */
data class RerollCouldGo(val past: Boolean, val eaten: Boolean, val custom: Boolean)
