// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * The custom diet's two ways, worked out on the phone as the API works them out: a split typed in grams kept as the
 * shares of their energy, and protein per kilogram of the weight with fat's share and carbohydrate the rest, every
 * share inside the limits of a split.
 */

/** The least and the most share of a day's energy, in per cent, that the custom diet's split may give each part. */
object SplitLimits {
    val PROTEIN = 10..45
    val FAT = 15..75
    val CARBS = 5..70
}

/** The custom diet's protein per kilogram of the body's weight, in grams: the least and the most, and where a form starts. */
object ProteinPerKg {
    const val LEAST = 0.8
    const val MOST = 3.0
    const val FIRST = 1.6
}

private const val KCAL_PROTEIN = 4.0
private const val KCAL_FAT = 9.0
private const val KCAL_CARBS = 4.0

/** Grams of protein, fat and carbohydrate. */
data class MacroGrams(val protein: Int, val fat: Int, val carbs: Int)

/** Shares of one as whole per cent that add up to a hundred, each rounded the way that misses its share least. */
fun wholePercent(protein: Double, fat: Double, carbs: Double): MacroSplit {
    val exact = listOf(protein * 100, fat * 100, carbs * 100)
    val whole = exact.map { floor(it).toInt() }.toMutableList()
    val left = 100 - whole.sum()
    exact.indices.sortedByDescending { exact[it] - floor(exact[it]) }.take(left.coerceAtLeast(0)).forEach { whole[it] += 1 }
    return MacroSplit(protein = whole[0], fat = whole[1], carbs = whole[2])
}

/** Whether every share of a split in whole per cent is inside the limits a split keeps. */
fun insideLimits(split: MacroSplit): Boolean =
    split.protein in SplitLimits.PROTEIN && split.fat in SplitLimits.FAT && split.carbs in SplitLimits.CARBS

/** The energy of grams, in kcal. */
fun kcalOfGrams(protein: Double, fat: Double, carbs: Double): Double = protein * KCAL_PROTEIN + fat * KCAL_FAT + carbs * KCAL_CARBS

/** Grams as the split of their energy, in whole per cent; null for grams that hold none. */
fun splitOfGrams(protein: Double, fat: Double, carbs: Double): MacroSplit? {
    val kcal = kcalOfGrams(protein, fat, carbs)
    if (!(kcal > 0)) return null
    return wholePercent(protein * KCAL_PROTEIN / kcal, fat * KCAL_FAT / kcal, carbs * KCAL_CARBS / kcal)
}

/** What a split in per cent comes to in whole grams at [calories]. */
fun gramsAt(split: MacroSplit, calories: Int): MacroGrams = MacroGrams(
    protein = (calories * split.protein / 100.0 / KCAL_PROTEIN).roundToInt(),
    fat = (calories * split.fat / 100.0 / KCAL_FAT).roundToInt(),
    carbs = (calories * split.carbs / 100.0 / KCAL_CARBS).roundToInt(),
)

/**
 * The least and the most share of a day's energy, of one, that protein of the custom diet may have while fat keeps
 * [fat] (a share of one) and carbohydrate takes the rest: every share inside [SplitLimits].
 */
fun proteinShareBounds(fat: Double): ClosedFloatingPointRange<Double> =
    maxOf(SplitLimits.PROTEIN.first / 100.0, 1 - fat - SplitLimits.CARBS.last / 100.0)..
        minOf(SplitLimits.PROTEIN.last / 100.0, 1 - fat - SplitLimits.CARBS.first / 100.0)

/**
 * The grams of protein a kilogram of [weightKg] that keep every share inside its limits at [target] kcal while fat
 * keeps [fat] (a share of one): to a tenth inward, inside [ProteinPerKg]; null where none do.
 */
fun proteinPerKgThatFits(fat: Double, target: Int, weightKg: Double): ClosedFloatingPointRange<Double>? {
    val bounds = proteinShareBounds(fat)
    fun perKg(share: Double) = share * target / KCAL_PROTEIN / weightKg
    // A bound that is a tenth but for the error of floating point is that tenth.
    val from = maxOf(ProteinPerKg.LEAST, ceil(perKg(bounds.start) * 10 - 1e-9) / 10)
    val to = minOf(ProteinPerKg.MOST, floor(perKg(bounds.endInclusive) * 10 + 1e-9) / 10)
    return if (from <= to) from..to else null
}

/** What protein per kilogram comes to at a target and a weight. */
data class PerKgSplit(
    /** The split it comes to, in whole per cent, protein held inside the limits. */
    val split: MacroSplit,
    /** Grams of protein a day, as the weight gives them before any limit. */
    val protein: Double,
    /** Grams of protein a day once a limit holds them. */
    val keptGrams: Double,
    /** Protein's share went past a limit, its own or carbohydrate's, and is held there. */
    val held: Boolean,
    /** The grams a kilogram that keep every share inside its limits here, to a tenth inward; null where none do. */
    val fits: ClosedFloatingPointRange<Double>?,
)

/**
 * The way of protein per kilogram at [target] kcal and [weightKg]: protein the energy of its grams, fat its
 * [fatPercent], carbohydrate the rest; protein held at the limit where a share would go past one, as the API holds it.
 */
fun perKgSplit(perKg: Double, fatPercent: Int, weightKg: Double, target: Int): PerKgSplit {
    val protein = perKg * weightKg
    val fat = fatPercent / 100.0
    val asked = protein * KCAL_PROTEIN / target
    val bounds = proteinShareBounds(fat)
    // As the API holds it, a fat past its own limits leaving no room at all included: the most, then the least.
    val kept = minOf(bounds.endInclusive, maxOf(bounds.start, asked))
    return PerKgSplit(
        split = wholePercent(kept, fat, 1 - kept - fat),
        protein = protein,
        keptGrams = kept * target / KCAL_PROTEIN,
        held = kotlin.math.abs(kept - asked) > 1e-9,
        fits = proteinPerKgThatFits(fat, target, weightKg),
    )
}
