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
import net.whiteravens.dietapp.domain.PantryChange
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.StockableIngredient
import net.whiteravens.dietapp.domain.firstPantryUnit
import net.whiteravens.dietapp.domain.pantryUnits
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A profile's pantry: its rows read, some of an ingredient put in, a row changed or taken out. */
@Singleton
class PantryRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
) {
    /** What [profileId]'s pantry holds, in the order the API gives. */
    suspend fun rows(profileId: String): List<PantryRow> = apiCall(json) { api.pantry(profileId) }.map { it.toDomain() }

    /** The catalogue's ingredients whose name holds [search], in the app's language, each with the units the pantry keeps it in. */
    suspend fun ingredients(search: String): List<StockableIngredient> =
        apiCall(json) { api.ingredients(search = search) }.map { it.toStockable() }

    /**
     * Puts [quantity] of [ingredientId], in [unit], into [profileId]'s pantry, best before [bestBefore]
     * where one is given; answers the row it is kept in.
     */
    suspend fun add(profileId: String, ingredientId: String, quantity: Double, unit: String, bestBefore: LocalDate?): PantryRow =
        apiCall(json) { api.addToPantry(UpsertInventoryItemRequest(profileId, ingredientId, quantity, unit, bestBefore?.toString())) }.toDomain()

    /** Changes the row [id] as [change] says, and nothing else of it; answers the row as it is kept now. */
    suspend fun change(id: String, change: PantryChange): PantryRow {
        val request = PatchInventoryItemRequest(
            quantity = change.quantity,
            bestBefore = if (change.newDay) change.bestBefore?.let { JsonPrimitive(it.toString()) } ?: JsonNull else null,
        )
        return apiCall(json) { api.changePantryRow(id, request) }.toDomain()
    }

    suspend fun remove(id: String) = apiCall(json) { api.removePantryRow(id) }
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

private fun IngredientDto.toStockable(): StockableIngredient {
    val units = pantryUnits(canonicalUnit, gramsPerPiece, density)
    return StockableIngredient(id, name, units, firstPantryUnit(units, canonicalUnit, displayUnit), displayUnit ?: "piece")
}
