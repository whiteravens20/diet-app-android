// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import net.whiteravens.dietapp.domain.CalorieQuestion
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.KindLimit
import net.whiteravens.dietapp.domain.MacroSplit
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.ProfileFigures
import net.whiteravens.dietapp.ui.common.Bound
import java.util.Locale
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

    /** A weight typed in this unit, with a decimal comma as well as a point, in kilograms; null for what is no number. The API says what it takes. */
    fun typedKg(typed: String): Double? =
        typed.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::toKilograms)

    /** How a refusal of a weight says its bound: in this unit, to one decimal. */
    val refusalBound: Bound get() = Bound.Number(decimals = 1, unit = code, inUnit = ::shown)

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

/** What a profile's form offers to choose from, and what a new profile starts with. */
object ProfileChoices {
    val MEAL_COUNT = 2..5

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

/** A field of the form, by the path the API names it by when it refuses what it holds: [SPLIT] is the split as a whole. */
enum class ProfileField(val path: String) {
    NAME("name"),
    AGE("age"),
    HEIGHT("heightCm"),
    WEIGHT("weightKg"),
    TARGET_WEIGHT("targetWeightKg"),
    OWN_TARGET("manualCalorieTarget"),
    SPLIT("customMacroSplit"),
    SPLIT_PROTEIN("customMacroSplit.protein"),
    SPLIT_FAT("customMacroSplit.fat"),
    SPLIT_CARBS("customMacroSplit.carbs"),
    CONSECUTIVE_DAYS("preferences.maxConsecutiveDaysSameMeal"),
    TIMES_PER_WEEK("preferences.maxTimesPerWeekSameMeal"),
    KIND_PER_DAY("preferences.maxMealsPerDaySameKind"),
    KIND_PER_WEEK("preferences.maxMealsPerWeekSameKind"),
    PANTRY_COOL_DOWN("preferences.inventoryBiasResetEvery"),
    ;

    companion object {
        /** The fields a calorie target is worked out from, which the calorie preview is asked with. */
        val QUESTION = setOf(AGE, HEIGHT, WEIGHT, OWN_TARGET, SPLIT, SPLIT_PROTEIN, SPLIT_FAT, SPLIT_CARBS)
    }
}

/** A part of a kind of protein's limit, by the name the API gives it under the limit's place among those sent. */
enum class KindPart(val key: String) { DAY("perDay"), WEEK("perWeek") }

/** The three shares of the custom diet's split, in per cent, as their fields hold them. */
data class SplitTyped(val protein: String, val fat: String, val carbs: String) {
    /** What the three add up to; a field still empty counts as nothing. */
    val sum: Int
        get() = listOf(protein, fat, carbs).sumOf { it.trim().toIntOrNull() ?: 0 }

    /** The split, when each share is a whole number; what the API takes of it is the API's to say. */
    fun split(): MacroSplit? = MacroSplit(
        protein = protein.trim().toIntOrNull() ?: return null,
        fat = fat.trim().toIntOrNull() ?: return null,
        carbs = carbs.trim().toIntOrNull() ?: return null,
    )

    /** The shares that hold no whole number. */
    val notWhole: Set<ProfileField>
        get() = buildSet {
            if (protein.trim().toIntOrNull() == null) add(ProfileField.SPLIT_PROTEIN)
            if (fat.trim().toIntOrNull() == null) add(ProfileField.SPLIT_FAT)
            if (carbs.trim().toIntOrNull() == null) add(ProfileField.SPLIT_CARBS)
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
    /** The parts whose field holds what is no whole number. */
    val notWhole: Set<KindPart>
        get() = buildSet {
            if (!perDay.emptyOrWhole()) add(KindPart.DAY)
            if (!perWeek.emptyOrWhole()) add(KindPart.WEEK)
        }

    fun valueOf(part: KindPart): String = if (part == KindPart.DAY) perDay else perWeek

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
 *
 * Its numbers are numbers; what the API takes of them, and of the name, is the API's to say.
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
    val split: SplitTyped = SplitTyped.of(ProfileChoices.FIRST_SPLIT),
    val allergens: Set<String> = emptySet(),
    val weightReminderCadence: String = "weekly",
    val limits: LimitsTyped = LimitsTyped.of(ProfileChoices.FIRST_LIMITS),
    val acknowledgeRisk: Boolean = false,
    val keptWeightKg: Double? = null,
    val keptTargetWeightKg: Double? = null,
    val keptSplit: MacroSplit? = null,
) {
    /** The fields that hold no number where one is needed, or what is no whole number; an empty one that may be empty is none. */
    fun problems(unit: WeightUnit): Set<ProfileField> = buildSet {
        addAll(questionProblems(unit))
        if (targetWeight.isNotBlank() && weightKg(targetWeight, keptTargetWeightKg, unit) == null) add(ProfileField.TARGET_WEIGHT)
        if (limits.consecutiveDays.trim().toIntOrNull() == null) add(ProfileField.CONSECUTIVE_DAYS)
        if (limits.timesPerWeek.trim().toIntOrNull() == null) add(ProfileField.TIMES_PER_WEEK)
        if (!limits.kindPerDay.emptyOrWhole()) add(ProfileField.KIND_PER_DAY)
        if (!limits.kindPerWeek.emptyOrWhole()) add(ProfileField.KIND_PER_WEEK)
        if (limits.pantryCoolDown.trim().toIntOrNull() == null) add(ProfileField.PANTRY_COOL_DOWN)
    }

    /** The kinds of protein whose row holds what is no whole number, with the parts that do. */
    fun kindProblems(): Map<String, Set<KindPart>> =
        limits.kindLimits.associate { it.kind to it.notWhole }.filterValues { it.isNotEmpty() }

    /** Those among the fields a calorie target is worked out from that hold no number, or no whole number. */
    private fun questionProblems(unit: WeightUnit): Set<ProfileField> = buildSet {
        if (age.trim().toIntOrNull() == null) add(ProfileField.AGE)
        if (height.number() == null) add(ProfileField.HEIGHT)
        if (weightKg(weight, keptWeightKg, unit) == null) add(ProfileField.WEIGHT)
        if (!ownTarget.emptyOrWhole()) add(ProfileField.OWN_TARGET)
        if (dietType == "custom") addAll(split.notWhole)
    }

    /**
     * What a calorie target is worked out from, as the form holds it at this moment: null while one
     * of those fields holds no number, so that the form asks with the figures it would save, and
     * the API refuses what it does not take. The risk is said acknowledged only by a form that shows
     * the box for it, which it does where the API's answer says there is a risk ([riskShown]).
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

    /** The figures the form holds, as a profile keeps them, or null while a field holds no number where one is needed. */
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
            allergens = ProfileChoices.ALLERGENS.filter { it in allergens },
        )
    }

    /** The limits the form holds, or null while a field of them holds no whole number. An exception with neither number is left out. */
    fun planLimits(): PlanLimits? {
        val typed = limits
        if (!typed.kindPerDay.emptyOrWhole() || !typed.kindPerWeek.emptyOrWhole() || typed.kindLimits.any { it.notWhole.isNotEmpty() }) return null
        return PlanLimits(
            maxConsecutiveDaysSameMeal = typed.consecutiveDays.trim().toIntOrNull() ?: return null,
            maxTimesPerWeekSameMeal = typed.timesPerWeek.trim().toIntOrNull() ?: return null,
            maxMealsPerDaySameKind = typed.kindPerDay.trim().toIntOrNull(),
            maxMealsPerWeekSameKind = typed.kindPerWeek.trim().toIntOrNull(),
            kindLimits = typed.kindLimits.mapNotNull { it.limit() },
            inventoryBiasResetEvery = typed.pantryCoolDown.trim().toIntOrNull() ?: return null,
        )
    }

    /** What the field holds as typed: the split as a whole, or a part of it, or another field. */
    fun valueOf(field: ProfileField): Any = when (field) {
        ProfileField.NAME -> name
        ProfileField.AGE -> age
        ProfileField.HEIGHT -> height
        ProfileField.WEIGHT -> weight
        ProfileField.TARGET_WEIGHT -> targetWeight
        ProfileField.OWN_TARGET -> ownTarget
        ProfileField.SPLIT -> dietType to split
        ProfileField.SPLIT_PROTEIN -> split.protein
        ProfileField.SPLIT_FAT -> split.fat
        ProfileField.SPLIT_CARBS -> split.carbs
        ProfileField.CONSECUTIVE_DAYS -> limits.consecutiveDays
        ProfileField.TIMES_PER_WEEK -> limits.timesPerWeek
        ProfileField.KIND_PER_DAY -> limits.kindPerDay
        ProfileField.KIND_PER_WEEK -> limits.kindPerWeek
        ProfileField.PANTRY_COOL_DOWN -> limits.pantryCoolDown
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
                split = SplitTyped.of(figures.customMacroSplit ?: ProfileChoices.FIRST_SPLIT),
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

/**
 * The fields of a profile's form the API refused, by its answer: the form's own, and a kind of
 * protein's limits by the kind, from the limit's place among those the form sent. A path the form
 * has no field for is in [elsewhere].
 */
data class ProfileRefusals(
    val fields: Map<ProfileField, FieldRefusal> = emptyMap(),
    val kinds: Map<String, Map<KindPart, FieldRefusal>> = emptyMap(),
    val elsewhere: Set<String> = emptySet(),
) {
    /** These, with those among [of] as [answer] says them: an answer about some fields says nothing of the others. */
    fun with(answer: ProfileRefusals, of: Set<ProfileField>): ProfileRefusals =
        copy(fields = fields.filterKeys { it !in of } + answer.fields.filterKeys { it in of })

    /** What is left of them once the form is [now] as it was [before]: a field changed since is no longer refused. */
    fun after(before: ProfileForm, now: ProfileForm): ProfileRefusals = copy(
        fields = fields.filterKeys { field -> before.valueOf(field) == now.valueOf(field) },
        kinds = kinds.mapValues { (kind, parts) ->
            val then = before.limits.kindLimits.find { it.kind == kind }
            val row = now.limits.kindLimits.find { it.kind == kind }
            parts.filterKeys { part -> then != null && row != null && then.valueOf(part) == row.valueOf(part) }
        }.filterValues { it.isNotEmpty() },
    )

    companion object {
        private val KIND_PATH = Regex("""(?:preferences\.)?kindLimits\.(\d+)\.(\w+)""")

        /** The fields [refused] names, of [form] as it was sent. */
        fun of(refused: Map<String, FieldRefusal>, form: ProfileForm): ProfileRefusals {
            val sent = form.limits.kindLimits.mapNotNull { it.limit() }
            val fields = mutableMapOf<ProfileField, FieldRefusal>()
            val kinds = mutableMapOf<String, MutableMap<KindPart, FieldRefusal>>()
            val elsewhere = mutableSetOf<String>()
            for ((path, refusal) in refused) {
                val field = ProfileField.entries.firstOrNull { it.path == path }
                if (field != null) {
                    fields[field] = refusal
                    continue
                }
                val match = KIND_PATH.matchEntire(path)
                val kind = match?.groupValues?.get(1)?.toInt()?.let { sent.getOrNull(it)?.kind }
                val part = match?.groupValues?.get(2)?.let { key -> KindPart.entries.firstOrNull { it.key == key } }
                if (kind != null && part != null) kinds.getOrPut(kind) { mutableMapOf() }[part] = refusal else elsewhere += path
            }
            return ProfileRefusals(fields, kinds, elsewhere)
        }
    }
}

/**
 * A weight typed in [unit], in kilograms; null for a field that holds no number. While the field
 * still shows what the profile had, [kept], it gives back exactly that.
 */
private fun weightKg(typed: String, kept: Double?, unit: WeightUnit): Double? {
    val number = typed.number() ?: return null
    return if (kept != null && number == unit.shownToOne(kept)) kept else unit.toKilograms(number)
}

/** A number as it is typed, with a decimal comma as well as a point. */
private fun String.number(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Whether the field is empty or holds a whole number. */
private fun String.emptyOrWhole(): Boolean = isBlank() || trim().toIntOrNull() != null

private fun Double.plain(): String = if (this % 1.0 == 0.0) roundToInt().toString() else toString()
