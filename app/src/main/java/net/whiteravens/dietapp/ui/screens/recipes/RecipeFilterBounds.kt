// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

/** What a figure of the library's filters may be, from [least] to [most], and whether only whole ones. */
internal data class Bounds(val least: Double, val most: Double, val whole: Boolean)

/** The bounds the API takes of each figure: whole kilocalories and minutes, grams of a serving to the gram or finer. */
internal val CALORIES = Bounds(1.0, 10_000.0, whole = true)
internal val MINUTES = Bounds(1.0, 1_440.0, whole = true)
internal val PROTEIN_OR_FAT = Bounds(0.0, 500.0, whole = false)
internal val CARBS = Bounds(0.0, 1_000.0, whole = false)

/** A number typed with a comma or a point; nothing where it is not one, or under nothing. */
internal fun decimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

/** Whether [typed] is a figure outside [bounds]; an empty field is none at all. */
internal fun outside(typed: String, bounds: Bounds): Boolean = decimal(typed)?.let { it < bounds.least || it > bounds.most } ?: false
