// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.local.readOrDrop
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.GenerateShoppingListRequest
import net.whiteravens.dietapp.data.remote.ModelApiService
import net.whiteravens.dietapp.data.remote.ShoppingListDto
import net.whiteravens.dietapp.data.remote.UpdateShoppingItemRequest
import net.whiteravens.dietapp.data.remote.WeighRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.ListProposal
import net.whiteravens.dietapp.domain.ListProposals
import net.whiteravens.dietapp.domain.ProposalMeal
import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.domain.ShoppingList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first shopping-list access. Same cache-mirror pattern as
 * [MealPlanRepository]. Item edits call the API directly and mirror the full
 * updated list the server returns; the queue of changes made offline, which
 * docs/sync-strategy.md describes, is not built yet.
 */
@Singleton
class ShoppingListRepository @Inject constructor(
    private val api: DietApiService,
    private val model: ModelApiService,
    private val cache: CacheDao,
    private val json: Json,
) {
    /** The cached copy, or null before the first [refresh]. */
    suspend fun cached(id: String): ShoppingList? = cache.shoppingList(id)?.let { row -> read(row) }

    /** Cached lists for a plan, newest sync first; empty until first fetch. */
    suspend fun cachedForPlan(planId: String): List<ShoppingList> =
        cache.shoppingListsForPlan(planId).mapNotNull { row -> read(row) }

    private suspend fun read(row: CachedShoppingList): ShoppingList? =
        readOrDrop({ json.decodeFromString<ShoppingListDto>(row.json).toDomain(syncedAt = row.syncedAt) }) {
            cache.deleteShoppingList(row.id)
        }

    /** Fetch a plan's lists from the API and mirror them; a list kept of the plan that the API no longer has goes. Throws when offline. */
    suspend fun refreshForPlan(planId: String): List<ShoppingList> {
        val lists = apiCall(json) { api.shoppingLists(planId) }
        val ids = lists.map { it.id }.toSet()
        cache.shoppingListsForPlan(planId).filter { it.id !in ids }.forEach { cache.deleteShoppingList(it.id) }
        lists.forEach { mirror(it) }
        return lists.map { it.toDomain() }
    }

    /**
     * Makes the list of a plan, or of the days from [fromDate] to [toDate] of it (yyyy-MM-dd; the
     * plan's first or last day where null). The lists of the plan that share a day with it are
     * replaced: the copies kept of them go too.
     */
    suspend fun generate(planId: String, fromDate: String? = null, toDate: String? = null): ShoppingList {
        val answer = apiCall(json) { api.generateShoppingList(GenerateShoppingListRequest(planId, fromDate, toDate)) }
        val made = answer.toDomain()
        cachedForPlan(planId).filter { it.id != made.id && it.meets(made.fromDate, made.toDate) }.forEach { cache.deleteShoppingList(it.id) }
        mirror(answer)
        return made
    }

    /** What a list could do without, put in order by a model where [weigh]. Nothing is written; a kind of proposal this version does not know is left out. */
    suspend fun proposals(listId: String, weigh: Boolean = false): ListProposals {
        val answer = apiCall(json) { model.listProposals(listId, WeighRequest(weigh)) }
        return ListProposals(
            proposals = answer.proposals.mapNotNull { proposal ->
                when (proposal.kind) {
                    "merge" -> {
                        val from = proposal.from ?: return@mapNotNull null
                        val to = proposal.to ?: return@mapNotNull null
                        ListProposal.Merge(from.name, to.name, proposal.meals.map { ProposalMeal(it.date, it.mealType, it.title) }, proposal.packsSaved ?: 0, proposal.reason)
                    }
                    "leftover" -> ListProposal.Leftover(
                        name = proposal.name ?: return@mapNotNull null,
                        unit = proposal.unit ?: return@mapNotNull null,
                        left = proposal.left ?: return@mapNotNull null,
                        packSize = proposal.packSize ?: return@mapNotNull null,
                        reason = proposal.reason,
                    )
                    else -> null
                }
            },
            weighed = answer.aiMeta != null && answer.aiMeta.fallbackReason == null,
            fallbackReason = answer.aiMeta?.fallbackReason,
        )
    }

    /** Deletes a list, and the copy kept of it. */
    suspend fun delete(listId: String) {
        apiCall(json) { api.deleteShoppingList(listId) }
        cache.deleteShoppingList(listId)
    }

    /** Fetch from the API and mirror into the cache. Throws when offline. */
    suspend fun refresh(id: String): ShoppingList {
        val list = apiCall(json) { api.shoppingList(id) }
        mirror(list)
        return list.toDomain()
    }

    /**
     * Changes a row: its tick, what is at home, everything the user has for it (cleared where 0).
     * The pantry follows on the server; the list as the server then has it is kept and given back.
     */
    suspend fun change(listId: String, itemId: String, change: RowChange): ShoppingList {
        val request = UpdateShoppingItemRequest(
            purchasedQuantity = change.have?.let { have -> if (have > 0) JsonPrimitive(have) else JsonNull },
            checked = change.checked,
            alreadyHaveQuantity = change.atHome,
        )
        val list = apiCall(json) { api.updateShoppingItem(listId, itemId, request) }
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
