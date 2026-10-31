// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.sync

import net.whiteravens.dietapp.data.repository.DiaryRepository
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.data.repository.PantryRepository
import net.whiteravens.dietapp.data.repository.ShoppingListRepository
import net.whiteravens.dietapp.data.repository.WeightRepository
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
    private val weights: WeightRepository,
    private val pantry: PantryRepository,
    private val favorites: FavoriteRepository,
    private val notifications: NotificationRepository,
    private val diary: DiaryRepository,
) {
    suspend fun send(change: QueuedChange) {
        when (change) {
            is QueuedChange.ShoppingRow -> lists.send(change)
            is QueuedChange.Eaten -> plans.send(change)
            is QueuedChange.WeightLogged -> weights.send(change)
            is QueuedChange.WeightDeleted -> weights.send(change)
            is QueuedChange.PantryAdded -> pantry.send(change)
            is QueuedChange.PantryChanged -> pantry.send(change)
            is QueuedChange.PantryRemoved -> pantry.send(change)
            is QueuedChange.OwnMealAdded -> plans.send(change)
            is QueuedChange.MealRemoved -> plans.send(change)
            is QueuedChange.Marked -> favorites.send(change)
            is QueuedChange.Unmarked -> favorites.send(change)
            is QueuedChange.NotificationRead -> notifications.send(change)
            is QueuedChange.DiaryAdded -> diary.send(change)
            is QueuedChange.DiaryChanged -> diary.send(change)
            is QueuedChange.DiaryRemoved -> diary.send(change)
        }
    }
}
