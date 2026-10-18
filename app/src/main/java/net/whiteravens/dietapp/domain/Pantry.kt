// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import java.time.LocalDate

/**
 * A row of a profile's pantry: how much of an ingredient it holds, as the API shows it ([shown] in
 * [shownUnit], a piece called what the ingredient calls it), and the day it is best eaten before.
 */
data class PantryRow(
    val id: String,
    val ingredientId: String,
    val name: String,
    val category: String,
    val shown: Double,
    val shownUnit: String,
    val bestBefore: LocalDate?,
)

/**
 * An ingredient of the catalogue as the pantry takes it: the [units] it can be kept in, the [unit]
 * offered first, and what a piece of it is called ([pieceName]).
 */
data class StockableIngredient(
    val id: String,
    val name: String,
    val units: List<String>,
    val unit: String,
    val pieceName: String,
)

/**
 * What is changed of a pantry row: its amount, in the row's unit, where [quantity] is not null, and
 * its best-before day, [bestBefore] or none, where [newDay] is true.
 */
data class PantryChange(val quantity: Double? = null, val newDay: Boolean = false, val bestBefore: LocalDate? = null) {
    val isEmpty: Boolean get() = quantity == null && !newDay
}

/** How near a row is to its best-before day. */
enum class Freshness { FINE, SOON, PAST }

/**
 * The units the API keeps an ingredient in: those it converts into the unit the ingredient is
 * weighed or measured in ([canonicalUnit]). A piece takes the weight of one ([gramsPerPiece]),
 * grams and millilitres the weight of a millilitre ([density]), and a piece of something measured
 * in millilitres both. Stock in any other unit would count as nothing, and is refused. What is
 * counted ([displayUnit]: eggs, slices, cloves) is kept in its pieces alone, as the API keeps it.
 */
fun pantryUnits(canonicalUnit: String, gramsPerPiece: Double?, density: Double?, displayUnit: String?): List<String> {
    val piece = gramsPerPiece != null && gramsPerPiece > 0
    val volume = density != null && density > 0
    fun converts(unit: String): Boolean = when {
        unit == canonicalUnit -> true
        unit == "piece" || canonicalUnit == "piece" -> piece && ("g" in setOf(unit, canonicalUnit) || volume)
        else -> volume
    }
    if (displayUnit != null && converts("piece")) return listOf("piece")
    return PANTRY_UNITS.filter(::converts)
}

/**
 * The unit an ingredient is put into the pantry in at first: pieces of its own where it is counted
 * ([displayUnit]) and a piece of it can be kept, the unit it is weighed or measured in otherwise.
 */
fun firstPantryUnit(units: List<String>, canonicalUnit: String, displayUnit: String?): String =
    if (displayUnit != null && "piece" in units) "piece" else canonicalUnit

/**
 * How near [bestBefore] is on [today]: past it from the day after it; soon from two days before it
 * to the day itself, when the web's pantry says it expires soon; fine before that, or where no day
 * is kept.
 */
fun freshness(bestBefore: LocalDate?, today: LocalDate): Freshness = when {
    bestBefore == null -> Freshness.FINE
    bestBefore.isBefore(today) -> Freshness.PAST
    !bestBefore.isAfter(today.plusDays(SOON_DAYS)) -> Freshness.SOON
    else -> Freshness.FINE
}

/** The units a pantry row can be in, in the order a choice lists them. */
private val PANTRY_UNITS = listOf("g", "ml", "piece")

/** How many days before its best-before day a row is said to go soon. */
private const val SOON_DAYS = 2L
