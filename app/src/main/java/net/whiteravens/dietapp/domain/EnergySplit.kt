// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import kotlin.math.floor

/** kcal in 1 g of protein, fat and carbohydrate (Atwater factors), as the API splits a target into grams. */
private val KCAL_PER_GRAM = listOf(4.0, 9.0, 4.0)

/** The energy that grams of protein, fat and carbohydrate give, in that order. */
fun energyOf(grams: List<Double>): List<Double> = grams.zip(KCAL_PER_GRAM) { g, kcal -> g * kcal }

/**
 * The share of the energy that grams of protein, fat and carbohydrate each give, which is how a
 * profile's split is set: a gram of fat gives more than twice the energy of the others. Whole
 * percents that add up to a hundred, the remainders going to the largest; null where the grams give
 * no energy.
 */
fun energyPercents(grams: List<Double>): List<Int>? {
    val energy = energyOf(grams)
    val total = energy.sum()
    if (total <= 0) return null
    val exact = energy.map { it * 100 / total }
    val percents = exact.map { floor(it).toInt() }.toMutableList()
    val left = 100 - percents.sum()
    exact.indices.sortedWith(compareByDescending<Int> { exact[it] - floor(exact[it]) }.thenBy { it })
        .take(left)
        .forEach { percents[it] += 1 }
    return percents
}
