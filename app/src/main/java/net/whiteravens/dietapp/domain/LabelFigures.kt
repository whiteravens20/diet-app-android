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

/** What a profile allows or aims at of them, in grams a day; null where it sets nothing. */
data class LabelLimits(
    val saltAtMost: Double? = null,
    val fiberAtLeast: Double? = null,
    val saturatedFatAtMost: Double? = null,
    val sugarsAtMost: Double? = null,
) {
    operator fun get(limit: LabelLimit): Double? = when (limit) {
        LabelLimit.SALT_AT_MOST -> saltAtMost
        LabelLimit.FIBER_AT_LEAST -> fiberAtLeast
        LabelLimit.SATURATED_FAT_AT_MOST -> saturatedFatAtMost
        LabelLimit.SUGARS_AT_MOST -> sugarsAtMost
    }

    fun with(limit: LabelLimit, grams: Double?): LabelLimits = when (limit) {
        LabelLimit.SALT_AT_MOST -> copy(saltAtMost = grams)
        LabelLimit.FIBER_AT_LEAST -> copy(fiberAtLeast = grams)
        LabelLimit.SATURATED_FAT_AT_MOST -> copy(saturatedFatAtMost = grams)
        LabelLimit.SUGARS_AT_MOST -> copy(sugarsAtMost = grams)
    }
}

/**
 * A limit a profile can set, in the order the profile shows them: where a turned-on one starts
 * (salt of the WHO, fibre of the EFSA, saturated fat and sugars of the reference intakes on EU
 * labels) and the least and the most the API takes, as packages/shared `LABEL_LIMIT_STARTS` and
 * `LABEL_LIMIT_BOUNDS` say.
 */
enum class LabelLimit(val start: Double, val min: Double, val max: Double) {
    SALT_AT_MOST(start = 5.0, min = 1.0, max = 20.0),
    FIBER_AT_LEAST(start = 25.0, min = 5.0, max = 80.0),
    SATURATED_FAT_AT_MOST(start = 20.0, min = 5.0, max = 100.0),
    SUGARS_AT_MOST(start = 90.0, min = 10.0, max = 300.0),
}

/** A limit of the profile that a day crosses: [atMost] for a most, else a least, and what the day comes to. */
data class LabelLimitNote(val nutrient: LabelNutrient, val atMost: Boolean, val limit: Double, val figure: LabelFigure)
