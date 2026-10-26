// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import net.whiteravens.dietapp.domain.CalorieQuestion
import net.whiteravens.dietapp.domain.KindLimit
import net.whiteravens.dietapp.domain.MacroSplit
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.ProfileFigures
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** The unit a body weight is typed and shown in; the API keeps kilograms. */
enum class WeightUnit(val code: String) {
    KG("kg"),
    LB("lb"),
    ;

    /** [kg] in this unit. */
    fun shown(kg: Double): Double = if (this == LB) kg / KG_PER_LB else kg

    /** [kg] in this unit to one decimal: what a field for it starts with. */
    fun shownToOne(kg: Double): Double = (shown(kg) * 10).roundToInt() / 10.0

    /** A weight typed in this unit, in kilograms to the hundredth, as the API takes it. */
    fun toKilograms(typed: Double): Double = ((if (this == LB) typed * KG_PER_LB else typed) * 100).roundToInt() / 100.0

    /** The least and the most a body weight may be in this unit, the least rounded up and the most down, so that neither is refused. */
    val bounds: ClosedFloatingPointRange<Double>
        get() = ceil(shown(ProfileBounds.WEIGHT_KG.start) * 10) / 10..floor(shown(ProfileBounds.WEIGHT_KG.endInclusive) * 10) / 10

    /** A weight typed in this unit, with a decimal comma as well as a point, in kilograms; null for one that is no number within [bounds]. */
    fun typedKg(typed: String): Double? =
        typed.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in bounds }?.let(::toKilograms)

    companion object {
        /** One pound in kilograms, as the pound is defined. */
        private const val KG_PER_LB = 0.45359237

        /** The countries that weigh people in pounds. */
        private val POUND_REGIONS = setOf("US", "LR", "MM")

        /** The account's unit, or where the phone is when the account names none. */
        fun of(account: String?, locale: Locale): WeightUnit = when (account) {
            "kg" -> KG
            "lb" -> LB
            else -> if (locale.country in POUND_REGIONS) LB else KG
        }
    }
}

/** The bounds the API holds a profile's figures to, and what a new profile starts with. */
object ProfileBounds {
    val AGE = 13..120
    val HEIGHT_CM = 100.0..250.0
    val WEIGHT_KG = 30.0..400.0
    val OWN_TARGET = 800..6000
    val MEAL_COUNT = 2..5
    val PROTEIN = 10..45
    val FAT = 15..75
    val CARBS = 5..70
    const val NAME_LENGTH = 80

    /** The paces of loss, slowest first: none at all, then the four in kilograms a week. */
    val PACES: List<String?> = listOf(null, "0.25", "0.5", "0.75", "1.0")
    val ACTIVITY_LEVELS = listOf("sedentary", "light", "moderate", "active", "very_active")

    /** The diets with a split of their own, and the custom one whose split the profile says. */
    val DIETS = listOf("balanced", "high_protein", "low_carb", "vegetarian", "vegan", "keto", "mediterranean", "custom")
    val ALLERGENS = listOf("gluten", "dairy", "eggs", "nuts", "peanuts", "soy", "fish", "shellfish", "sesame")
    val CUISINES = listOf("polish", "italian", "asian", "mediterranean", "middle_eastern", "mexican", "american", "indian")
    val REMINDER_CADENCES = listOf("off", "daily", "weekly")

    /** The split a profile that goes to the custom diet begins with: the balanced diet's. */
    val FIRST_SPLIT = MacroSplit(protein = 25, fat = 30, carbs = 45)

    val CONSECUTIVE_DAYS = 1..7
    val TIMES_PER_WEEK = 1..7

    /** The most meals of a day, and of seven days, with the same kind of protein: every meal of a day of five, every meal of a week. */
    val KIND_PER_DAY = 1..5
    val KIND_PER_WEEK = 1..35
    val PANTRY_COOL_DOWN = 0..20

    /** The kinds of protein a limit can be set for, in the order the API lists them. */
    val PROTEIN_KINDS = listOf("chicken", "turkey", "beef", "pork", "fish", "seafood", "eggs", "dairy", "legumes")

    /** The limits a new profile starts with, as the API sets them: none on a kind of protein. */
    val FIRST_LIMITS = PlanLimits(
        maxConsecutiveDaysSameMeal = 2,
        maxTimesPerWeekSameMeal = 3,
        maxMealsPerDaySameKind = null,
        maxMealsPerWeekSameKind = null,
        kindLimits = emptyList(),
        inventoryBiasResetEvery = 5,
    )
}

/** A field of the form that holds something the API would refuse. */
enum class ProfileField {
    NAME, AGE, HEIGHT, WEIGHT, TARGET_WEIGHT, OWN_TARGET, SPLIT,
    CONSECUTIVE_DAYS, TIMES_PER_WEEK, KIND_PER_DAY, KIND_PER_WEEK, KIND_LIMITS, PANTRY_COOL_DOWN,
}

/** The three shares of the custom diet's split, in per cent, as their fields hold them. */
data class SplitTyped(val protein: String, val fat: String, val carbs: String) {
    /** What the three add up to; a field still empty counts as nothing. */
    val sum: Int
        get() = listOf(protein, fat, carbs).sumOf { it.trim().toIntOrNull() ?: 0 }

    /** The split, when each share is a whole number in its bounds and the three add up to 100. */
    fun split(): MacroSplit? {
        val split = MacroSplit(
            protein = protein.trim().toIntOrNull() ?: return null,
            fat = fat.trim().toIntOrNull() ?: return null,
            carbs = carbs.trim().toIntOrNull() ?: return null,
        )
        return split.takeIf { it.holds() }
    }

    companion object {
        fun of(split: MacroSplit) = SplitTyped(split.protein.toString(), split.fat.toString(), split.carbs.toString())
    }
}

/**
 * The limits on how often a plan repeats itself, as their fields hold them: those on a kind of
 * protein empty for none.
 */
data class LimitsTyped(
    val consecutiveDays: String,
    val timesPerWeek: String,
    val kindPerDay: String,
    val kindPerWeek: String,
    val kindLimits: List<KindLimitTyped>,
    val pantryCoolDown: String,
) {
    companion object {
        fun of(limits: PlanLimits) = LimitsTyped(
            consecutiveDays = limits.maxConsecutiveDaysSameMeal.toString(),
            timesPerWeek = limits.maxTimesPerWeekSameMeal.toString(),
            kindPerDay = limits.maxMealsPerDaySameKind?.toString().orEmpty(),
            kindPerWeek = limits.maxMealsPerWeekSameKind?.toString().orEmpty(),
            kindLimits = limits.kindLimits.map(KindLimitTyped::of),
            pantryCoolDown = limits.inventoryBiasResetEvery.toString(),
        )
    }
}

/** A kind of protein held to numbers of its own, as the fields of its row hold them: an empty one is the profile's. */
data class KindLimitTyped(val kind: String, val perDay: String = "", val perWeek: String = "") {
    val dayHolds: Boolean
        get() = perDay.emptyOrWithin(ProfileBounds.KIND_PER_DAY)

    val weekHolds: Boolean
        get() = perWeek.emptyOrWithin(ProfileBounds.KIND_PER_WEEK)

    /** The limit the row holds, or null for one with neither number, which says nothing. */
    fun limit(): KindLimit? {
        val day = perDay.trim().toIntOrNull()
        val week = perWeek.trim().toIntOrNull()
        return if (day == null && week == null) null else KindLimit(kind, day, week)
    }

    companion object {
        fun of(limit: KindLimit) = KindLimitTyped(limit.kind, limit.perDay?.toString().orEmpty(), limit.perWeek?.toString().orEmpty())
    }
}

/**
 * A profile's form as it is typed: the numbers as their fields hold them, the weights in the unit
 * of the account. It begins with nobody's figures, so that no target is worked out from numbers
 * that were only standing there.
 *
 * A kept profile's form remembers what the profile had where the form shows it rounded or not at
 * all: its weights, which a field shows to one decimal in the account's unit and gives back
 * exactly while it still shows that, so that a weight that went through pounds and back does not
 * move by a hair with every save; and its split, which a profile on another diet keeps for when it
 * goes back.
 */
data class ProfileForm(
    val name: String = "",
    val age: String = "",
    val sex: String? = null,
    val height: String = "",
    val weight: String = "",
    val targetWeight: String = "",
    val activityLevel: String = "moderate",
    val weeklyLossTarget: String? = "0.5",
    val ownTarget: String = "",
    val dietType: String = "balanced",
    val mealCount: Int = 3,
    val split: SplitTyped = SplitTyped.of(ProfileBounds.FIRST_SPLIT),
    val allergens: Set<String> = emptySet(),
    val weightReminderCadence: String = "weekly",
    val limits: LimitsTyped = LimitsTyped.of(ProfileBounds.FIRST_LIMITS),
    val acknowledgeRisk: Boolean = false,
    val keptWeightKg: Double? = null,
    val keptTargetWeightKg: Double? = null,
    val keptSplit: MacroSplit? = null,
) {
    /** The fields that hold something the API would refuse; an empty one that may be empty is none. */
    fun problems(unit: WeightUnit): Set<ProfileField> = buildSet {
        if (name.isBlank() || name.trim().length > ProfileBounds.NAME_LENGTH) add(ProfileField.NAME)
        addAll(questionProblems(unit))
        if (targetWeight.isNotBlank() && weightKg(targetWeight, keptTargetWeightKg, unit)?.let { it in ProfileBounds.WEIGHT_KG } != true) {
            add(ProfileField.TARGET_WEIGHT)
        }
        if (!limits.consecutiveDays.within(ProfileBounds.CONSECUTIVE_DAYS)) add(ProfileField.CONSECUTIVE_DAYS)
        if (!limits.timesPerWeek.within(ProfileBounds.TIMES_PER_WEEK)) add(ProfileField.TIMES_PER_WEEK)
        if (!limits.kindPerDay.emptyOrWithin(ProfileBounds.KIND_PER_DAY)) add(ProfileField.KIND_PER_DAY)
        if (!limits.kindPerWeek.emptyOrWithin(ProfileBounds.KIND_PER_WEEK)) add(ProfileField.KIND_PER_WEEK)
        if (!limits.kindLimits.all { it.dayHolds && it.weekHolds }) add(ProfileField.KIND_LIMITS)
        if (!limits.pantryCoolDown.within(ProfileBounds.PANTRY_COOL_DOWN)) add(ProfileField.PANTRY_COOL_DOWN)
    }

    /** The refused fields among those a calorie target is worked out from. */
    private fun questionProblems(unit: WeightUnit): Set<ProfileField> = buildSet {
        if (!age.within(ProfileBounds.AGE)) add(ProfileField.AGE)
        if (height.number()?.let { it in ProfileBounds.HEIGHT_CM } != true) add(ProfileField.HEIGHT)
        if (weightKg(weight, keptWeightKg, unit)?.let { it in ProfileBounds.WEIGHT_KG } != true) add(ProfileField.WEIGHT)
        if (ownTarget.isNotBlank() && !ownTarget.within(ProfileBounds.OWN_TARGET)) add(ProfileField.OWN_TARGET)
        if (dietType == "custom" && split.split() == null) add(ProfileField.SPLIT)
    }

    /**
     * What a calorie target is worked out from, as the form holds it at this moment: null while one
     * of those figures is not one the API would take, so that the form asks only with figures it
     * could also save. The risk is said acknowledged only by a form that shows the box for it,
     * which it does where the API's answer says there is a risk ([riskShown]).
     */
    fun question(unit: WeightUnit, riskShown: Boolean = false): CalorieQuestion? {
        if (questionProblems(unit).isNotEmpty()) return null
        return CalorieQuestion(
            age = age.trim().toInt(),
            sex = sex,
            heightCm = height.number()!!,
            weightKg = weightKg(weight, keptWeightKg, unit)!!,
            activityLevel = activityLevel,
            dietType = dietType,
            customMacroSplit = if (dietType == "custom") split.split() else null,
            weeklyLossTarget = weeklyLossTarget,
            manualCalorieTarget = ownTarget.trim().toIntOrNull(),
            acknowledgeRisk = acknowledgeRisk && riskShown,
        )
    }

    /** The figures the form holds, as a profile keeps them, or null while one is refused. */
    fun figures(unit: WeightUnit): ProfileFigures? {
        val asked = question(unit) ?: return null
        if (problems(unit).isNotEmpty()) return null
        return ProfileFigures(
            name = name.trim(),
            age = asked.age,
            sex = sex,
            heightCm = asked.heightCm,
            weightKg = asked.weightKg,
            targetWeightKg = weightKg(targetWeight, keptTargetWeightKg, unit),
            activityLevel = activityLevel,
            dietType = dietType,
            weeklyLossTarget = weeklyLossTarget,
            manualCalorieTarget = asked.manualCalorieTarget,
            mealCount = mealCount,
            weightReminderCadence = weightReminderCadence,
            // The custom diet's split as the form shows it; on another diet the profile keeps the one it has.
            customMacroSplit = if (dietType == "custom") asked.customMacroSplit else keptSplit,
            allergens = ProfileBounds.ALLERGENS.filter { it in allergens },
        )
    }

    /** The limits the form holds, or null while one is refused. An exception with neither number is left out. */
    fun planLimits(): PlanLimits? {
        val typed = limits
        val kindsHold = typed.kindPerDay.emptyOrWithin(ProfileBounds.KIND_PER_DAY) &&
            typed.kindPerWeek.emptyOrWithin(ProfileBounds.KIND_PER_WEEK) &&
            typed.kindLimits.all { it.dayHolds && it.weekHolds }
        if (!kindsHold) return null
        val limits = PlanLimits(
            maxConsecutiveDaysSameMeal = typed.consecutiveDays.trim().toIntOrNull() ?: return null,
            maxTimesPerWeekSameMeal = typed.timesPerWeek.trim().toIntOrNull() ?: return null,
            maxMealsPerDaySameKind = typed.kindPerDay.trim().toIntOrNull(),
            maxMealsPerWeekSameKind = typed.kindPerWeek.trim().toIntOrNull(),
            kindLimits = typed.kindLimits.mapNotNull { it.limit() },
            inventoryBiasResetEvery = typed.pantryCoolDown.trim().toIntOrNull() ?: return null,
        )
        return limits.takeIf {
            it.maxConsecutiveDaysSameMeal in ProfileBounds.CONSECUTIVE_DAYS &&
                it.maxTimesPerWeekSameMeal in ProfileBounds.TIMES_PER_WEEK &&
                it.inventoryBiasResetEvery in ProfileBounds.PANTRY_COOL_DOWN
        }
    }

    companion object {
        /** The form of a kept profile, its weights in [unit] to one decimal. */
        fun of(kept: ProfileDetails, unit: WeightUnit): ProfileForm {
            val figures = kept.figures
            return ProfileForm(
                name = figures.name,
                age = figures.age.toString(),
                sex = figures.sex,
                height = figures.heightCm.plain(),
                weight = unit.shownToOne(figures.weightKg).plain(),
                targetWeight = figures.targetWeightKg?.let { unit.shownToOne(it).plain() }.orEmpty(),
                activityLevel = figures.activityLevel,
                weeklyLossTarget = figures.weeklyLossTarget,
                ownTarget = figures.manualCalorieTarget?.toString().orEmpty(),
                dietType = figures.dietType,
                mealCount = figures.mealCount,
                split = SplitTyped.of(figures.customMacroSplit ?: ProfileBounds.FIRST_SPLIT),
                allergens = figures.allergens.toSet(),
                weightReminderCadence = figures.weightReminderCadence,
                limits = LimitsTyped.of(kept.preferences.limits),
                acknowledgeRisk = kept.riskAcknowledged,
                keptWeightKg = figures.weightKg,
                keptTargetWeightKg = figures.targetWeightKg,
                keptSplit = figures.customMacroSplit,
            )
        }
    }
}

/** Whether the three shares are each within their bounds and add up to 100. */
fun MacroSplit.holds(): Boolean =
    protein in ProfileBounds.PROTEIN && fat in ProfileBounds.FAT && carbs in ProfileBounds.CARBS && protein + fat + carbs == 100

/**
 * A weight typed in [unit], in kilograms; null for a field that holds no number. While the field
 * still shows what the profile had, [kept], it gives back exactly that.
 */
private fun weightKg(typed: String, kept: Double?, unit: WeightUnit): Double? {
    val number = typed.number() ?: return null
    return if (kept != null && number == unit.shownToOne(kept)) kept else unit.toKilograms(number)
}

/** A number as it is typed, with a decimal comma as well as a point. */
private fun String.number(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** Whether the field holds a whole number within [range]. */
private fun String.within(range: IntRange): Boolean = trim().toIntOrNull()?.let { it in range } == true

/** Whether the field is empty or holds a whole number within [range]. */
private fun String.emptyOrWithin(range: IntRange): Boolean = isBlank() || within(range)

private fun Double.plain(): String = if (this % 1.0 == 0.0) roundToInt().toString() else toString()
