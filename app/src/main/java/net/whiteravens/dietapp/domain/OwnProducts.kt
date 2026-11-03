// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/** Where the figures of a product come from: Open Food Facts, a person whose product the instance accepted, or the reader. */
enum class ProductSource { OPEN_FOOD_FACTS, INSTANCE, OWN }

/** What became of a product of the account: for itself alone, waiting for the instance's reviewers, or turned down or withdrawn. */
enum class OwnProductState { OWN, SENT, REJECTED }

/** A product of the account's own, with [reason] where it was turned down or withdrawn. */
data class OwnProduct(val product: Product, val state: OwnProductState, val reason: String?) {
    /** A product goes to the base with its code and all of its energy, protein, fat and carbohydrates. */
    val sendable: Boolean
        get() = product.code != null && product.protein != null && product.fat != null && product.carbs != null
}

/** What is written of a product of the account's own: a name and its energy are needed. */
data class NewOwnProduct(
    val code: String?,
    val name: String,
    val brand: String?,
    val pack: String?,
    val unit: String,
    val packAmount: Double?,
    val servingAmount: Double?,
    val calories: Double,
    val protein: Double?,
    val fat: Double?,
    val saturatedFat: Double?,
    val carbs: Double?,
    val sugars: Double?,
    val fiber: Double?,
    val salt: Double?,
)

/** What the API holds a product of an account's own to. */
object OwnProductLimits {
    const val MOST_HELD = 500
    const val WRITTEN_A_DAY = 50
    const val SENT_A_DAY = 10
    const val NAME = 160
    const val BRAND = 80
    const val PACK = 40
    const val MOST_AMOUNT = 100_000.0
    const val MOST_CALORIES = 900.0
    const val MOST_GRAMS = 100.0

    /** The three macronutrients in 100 g, with what the rounding of a label can add. */
    const val MOST_MACROS = 105.0

    /** What the rounding of a label can put between a part and the whole it is a part of. */
    const val PART_SLACK = 0.5
}
