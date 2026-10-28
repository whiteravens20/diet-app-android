// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.IngredientDto
import net.whiteravens.dietapp.data.remote.InventoryItemDto
import net.whiteravens.dietapp.data.remote.PatchInventoryItemRequest
import net.whiteravens.dietapp.data.remote.UpsertInventoryItemRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.PantryChange
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.StockableIngredient
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A profile's pantry: its rows read, some of an ingredient put in, a row changed or taken out. What
 * is put in, changed or taken out goes through the queue of changes, and waits there for a network
 * where there is none: what is put in is sent with a key that has the server put it in once however
 * often it is sent.
 */
@Singleton
class PantryRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
    private val queue: ChangeQueue,
) {
    /** What [profileId]'s pantry holds, in the order the API gives. */
    suspend fun rows(profileId: String): List<PantryRow> = apiCall(json) { api.pantry(profileId) }.map { it.toDomain() }

    /** The catalogue's ingredients whose name holds [search], in the app's language, each with the units the API says the pantry keeps it in. */
    suspend fun ingredients(search: String): List<StockableIngredient> =
        apiCall(json) { api.ingredients(search = search) }.map { it.toStockable() }

    /**
     * Puts [quantity] of [ingredient], in [unit], into [profileId]'s pantry, best before [bestBefore]
     * where one is given: the row it is kept in, or kept to be sent in its turn.
     */
    suspend fun add(profileId: String, ingredient: StockableIngredient, quantity: Double, unit: String, bestBefore: LocalDate?): Submitted<PantryRow> {
        val queued = QueuedChange.PantryAdded(
            profileId,
            ingredient.id,
            ingredient.name,
            ingredient.category,
            quantity,
            unit,
            bestBefore?.toString(),
            UUID.randomUUID().toString(),
        )
        return queue.submit(queued) { send(queued) }
    }

    /** Sends what is put in; answers the row it is kept in. */
    suspend fun send(change: QueuedChange.PantryAdded): PantryRow = apiCall(json) {
        api.addToPantry(change.key, UpsertInventoryItemRequest(change.profileId, change.ingredientId, change.quantity, change.unit, change.bestBefore))
    }.toDomain()

    /** Changes the row [id] of [profileId] as [change] says, and nothing else of it: the row as it is kept now, or kept to be sent in its turn. */
    suspend fun change(profileId: String, id: String, change: PantryChange): Submitted<PantryRow> {
        val queued = QueuedChange.PantryChanged(profileId, id, change.quantity, change.newDay, change.bestBefore?.toString())
        return queue.submit(queued) { send(queued) }
    }

    /** Sends a row's change; answers the row as it is kept now. */
    suspend fun send(change: QueuedChange.PantryChanged): PantryRow {
        val request = PatchInventoryItemRequest(
            quantity = change.quantity,
            bestBefore = if (change.newDay) change.bestBefore?.let { JsonPrimitive(it) } ?: JsonNull else null,
        )
        return apiCall(json) { api.changePantryRow(change.id, request) }.toDomain()
    }

    /** Takes the row [id] out of [profileId]'s pantry, or keeps that to be sent in its turn. */
    suspend fun remove(profileId: String, id: String): Submitted<Unit> {
        val queued = QueuedChange.PantryRemoved(profileId, id)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends a row's taking out. */
    suspend fun send(change: QueuedChange.PantryRemoved) = apiCall(json) { api.removePantryRow(change.id) }
}

private fun InventoryItemDto.toDomain() = PantryRow(
    id = id,
    ingredientId = ingredient.id,
    name = ingredient.name,
    category = ingredient.category,
    shown = display.quantity,
    shownUnit = display.unit,
    bestBefore = bestBefore?.let(LocalDate::parse),
)

private fun IngredientDto.toStockable() = StockableIngredient(id, name, category, pantryUnits, pantryUnits.first(), displayUnit ?: "piece")
