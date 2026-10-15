// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/**
 * What the instance allows an account and what its catalogue holds, as `GET /config` says it: how
 * many profiles an account may have, whether it sends mail, and for each diet, at each number of
 * meals a day, how many recipes the meal it has the fewest for can choose from.
 */
data class InstanceRules(
    val maxProfiles: Int,
    val catalogueDepth: Map<String, Map<Int, Int>>,
    /** Whether the instance sends mail: a confirmation or a link to set a new password needs it. */
    val mailsSent: Boolean = false,
    /** The most a recipe repeats in a plan that names no number, where the instance sets one. */
    val maxRepeatsPerRecipe: Int? = null,
) {
    /**
     * How well the catalogue serves [diet] at [mealCount] meals a day, by the meal it has the fewest
     * recipes for; null where the catalogue's answer does not say.
     */
    fun supplyOf(diet: String, mealCount: Int): Supply? {
        val byCount = catalogueDepth[diet] ?: return null
        val recipes = byCount[mealCount] ?: return null
        val fewerMeals = listOf(4, 3, 2).firstOrNull { it < mealCount && (byCount[it] ?: 0) > 0 }
        val level = when {
            recipes == 0 -> SupplyLevel.NONE
            recipes < VARIETY_DEPTH -> SupplyLevel.THIN
            else -> SupplyLevel.ENOUGH
        }
        return Supply(level, recipes, fewerMeals)
    }

    companion object {
        /** Fewer recipes than this for a meal is too few for a week within the default limit on repeats. */
        const val VARIETY_DEPTH = 3

        /** How many profiles an account may have where the instance does not say: the API's default. */
        const val DEFAULT_MAX_PROFILES = 2
    }
}

/**
 * How well the catalogue serves a diet at a number of meals a day: [recipes] for the meal it has the
 * fewest for, and the largest smaller number of meals a day it can make a plan for ([fewerMeals]).
 */
data class Supply(val level: SupplyLevel, val recipes: Int, val fewerMeals: Int?)

/** `NONE`: a meal has no recipe, so no plan can be made from the catalogue. `THIN`: too few for a week. */
enum class SupplyLevel { NONE, THIN, ENOUGH }
