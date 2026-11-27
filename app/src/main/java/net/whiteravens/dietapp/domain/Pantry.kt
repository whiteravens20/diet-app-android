// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

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
    /** What [shown] weighs, where it is in pieces of some kind. */
    val shownGrams: Double? = null,
    /**
     * In the freezer: a package of its own beside what is fresh of the ingredient, put in by hand or a rest of a plan.
     * Every freezing is one, and none adds to another; a food may have many, each with its day.
     */
    val frozen: Boolean = false,
    /** How much of it the row holds, exactly, in [unit]; as shown where the row is one waiting to be sent. */
    val quantity: Double = shown,
    /** `g` | `ml` | `piece`: the unit the API keeps it in. */
    val unit: String = shownUnit,
    /** Fresh stock past [bestBefore] the user said is still good: it counts at home until its day or amount changes. */
    val keptPastDate: Boolean = false,
)

/**
 * An ingredient of the catalogue as the pantry takes it: the [units] the API keeps it in, the [unit]
 * offered first, and what a piece of it is called ([pieceName]).
 */
@Serializable
data class StockableIngredient(
    val id: String,
    val name: String,
    /** The category the pantry groups it under. */
    val category: String,
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

/**
 * The day what goes into the freezer on [today] is best eaten before, as the API dates it: 30 days on, the shortest
 * time a frozen kind keeps its quality, so that the next plans take it first.
 */
fun frozenUntil(today: LocalDate): LocalDate = today.plusDays(FROZEN_KEEPS_DAYS.toLong())

/** How many days on what goes into the freezer is dated, as the API dates it. */
const val FROZEN_KEEPS_DAYS = 30

/** The most packages what goes into the freezer is split into at once, as the API takes it. */
const val MAX_PORTIONS = 10

/** Quantities closer than this are the same quantity: the noise of converting units. */
private const val PORTION_EPSILON = 1e-6

private fun toMillionths(quantity: Double): Double = (quantity * 1e6).roundToLong() / 1e6

/**
 * [quantity] split into [portions] packages of the freezer, as the API splits it: whole grams, millilitres or pieces
 * as evenly as they go, the larger parts first (500 g in 3: 167, 167 and 166; 6 pieces in 4: 2, 2, 1 and 1), and what
 * is less than one of the unit with the last. Null where a part would hold less than one of the unit.
 */
fun portionsOf(quantity: Double, portions: Int): List<Double>? {
    if (portions <= 1) return listOf(quantity)
    val whole = floor(quantity + PORTION_EPSILON).toInt()
    if (whole < portions) return null
    val base = whole / portions
    val more = whole - base * portions
    return List(portions) { at ->
        toMillionths(base + (if (at < more) 1 else 0) + (if (at == portions - 1) quantity - whole else 0.0))
    }
}

/** A number of portions as it is typed, or null where it is none the freezer takes. */
fun portionCount(typed: String): Int? = typed.trim().toIntOrNull()?.takeIf { it in 1..MAX_PORTIONS }

/**
 * The fewest portions [quantity] goes into with none bigger than [most], at most [MAX_PORTIONS], as the API counts them:
 * what to split it in for a plan that takes a package out of the freezer only whole, [most] being what two days of it
 * eat. One where it is no bigger than [most], or where there is no [most]; never parts of less than one of the unit.
 */
fun portionsUpTo(quantity: Double, most: Double?): Int {
    if (most == null || most <= 0.0 || quantity <= most + PORTION_EPSILON) return 1
    val count = minOf(MAX_PORTIONS, ceil(quantity / most - PORTION_EPSILON).toInt())
    return maxOf(1, minOf(count, floor(quantity + PORTION_EPSILON).toInt()))
}

/**
 * The size the profile's plans would take a row of the pantry out of the freezer in ([portion], in [unit]): the most
 * they eat of its food on a day and the next from tomorrow on; null where none of them takes it from packs.
 */
data class PantryPortion(val portion: Int?, val unit: String)

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
