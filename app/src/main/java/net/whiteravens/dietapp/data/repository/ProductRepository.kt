// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.OwnProductBodyDto
import net.whiteravens.dietapp.data.remote.OwnProductDto
import net.whiteravens.dietapp.data.remote.OwnProductPer100Dto
import net.whiteravens.dietapp.data.remote.ProductDto
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.NewOwnProduct
import net.whiteravens.dietapp.domain.OwnProduct
import net.whiteravens.dietapp.domain.OwnProductState
import net.whiteravens.dietapp.domain.Product
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The products of the account's own: written, changed, sent to the instance's reviewers, taken back
 * and removed. All of it needs a network: nothing here goes through the queue of changes.
 */
@Singleton
class ProductRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
) {
    /** The account's products, the latest change first. */
    suspend fun mine(): List<OwnProduct> = apiCall(json) { api.ownProducts() }.items.map { it.toDomain() }

    /** Writes a product; a code the base knows is refused with `PRODUCT_CODE_KNOWN`. */
    suspend fun write(product: NewOwnProduct): OwnProduct = apiCall(json) { api.writeOwnProduct(product.toBody()) }.toDomain()

    /** Changes a product; one waiting for review leaves the queue. */
    suspend fun change(id: String, product: NewOwnProduct): OwnProduct =
        apiCall(json) { api.changeOwnProduct(id, product.toBody()) }.toDomain()

    suspend fun remove(id: String) = apiCall(json) { api.removeOwnProduct(id) }

    /** Sends a product to the instance's reviewers. */
    suspend fun send(id: String): OwnProduct = apiCall(json) { api.sendOwnProduct(id) }.toDomain()

    /** Takes a product back from the queue. */
    suspend fun withdraw(id: String): OwnProduct = apiCall(json) { api.withdrawOwnProduct(id) }.toDomain()

    /** The product the base holds of [code]. */
    suspend fun inTheBase(code: String): Product = apiCall(json) { api.productByCode(code) }.toDomain()

    /** What the people of the instance added to its base, as a file to read. */
    suspend fun additions(): String = READABLE.encodeToString(JsonObject.serializer(), apiCall(json) { api.productAdditions() })
}

private val READABLE = Json { prettyPrint = true }

private fun NewOwnProduct.toBody() = OwnProductBodyDto(
    code = code,
    name = name,
    brand = brand,
    pack = pack,
    unit = unit,
    packAmount = packAmount,
    servingAmount = servingAmount,
    per100 = OwnProductPer100Dto(calories, protein, fat, saturatedFat, carbs, sugars, fiber, salt),
)

private fun OwnProductDto.toDomain() = OwnProduct(
    product = ProductDto(
        id = id,
        code = code,
        source = "own",
        name = name,
        brand = brand,
        pack = pack,
        unit = unit,
        packAmount = packAmount,
        servingAmount = servingAmount,
        per100 = per100,
        energyWorkedOut = energyWorkedOut,
        doubtful = doubtful,
    ).toDomain(),
    state = when (state) {
        "sent" -> OwnProductState.SENT
        "rejected" -> OwnProductState.REJECTED
        else -> OwnProductState.OWN
    },
    reason = reason,
)
