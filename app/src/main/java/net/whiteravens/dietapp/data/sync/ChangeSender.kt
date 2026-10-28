// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.sync

import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ShoppingListRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a change of the queue as the screen that made it sends it: the same call, and the copies on
 * the phone kept as its answer says.
 */
@Singleton
class ChangeSender @Inject constructor(
    private val lists: ShoppingListRepository,
    private val plans: MealPlanRepository,
) {
    suspend fun send(change: QueuedChange) {
        when (change) {
            is QueuedChange.ShoppingRow -> lists.send(change)
            is QueuedChange.Eaten -> plans.send(change)
        }
    }
}
