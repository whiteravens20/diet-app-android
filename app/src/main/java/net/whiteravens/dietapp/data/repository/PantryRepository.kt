// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.DisplayAmountDto
import net.whiteravens.dietapp.data.remote.EatenFromPantryDto
import net.whiteravens.dietapp.data.remote.EatenPantryDto
import net.whiteravens.dietapp.data.remote.FreezePantryRowRequest
import net.whiteravens.dietapp.data.remote.IngredientDto
import net.whiteravens.dietapp.data.remote.InventoryItemDto
import net.whiteravens.dietapp.data.remote.PatchInventoryItemRequest
import net.whiteravens.dietapp.data.remote.StillGoodRequest
import net.whiteravens.dietapp.data.remote.UpsertInventoryItemRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.Borrowed
import net.whiteravens.dietapp.domain.EatenFood
import net.whiteravens.dietapp.domain.EatenOf
import net.whiteravens.dietapp.domain.EatenPantry
import net.whiteravens.dietapp.domain.PantryChange
import net.whiteravens.dietapp.domain.PantryPortion
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.ShownAmount
import net.whiteravens.dietapp.domain.StockableIngredient
import net.whiteravens.dietapp.domain.frozenUntil
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
     * where one is given, into the freezer where [frozen], split into [portions] packages there: the row
     * it is kept in (the first package), or kept to be sent in its turn.
     */
    suspend fun add(
        profileId: String,
        ingredient: StockableIngredient,
        quantity: Double,
        unit: String,
        bestBefore: LocalDate?,
        frozen: Boolean = false,
        portions: Int = 1,
    ): Submitted<PantryRow> {
        val queued = QueuedChange.PantryAdded(
            profileId,
            ingredient.id,
            ingredient.name,
            ingredient.category,
            quantity,
            unit,
            bestBefore?.toString(),
            UUID.randomUUID().toString(),
            frozen,
            portions,
        )
        return queue.submit(queued) { send(queued) }
    }

    /** Sends what is put in; answers the row it is kept in. */
    suspend fun send(change: QueuedChange.PantryAdded): PantryRow = apiCall(json) {
        api.addToPantry(
            change.key,
            UpsertInventoryItemRequest(
                change.profileId,
                change.ingredientId,
                change.quantity,
                change.unit,
                change.bestBefore,
                change.frozen,
                change.portions,
            ),
        )
    }.toDomain()

    /** Changes the row [id] of [profileId] as [change] says, and nothing else of it: the row as it is kept now, or kept to be sent in its turn. */
    suspend fun change(profileId: String, id: String, change: PantryChange): Submitted<PantryRow> {
        val queued = QueuedChange.PantryChanged(profileId, id, change.quantity, change.newDay, change.bestBefore?.toString())
        return queue.submit(queued) { send(queued) }
    }

    /**
     * "Nadal dobre": fresh stock [id] past its day is still good, until [bestBefore] where one is given, or until its day
     * or amount changes; the row as it is kept now. It needs the network.
     */
    suspend fun keep(id: String, bestBefore: LocalDate?): PantryRow =
        apiCall(json) { api.keepPantryRow(id, StillGoodRequest(bestBefore?.toString())) }.toDomain()

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

    /**
     * Puts the fresh row [id] of [profileId]'s pantry into the freezer on [today], split into [portions] packages: the
     * pantry as it is then, or kept to be sent in its turn, under a key that has the server freeze it once however often
     * it is sent.
     */
    /** The size to freeze row [id] in, from the profile's plans; asked of the server, never kept. */
    suspend fun portion(id: String): PantryPortion = apiCall(json) { api.pantryRowPortion(id) }.let { PantryPortion(it.portion, it.unit) }

    suspend fun freeze(profileId: String, id: String, portions: Int, today: LocalDate): Submitted<List<PantryRow>> {
        val queued = QueuedChange.PantryFrozen(profileId, id, UUID.randomUUID().toString(), frozenUntil(today).toString(), portions)
        return queue.submit(queued) { send(queued) }
    }

    /** Sends a row's freezing; answers the pantry of its profile as it is then. */
    suspend fun send(change: QueuedChange.PantryFrozen): List<PantryRow> =
        apiCall(json) { api.freezePantryRow(change.key, change.id, FreezePantryRowRequest(change.portions)) }.map { it.toDomain() }

    /**
     * What eating the meal or the entry [of] does to its profile's pantry on a day no shopping list covers: what taking
     * it would take, and what it took. Asked of the server, never kept: it needs the network.
     */
    suspend fun eaten(of: EatenOf): EatenPantry =
        apiCall(json) {
            when (of) {
                is EatenOf.Meal -> api.mealPantry(of.planId, of.mealId)
                is EatenOf.Entry -> api.entryPantry(of.id)
            }
        }.toDomain()

    /** Takes what [of] ate off the pantry, as offered: once, until it is given back. It needs the network. */
    suspend fun takeEaten(of: EatenOf) =
        apiCall(json) {
            when (of) {
                is EatenOf.Meal -> api.takeMealPantry(of.planId, of.mealId)
                is EatenOf.Entry -> api.takeEntryPantry(of.id)
            }
        }

    /** Gives back to the pantry what [of] took off it ("Oddaj do spiżarni"). It needs the network. */
    suspend fun giveBackEaten(of: EatenOf) =
        apiCall(json) {
            when (of) {
                is EatenOf.Meal -> api.returnMealPantry(of.planId, of.mealId)
                is EatenOf.Entry -> api.returnEntryPantry(of.id)
            }
        }

    /**
     * What eating [meal], of a joint plan and marked eaten by both, does to the pantries: both portions worked out
     * together, its own plan's first, so that no food is offered twice. Asked of the server, never kept.
     */
    suspend fun eatenByBoth(meal: EatenOf.Meal): List<EatenPantry> =
        apiCall(json) { api.mealPantries(meal.planId, meal.mealId) }.map { it.toDomain() }

    /** Takes both portions of [meal] off the pantries, as offered: once, until each is given back. It needs the network. */
    suspend fun takeEatenByBoth(meal: EatenOf.Meal) = apiCall(json) { api.takeMealPantries(meal.planId, meal.mealId) }
}

private fun EatenPantryDto.toDomain() = EatenPantry(profileName, offer.map { it.toDomain() }, taken.map { it.toDomain() })

private fun EatenFromPantryDto.toDomain() =
    EatenFood(ingredientId, name, eaten.shown(), held.shown(), taken.shown(), borrowed?.let { Borrowed(it.amount.shown(), it.from) })

private fun DisplayAmountDto.shown() = ShownAmount(quantity, unit, grams)

private fun InventoryItemDto.toDomain() = PantryRow(
    id = id,
    ingredientId = ingredient.id,
    name = ingredient.name,
    category = ingredient.category,
    shown = display.quantity,
    shownUnit = display.unit,
    bestBefore = bestBefore?.let(LocalDate::parse),
    shownGrams = display.grams,
    frozen = frozen,
    quantity = quantity,
    unit = unit,
    keptPastDate = keptPastDate,
)

private fun IngredientDto.toStockable() = StockableIngredient(id, name, category, pantryUnits, pantryUnits.first(), displayUnit ?: "piece")
