// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import net.whiteravens.dietapp.domain.MacroSplit
import net.whiteravens.dietapp.domain.PerKgSplit
import net.whiteravens.dietapp.domain.ProteinPerKg
import net.whiteravens.dietapp.domain.SplitLimits
import net.whiteravens.dietapp.domain.perKgSplit

/** How the custom diet's targets are set: the split of a day's energy, or protein per kilogram of the weight with fat's share. */
enum class OwnWay { SPLIT, PER_KG }

/** What is wrong with the custom diet's targets as they are typed: the form says it under them and keeps none of them. */
enum class OwnFault {
    /** The three shares do not add up to a hundred. */
    SUM,

    /** Protein per kilogram puts a share past a limit at the target and the weight. */
    PER_KG,
}

/**
 * The custom diet's targets as their fields hold them, as the profile's form and the form of a plan ask for them: the
 * way, the split typed in per cent, and protein per kilogram with fat's share. Whatever the way, they come
 * to a split in whole per cent ([split]), as a profile keeps it, and to protein per kilogram where that is the way
 * ([perKgValue]).
 */
data class OwnTargetsTyped(
    val way: OwnWay = OwnWay.SPLIT,
    val percent: SplitTyped = SplitTyped.of(ProfileChoices.FIRST_SPLIT),
    val perKg: String = ProteinPerKg.FIRST.plain(),
    val perKgFat: String = ProfileChoices.FIRST_SPLIT.fat.toString(),
) {
    /**
     * The split the targets come to, in whole per cent; null while a field of the way holds no number. For protein per
     * kilogram it is a split around fat's share, which the API takes for fat alone: what protein comes to is worked
     * out from the weight and the target, there and in [perKgAt].
     */
    fun split(): MacroSplit? = when (way) {
        OwnWay.SPLIT -> percent.split()
        OwnWay.PER_KG -> perKgFat.trim().toIntOrNull()?.let { fat ->
            splitAround(percent.protein.trim().toIntOrNull() ?: ProfileChoices.FIRST_SPLIT.protein, fat)
        }
    }

    /** Protein per kilogram where it is the way; null for the split's, and while it is no number. */
    fun perKgValue(): Double? = if (way == OwnWay.PER_KG) perKg.number() else null

    /** The fields of the way that hold no number where one is needed. */
    val notNumbers: Set<ProfileField>
        get() = when (way) {
            OwnWay.SPLIT -> percent.notWhole
            OwnWay.PER_KG -> buildSet {
                if (perKg.number() == null) add(ProfileField.PER_KG)
                if (perKgFat.trim().toIntOrNull() == null) add(ProfileField.SPLIT_FAT)
            }
        }

    /** What protein per kilogram comes to at [target] kcal and [weightKg]; null for the split's way, and while either is not known. */
    fun perKgAt(target: Int?, weightKg: Double?): PerKgSplit? {
        if (way != OwnWay.PER_KG || target == null || target <= 0 || weightKg == null || weightKg <= 0) return null
        val perKg = perKg.number() ?: return null
        val fat = perKgFat.trim().toIntOrNull() ?: return null
        return perKgSplit(perKg, fat, weightKg, target)
    }

    /**
     * What is wrong with the targets as they are typed, at [target] kcal and [weightKg]: three shares that do not add
     * up to a hundred, or protein per kilogram held at a limit there. Nothing
     * while a field holds no number, which says so itself.
     */
    fun fault(target: Int?, weightKg: Double?): OwnFault? {
        if (notNumbers.isNotEmpty()) return null
        return when (way) {
            OwnWay.SPLIT -> OwnFault.SUM.takeIf { percent.sum != 100 }
            OwnWay.PER_KG -> OwnFault.PER_KG.takeIf { perKgAt(target, weightKg)?.held == true }
        }
    }

    /** The targets set [now]: protein per kilogram begins with the fat of the split as it is typed. */
    fun inWay(now: OwnWay): OwnTargetsTyped = when {
        now == way -> this
        now == OwnWay.PER_KG -> copy(way = now, perKgFat = split()?.fat?.toString() ?: perKgFat)
        else -> copy(way = now)
    }

    companion object {
        /** The targets a profile keeps: protein per kilogram where it has it, the split's way where not. */
        fun of(split: MacroSplit?, perKg: Double?): OwnTargetsTyped {
            val first = split ?: ProfileChoices.FIRST_SPLIT
            return OwnTargetsTyped(
                way = if (perKg != null) OwnWay.PER_KG else OwnWay.SPLIT,
                percent = SplitTyped.of(first),
                perKg = (perKg ?: ProteinPerKg.FIRST).plain(),
                perKgFat = first.fat.toString(),
            )
        }
    }
}

/**
 * A split that adds up around [fat], protein as near [protein] as the limits let it be and carbohydrate the rest: what
 * protein per kilogram sends, every share a split may have.
 */
internal fun splitAround(protein: Int, fat: Int): MacroSplit {
    // A fat past its own limits leaves no room between the two: the API then says what is wrong with the fat.
    val kept = minOf(
        minOf(SplitLimits.PROTEIN.last, 100 - fat - SplitLimits.CARBS.first),
        maxOf(maxOf(SplitLimits.PROTEIN.first, 100 - fat - SplitLimits.CARBS.last), protein),
    )
    return MacroSplit(protein = kept, fat = fat, carbs = 100 - kept - fat)
}
