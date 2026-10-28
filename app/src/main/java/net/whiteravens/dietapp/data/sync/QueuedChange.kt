// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A change the application makes without a network as well, which then waits for one before the
 * server has it: what is done in a shop or a kitchen without a signal. A value set is sent again as
 * it is.
 */
@Serializable
sealed interface QueuedChange {
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
}
