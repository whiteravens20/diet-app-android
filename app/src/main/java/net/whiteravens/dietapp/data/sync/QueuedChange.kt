// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A change the application makes without a network as well, which then waits for one before the
 * server has it: what is done in a shop or a kitchen without a signal. A value set is sent again as
 * it is. An addition carries the key ([Addition.key]) it is sent with every time, so that the
 * server writes it once however often it is sent. A change of something the server no longer has
 * ([DoneWhenGone]) was made already.
 */
@Serializable
sealed interface QueuedChange {
    /** An addition, sent with its key every time. */
    sealed interface Addition : QueuedChange {
        val key: String
    }

    /** A change that is made where what it changes is gone: a deletion. */
    sealed interface DoneWhenGone : QueuedChange

    /** A row of a shopping list ticked or not, or what of it is at home or was bought (0 for nothing). */
    @Serializable
    @SerialName("shopping-row")
    data class ShoppingRow(
        val listId: String,
        val itemId: String,
        val checked: Boolean? = null,
        val atHome: Double? = null,
        val have: Double? = null,
    ) : QueuedChange

    /** A meal of a plan marked eaten or not. */
    @Serializable
    @SerialName("eaten")
    data class Eaten(val planId: String, val mealId: String, val eaten: Boolean) : QueuedChange

    /** A weight of a profile, taken at [recordedAt] (an instant as ISO 8601). */
    @Serializable
    @SerialName("weight-logged")
    data class WeightLogged(val profileId: String, val kg: Double, val recordedAt: String, override val key: String) : Addition

    /** A weight of a profile deleted. */
    @Serializable
    @SerialName("weight-deleted")
    data class WeightDeleted(val profileId: String, val id: String) : DoneWhenGone

    /**
     * Some of an ingredient put into a profile's pantry, in [unit], best before [bestBefore]
     * (yyyy-MM-dd) where it has a day; [name] and [category] as the screen shows it until the server
     * has it.
     */
    @Serializable
    @SerialName("pantry-added")
    data class PantryAdded(
        val profileId: String,
        val ingredientId: String,
        val name: String,
        val category: String,
        val quantity: Double,
        val unit: String,
        val bestBefore: String? = null,
        override val key: String,
    ) : Addition

    /** A row of a pantry changed: its amount where [quantity] is given, its day ([bestBefore], or none) where [newDay]. */
    @Serializable
    @SerialName("pantry-changed")
    data class PantryChanged(
        val profileId: String,
        val id: String,
        val quantity: Double? = null,
        val newDay: Boolean = false,
        val bestBefore: String? = null,
    ) : QueuedChange

    /** A row of a pantry taken out. */
    @Serializable
    @SerialName("pantry-removed")
    data class PantryRemoved(val profileId: String, val id: String) : DoneWhenGone

    /**
     * A meal of one's own added to the day of [date] (yyyy-MM-dd) of a plan, beside its meals or in
     * the place of [replaces]: what one serving holds, and how many servings it is.
     */
    @Serializable
    @SerialName("own-meal-added")
    data class OwnMealAdded(
        val planId: String,
        val date: String,
        val name: String,
        val slot: String,
        val calories: Double,
        val protein: Double,
        val fat: Double,
        val carbs: Double,
        val servings: Double,
        val replaces: String? = null,
        override val key: String,
    ) : Addition

    /** A meal taken off its day. */
    @Serializable
    @SerialName("meal-removed")
    data class MealRemoved(val planId: String, val mealId: String) : DoneWhenGone

    /** A recipe marked for a profile (`favorite`, `often`, `avoid`), with its tags: what was said of it before is replaced. */
    @Serializable
    @SerialName("marked")
    data class Marked(val profileId: String, val recipeId: String, val sentiment: String, val tags: List<String>) : QueuedChange

    /** A profile's mark taken off a recipe. */
    @Serializable
    @SerialName("unmarked")
    data class Unmarked(val profileId: String, val recipeId: String) : DoneWhenGone
}
