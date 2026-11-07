// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedHouseholdList
import net.whiteravens.dietapp.data.local.HouseholdListDao
import net.whiteravens.dietapp.data.local.readOrDrop
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.GenerateHouseholdListRequest
import net.whiteravens.dietapp.data.remote.HouseholdListDto
import net.whiteravens.dietapp.data.remote.ShoppingListDto
import net.whiteravens.dietapp.data.remote.UpdateShoppingItemRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.HouseholdList
import net.whiteravens.dietapp.domain.RowChange
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The account's household lists, kept on the phone as its plans' lists are: read and mirrored, made,
 * deleted, and a row changed through the [ChangeQueue], kept to be sent in its turn without a
 * network. Making or deleting one changes the plans' own lists of its days on the server: the copies
 * kept of those go, to be read again.
 */
@Singleton
class HouseholdListRepository @Inject constructor(
    private val api: DietApiService,
    private val kept: HouseholdListDao,
    private val lists: CacheDao,
    private val json: Json,
    private val queue: ChangeQueue,
) {
    /** The household lists as they were last read, the newest first; empty before the first [refresh]. */
    suspend fun cached(): List<HouseholdList> = kept.all().mapNotNull { row ->
        readOrDrop({ json.decodeFromString<HouseholdListDto>(row.json).toDomain(syncedAt = row.syncedAt) }) { kept.delete(row.id) }
    }

    /** The account's household lists from the API, kept as it has them: one the API no longer has goes. Throws when offline. */
    suspend fun refresh(): List<HouseholdList> {
        val read = apiCall(json) { api.householdLists() }
        val ids = read.map { it.id }.toSet()
        kept.all().filter { it.id !in ids }.forEach { kept.delete(it.id) }
        read.forEach { mirror(it) }
        return read.map { it.toDomain() }
    }

    /**
     * Makes a household list of [planIds], one plan of each profile, for the days from [fromDate] to
     * [toDate]; one made of the same plans again, as "Update the list" does, replaces the old. The
     * household lists are read again after it: one that lost its parts to it is gone on the server.
     */
    suspend fun make(planIds: List<String>, fromDate: String, toDate: String): HouseholdList {
        val made = apiCall(json) { api.makeHouseholdList(GenerateHouseholdListRequest(planIds, fromDate, toDate)) }
        // The plans' lists of these days gave way to the parts: their copies go, to be read again.
        planIds.forEach { planId ->
            lists.shoppingListsForPlan(planId)
                .filter { row -> runCatching { json.decodeFromString<ShoppingListDto>(row.json) }.getOrNull()?.let { it.fromDate <= toDate && fromDate <= it.toDate } ?: true }
                .forEach { lists.deleteShoppingList(it.id) }
        }
        mirror(made)
        try {
            refresh()
        } catch (e: IOException) {
            // Read again the next time the lists are shown.
        } catch (e: ApiException) {
            // The same.
        }
        return made.toDomain()
    }

    /** Deletes [list] with its parts: the copies kept of both go. */
    suspend fun delete(list: HouseholdList) {
        try {
            apiCall(json) { api.deleteHouseholdList(list.id) }
        } catch (e: ApiException) {
            if (e.code != LIST_NOT_FOUND) throw e
        }
        kept.delete(list.id)
        list.parts.forEach { lists.deleteShoppingList(it.listId) }
    }

    /** Changes a row of [householdId] as a row of a plan's list is changed, or keeps that to be sent in its turn. */
    suspend fun change(householdId: String, itemId: String, change: RowChange): Submitted<HouseholdList> {
        val queued = QueuedChange.HouseholdRow(householdId, itemId, change.checked, change.atHome, change.have)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends a row's change; the list as the server then has it is kept and given back. */
    suspend fun send(change: QueuedChange.HouseholdRow): HouseholdList {
        val request = UpdateShoppingItemRequest(
            purchasedQuantity = change.have?.let { have -> if (have > 0) JsonPrimitive(have) else JsonNull },
            checked = change.checked,
            alreadyHaveQuantity = change.atHome,
        )
        val list = apiCall(json) { api.updateHouseholdItem(change.householdId, change.itemId, request) }
        mirror(list)
        return list.toDomain()
    }

    private suspend fun mirror(list: HouseholdListDto) {
        kept.upsert(CachedHouseholdList(id = list.id, json = json.encodeToString(list), syncedAt = System.currentTimeMillis()))
    }

    private companion object {
        /** The household list asked for is not there, or is not the account's. */
        const val LIST_NOT_FOUND = "HOUSEHOLD_LIST_NOT_FOUND"
    }
}
