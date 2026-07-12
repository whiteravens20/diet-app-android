package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.GenerateShoppingListRequest
import net.whiteravens.dietapp.data.remote.ShoppingListDto
import net.whiteravens.dietapp.data.remote.UpdateShoppingItemRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.ShoppingList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first shopping-list access. Same cache-mirror pattern as
 * [MealPlanRepository]. Item edits call the API directly and mirror the full
 * updated list the server returns; the offline outbox/replay queue from
 * docs/sync-strategy.md is Phase 3+ and not built yet.
 */
@Singleton
class ShoppingListRepository @Inject constructor(
    private val api: DietApiService,
    private val cache: CacheDao,
    private val json: Json,
) {
    /** The cached copy, or null before the first [refresh]. */
    suspend fun cached(id: String): ShoppingList? =
        cache.shoppingList(id)?.let { row ->
            json.decodeFromString<ShoppingListDto>(row.json).toDomain(syncedAt = row.syncedAt)
        }

    /** Cached lists for a plan, newest sync first; empty until first fetch. */
    suspend fun cachedForPlan(planId: String): List<ShoppingList> =
        cache.shoppingListsForPlan(planId).map { row ->
            json.decodeFromString<ShoppingListDto>(row.json).toDomain(syncedAt = row.syncedAt)
        }

    /** Fetch a plan's lists from the API and mirror them. Throws when offline. */
    suspend fun refreshForPlan(planId: String): List<ShoppingList> {
        val lists = apiCall(json) { api.shoppingLists(planId) }
        lists.forEach { mirror(it) }
        return lists.map { it.toDomain() }
    }

    /** Generate a list from a plan (backend operation; may consume pantry stock). */
    suspend fun generate(planId: String): ShoppingList {
        val list = apiCall(json) { api.generateShoppingList(GenerateShoppingListRequest(planId)) }
        mirror(list)
        return list.toDomain()
    }

    /** Fetch from the API and mirror into the cache. Throws when offline. */
    suspend fun refresh(id: String): ShoppingList {
        val list = apiCall(json) { api.shoppingList(id) }
        mirror(list)
        return list.toDomain()
    }

    /** Tick / untick an item. Server-authoritative; returns the updated list. */
    suspend fun setChecked(listId: String, itemId: String, checked: Boolean): ShoppingList {
        val list = apiCall(json) {
            api.updateShoppingItem(listId, itemId, UpdateShoppingItemRequest(checked = checked))
        }
        mirror(list)
        return list.toDomain()
    }

    /** Record how much of an item was obtained; >= total auto-checks server-side. */
    suspend fun setPurchasedQuantity(listId: String, itemId: String, quantity: Double): ShoppingList {
        val list = apiCall(json) {
            api.updateShoppingItem(listId, itemId, UpdateShoppingItemRequest(purchasedQuantity = quantity))
        }
        mirror(list)
        return list.toDomain()
    }

    private suspend fun mirror(list: ShoppingListDto) {
        cache.upsertShoppingList(
            CachedShoppingList(
                id = list.id,
                planId = list.planId,
                json = json.encodeToString(list),
                syncedAt = System.currentTimeMillis(),
            ),
        )
    }
}
