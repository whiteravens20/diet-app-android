// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.products

import net.whiteravens.dietapp.domain.NewOwnProduct
import net.whiteravens.dietapp.domain.OwnProductLimits
import net.whiteravens.dietapp.domain.Product
import net.whiteravens.dietapp.domain.productCodeOf
import net.whiteravens.dietapp.ui.screens.diary.number
import net.whiteravens.dietapp.ui.screens.diary.plainNumber

/** A field of the form, by what it holds. */
enum class OwnField { CODE, NAME, PACK_AMOUNT, SERVING_AMOUNT, CALORIES, PROTEIN, FAT, SATURATED, CARBS, SUGARS, FIBER, SALT }

/** What is wrong with what a field holds. */
enum class OwnProblem { CODE, NAME, AMOUNT, CALORIES, FIGURE, MACROS, SUGARS, SATURATED }

/**
 * A product as the form holds it, as it is typed: what the API holds it to is said of a field before
 * anything is sent, and what merely does not add up is the API's to mark.
 */
data class OwnProductDraft(
    val code: String = "",
    val name: String = "",
    val brand: String = "",
    val pack: String = "",
    val unit: String = "g",
    val packAmount: String = "",
    val servingAmount: String = "",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val saturatedFat: String = "",
    val carbs: String = "",
    val sugars: String = "",
    val fiber: String = "",
    val salt: String = "",
) {
    fun problems(): Map<OwnField, OwnProblem> = buildMap {
        if (code.isNotBlank() && productCodeOf(code) == null) put(OwnField.CODE, OwnProblem.CODE)
        if (name.none(Char::isLetterOrDigit)) put(OwnField.NAME, OwnProblem.NAME)
        listOf(OwnField.PACK_AMOUNT to packAmount, OwnField.SERVING_AMOUNT to servingAmount).forEach { (field, text) ->
            if (text.isNotBlank() && text.number()?.takeIf { it > 0 && it <= OwnProductLimits.MOST_AMOUNT } == null) {
                put(field, OwnProblem.AMOUNT)
            }
        }
        if (calories.number()?.takeIf { it in 0.0..OwnProductLimits.MOST_CALORIES } == null) put(OwnField.CALORIES, OwnProblem.CALORIES)
        figures().forEach { (field, text) ->
            if (text.isNotBlank() && text.number()?.takeIf { it in 0.0..OwnProductLimits.MOST_GRAMS } == null) put(field, OwnProblem.FIGURE)
        }
        val known = { text: String -> text.number() }
        val macros = listOf(protein, fat, carbs).mapNotNull(known).sum()
        if (macros > OwnProductLimits.MOST_MACROS && OwnField.CARBS !in this) put(OwnField.CARBS, OwnProblem.MACROS)
        if (isMoreThanItsWhole(sugars, carbs) && OwnField.SUGARS !in this) put(OwnField.SUGARS, OwnProblem.SUGARS)
        if (isMoreThanItsWhole(saturatedFat, fat) && OwnField.SATURATED !in this) put(OwnField.SATURATED, OwnProblem.SATURATED)
    }

    private fun figures() = listOf(
        OwnField.PROTEIN to protein,
        OwnField.FAT to fat,
        OwnField.SATURATED to saturatedFat,
        OwnField.CARBS to carbs,
        OwnField.SUGARS to sugars,
        OwnField.FIBER to fiber,
        OwnField.SALT to salt,
    )

    private fun isMoreThanItsWhole(part: String, whole: String): Boolean {
        val partOf = part.number() ?: return false
        val wholeOf = whole.number() ?: return false
        return partOf > wholeOf + OwnProductLimits.PART_SLACK
    }

    /** What is sent; null while something is wrong with it. */
    fun toNew(): NewOwnProduct? = if (problems().isNotEmpty()) {
        null
    } else {
        NewOwnProduct(
            code = code.trim().ifEmpty { null },
            name = name.trim(),
            brand = brand.trim().ifEmpty { null },
            pack = pack.trim().ifEmpty { null },
            unit = unit,
            packAmount = packAmount.number(),
            servingAmount = servingAmount.number(),
            calories = checkNotNull(calories.number()),
            protein = protein.number(),
            fat = fat.number(),
            saturatedFat = saturatedFat.number(),
            carbs = carbs.number(),
            sugars = sugars.number(),
            fiber = fiber.number(),
            salt = salt.number(),
        )
    }

    companion object {
        private fun typed(value: Double?): String = value?.let(::plainNumber).orEmpty()

        /** The form of [product], one of the reader's own or one of the base to correct, as it stands; or of a new product of [code]. */
        fun of(source: Product?, code: String): OwnProductDraft = source?.let { product ->
            OwnProductDraft(
                code = product.code.orEmpty(),
                name = product.name,
                brand = product.brand.orEmpty(),
                pack = product.pack.orEmpty(),
                unit = product.unit,
                packAmount = typed(product.packAmount),
                servingAmount = typed(product.servingAmount),
                calories = typed(product.calories),
                protein = typed(product.protein),
                fat = typed(product.fat),
                saturatedFat = typed(product.saturatedFat),
                carbs = typed(product.carbs),
                sugars = typed(product.sugars),
                fiber = typed(product.fiber),
                salt = typed(product.salt),
            )
        } ?: OwnProductDraft(code = code)
    }
}
