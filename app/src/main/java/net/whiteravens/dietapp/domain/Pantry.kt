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
 * An ingredient of the catalogue as the pantry takes it: the [units] the API keeps it in, the [unit]
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

/** How many days before its best-before day a row is said to go soon. */
private const val SOON_DAYS = 2L
