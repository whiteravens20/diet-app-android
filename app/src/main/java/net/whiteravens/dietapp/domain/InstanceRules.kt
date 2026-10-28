// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/**
 * What the instance allows an account and what its catalogue holds, as `GET /config` says it: how
 * many profiles an account may have, whether it sends mail, how well the catalogue serves each
 * diet at each number of meals a day; and whom to ask.
 */
data class InstanceRules(
    val maxProfiles: Int,
    /** How well the catalogue serves each diet, by number of meals a day, as the API says it. */
    val catalogueSupply: Map<String, Map<Int, Supply>>,
    /** Whether the instance sends mail: a confirmation or a link to set a new password needs it. */
    val mailsSent: Boolean = false,
    /** The most a recipe repeats in a plan that names no number, where the instance sets one. */
    val maxRepeatsPerRecipe: Int? = null,
    /** How to reach whoever runs the instance, where they published it: to ask for the use of its AI. */
    val operatorContact: String? = null,
    /** Where the instance's users find help, where its operator published a page for it. */
    val supportUrl: String? = null,
) {
    /** How well the catalogue serves [diet] at [mealCount] meals a day; null where the API does not say. */
    fun supplyOf(diet: String, mealCount: Int): Supply? = catalogueSupply[diet]?.get(mealCount)

    companion object {
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
