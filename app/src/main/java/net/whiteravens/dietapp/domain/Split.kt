// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

/**
 * Each portion's share of a dish, from the energy each is planned to hold, as parts of one, as the
 * web works it out. Where no portion's energy is known they share it equally; a portion of unknown
 * energy beside known ones gets nothing.
 */
fun sharesOf(kcal: List<Double?>): List<Double> {
    val known = kcal.map { if (it != null && it.isFinite() && it > 0) it else 0.0 }
    val total = known.sum()
    if (total <= 0) return kcal.map { 1.0 / kcal.size }
    return known.map { it / total }
}

/**
 * [whole] grams or pieces split by [shares] into whole ones that add up to it: a portion is its
 * rounded running total less the one before, so that each stays within one of its exact part and
 * the last takes what is left.
 */
fun splitWhole(whole: Double, shares: List<Double>): List<Long> {
    val total = maxOf(0L, Math.round(whole))
    var running = 0.0
    var before = 0L
    return shares.mapIndexed { index, share ->
        running += share
        val upTo = if (index == shares.lastIndex) total else minOf(total, Math.round(total * running))
        val portion = upTo - before
        before = upTo
        portion
    }
}

/** What the food alone weighs: the scale's weight, less the empty pot's where it was weighed in it; null where that leaves nothing. */
fun foodWeight(onScale: Double, emptyPot: Double?): Double? {
    val food = if (emptyPot == null) onScale else onScale - emptyPot
    return food.takeIf { it.isFinite() && it > 0 }
}

/** What a portion takes of an ingredient: by which the ingredient, weighed alone, is split. */
data class SplitLine(val ingredientId: String, val name: String, val quantity: Double)

/** The ingredients the portions take, each once, in the order of their lines. */
fun ingredientsOf(portions: List<List<SplitLine>>): List<SplitLine> =
    portions.flatten().distinctBy { it.ingredientId }

/**
 * Each portion's share of an ingredient weighed alone, as the web works it out: what each takes of it, all its
 * lines of it together, as parts of one. A plate of a dish both people eat holds its parts in proportions of its own.
 */
fun sharesOfIngredient(portions: List<List<SplitLine>>, ingredientId: String): List<Double> =
    sharesOf(portions.map { lines -> lines.filter { it.ingredientId == ingredientId }.sumOf { it.quantity } })
