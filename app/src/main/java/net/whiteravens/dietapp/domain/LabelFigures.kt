// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/** Fibre, sugars, salt and saturated fat, in the order they are shown. */
enum class LabelNutrient(val code: String) {
    FIBER("fiber"),
    SUGARS("sugars"),
    SALT("salt"),
    SATURATED_FAT("saturatedFat");

    companion object {
        fun of(code: String): LabelNutrient? = entries.firstOrNull { it.code == code }
    }
}

/** A figure of one of them, in grams: [atLeast] where a part of what it sums gives none. */
data class LabelFigure(val grams: Double, val atLeast: Boolean)

/**
 * Fibre, sugars, salt and saturated fat of a serving, a meal or a day, as the API worked them out
 * and rounded them; null where nothing of it gives the figure.
 */
data class LabelTotals(
    val fiber: LabelFigure?,
    val sugars: LabelFigure?,
    val salt: LabelFigure?,
    val saturatedFat: LabelFigure?,
) {
    operator fun get(nutrient: LabelNutrient): LabelFigure? = when (nutrient) {
        LabelNutrient.FIBER -> fiber
        LabelNutrient.SUGARS -> sugars
        LabelNutrient.SALT -> salt
        LabelNutrient.SATURATED_FAT -> saturatedFat
    }

    /** A figure sums parts some of which give none of it. */
    val anyAtLeast: Boolean get() = LabelNutrient.entries.any { this[it]?.atLeast == true }

    /** A figure nothing of it gives. */
    val anyUnknown: Boolean get() = LabelNutrient.entries.any { this[it] == null }
}
