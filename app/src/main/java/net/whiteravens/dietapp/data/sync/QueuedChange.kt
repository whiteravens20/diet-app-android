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
}
