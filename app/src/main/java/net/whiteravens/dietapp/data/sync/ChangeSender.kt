// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.sync

import kotlinx.serialization.SerializationException
import net.whiteravens.dietapp.data.repository.DiaryRepository
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.HouseholdListRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.data.repository.PantryRepository
import net.whiteravens.dietapp.data.repository.ShoppingListRepository
import net.whiteravens.dietapp.data.repository.WaterRepository
import net.whiteravens.dietapp.data.repository.WeightRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a change of the queue as the screen that made it sends it: the same call, and the copies on
 * the phone kept as its answer says.
 */
@Singleton
class ChangeSender @Inject constructor(
    private val lists: ShoppingListRepository,
    private val households: HouseholdListRepository,
    private val plans: MealPlanRepository,
    private val weights: WeightRepository,
    private val water: WaterRepository,
    private val pantry: PantryRepository,
    private val favorites: FavoriteRepository,
    private val notifications: NotificationRepository,
    private val diary: DiaryRepository,
) {
    /**
     * Sends [change]; an answer this version cannot read means the server made the change all the same: the copy it
     * changed is read again (a plan, a list, the household's lists, a diary's day), and the queue takes it as sent.
     */
    suspend fun send(change: QueuedChange) {
        try {
            sendIt(change)
        } catch (unread: SerializationException) {
            runCatching { readAgain(change) }
            throw unread
        }
    }

    private suspend fun readAgain(change: QueuedChange) {
        when (change) {
            is QueuedChange.ShoppingRow -> lists.refresh(change.listId)
            is QueuedChange.HouseholdRow -> households.refresh()
            is QueuedChange.Eaten -> plans.refreshPlan(change.planId)
            is QueuedChange.OwnMealAdded -> plans.refreshPlan(change.planId)
            is QueuedChange.MealRemoved -> plans.refreshPlan(change.planId)
            is QueuedChange.DiaryAdded -> diary.day(change.profileId, LocalDate.parse(change.date))
            // The rest is read again by its screen when it is next shown.
            else -> Unit
        }
    }

    private suspend fun sendIt(change: QueuedChange) {
        when (change) {
            is QueuedChange.ShoppingRow -> lists.send(change)
            is QueuedChange.HouseholdRow -> households.send(change)
            is QueuedChange.Eaten -> plans.send(change)
            is QueuedChange.WeightLogged -> weights.send(change)
            is QueuedChange.WeightDeleted -> weights.send(change)
            is QueuedChange.WaterAdded -> water.send(change)
            is QueuedChange.WaterDeleted -> water.send(change)
            is QueuedChange.PantryAdded -> pantry.send(change)
            is QueuedChange.PantryChanged -> pantry.send(change)
            is QueuedChange.PantryRemoved -> pantry.send(change)
            is QueuedChange.PantryFrozen -> pantry.send(change)
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
