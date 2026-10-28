// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.MacroSplit
import net.whiteravens.dietapp.domain.KindLimit
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.ProfileFigures
import net.whiteravens.dietapp.domain.ProfilePreferences
import net.whiteravens.dietapp.testing.refusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** A profile's form as it is typed, and the figures the API is given from it. */
class ProfileFormTest {
    private val filled = ProfileForm(name = "Ala", age = "30", height = "168", weight = "64.5")

    private fun kept(
        weightKg: Double = 64.35,
        targetWeightKg: Double? = 60.0,
        dietType: String = "balanced",
        split: MacroSplit? = null,
        limits: PlanLimits = ProfileChoices.FIRST_LIMITS,
        riskAcknowledged: Boolean = false,
    ) = ProfileDetails(
        id = "p",
        figures = ProfileFigures(
            name = "Ala", age = 30, sex = "female", heightCm = 168.0, weightKg = weightKg, targetWeightKg = targetWeightKg,
            activityLevel = "light", dietType = dietType, weeklyLossTarget = "0.5", manualCalorieTarget = null,
            mealCount = 4, weightReminderCadence = "daily", customMacroSplit = split, allergens = listOf("gluten"),
        ),
        preferences = ProfilePreferences(
            favoriteIngredientIds = emptyList(), excludedIngredientIds = emptyList(), dislikedIngredientIds = emptyList(),
            preferredCuisines = emptyList(), maxConsecutiveDaysSameMeal = limits.maxConsecutiveDaysSameMeal,
            maxTimesPerWeekSameMeal = limits.maxTimesPerWeekSameMeal, maxMealsPerDaySameKind = limits.maxMealsPerDaySameKind,
            maxMealsPerWeekSameKind = limits.maxMealsPerWeekSameKind, kindLimits = limits.kindLimits,
            inventoryBiasResetEvery = limits.inventoryBiasResetEvery,
        ),
        riskAcknowledged = riskAcknowledged,
    )

    @Test
    fun `an empty form holds no numbers where a profile needs them and asks nothing, while its name is the API's to refuse`() {
        val empty = ProfileForm()

        assertEquals(setOf(ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT), empty.problems(WeightUnit.KG))
        assertNull(empty.question(WeightUnit.KG))
        assertEquals(emptySet<ProfileField>(), filled.problems(WeightUnit.KG))
        assertEquals(emptySet<ProfileField>(), filled.copy(name = "").problems(WeightUnit.KG))
    }

    @Test
    fun `what is no number is the form's to say, and numbers past the API's bounds are asked about and sent for the API to refuse`() {
        assertEquals(setOf(ProfileField.AGE), filled.copy(age = "twelve").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.AGE), filled.copy(age = "12.5").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.HEIGHT), filled.copy(height = "tall").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.WEIGHT), filled.copy(weight = "").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.TARGET_WEIGHT), filled.copy(targetWeight = "less").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.OWN_TARGET), filled.copy(ownTarget = "1800,5").problems(WeightUnit.KG))
        assertEquals(emptySet<ProfileField>(), filled.copy(targetWeight = "", ownTarget = "").problems(WeightUnit.KG))

        val pastTheBounds = filled.copy(name = "x".repeat(81), age = "5", height = "300", weight = "10", targetWeight = "500", ownTarget = "100")
        assertEquals(emptySet<ProfileField>(), pastTheBounds.problems(WeightUnit.KG))
        assertEquals(5, pastTheBounds.question(WeightUnit.KG)!!.age)
        assertEquals(500.0, pastTheBounds.figures(WeightUnit.KG)!!.targetWeightKg!!, 0.0)
        // The custom diet's shares are whole numbers; whether they add up to a hundred is the API's to say.
        val split = filled.copy(dietType = "custom", split = SplitTyped("30", "30", "30"))
        assertEquals(MacroSplit(30, 30, 30), split.question(WeightUnit.KG)!!.customMacroSplit)
        assertEquals(setOf(ProfileField.SPLIT_FAT), split.copy(split = SplitTyped("30", "x", "40")).problems(WeightUnit.KG))
    }

    @Test
    fun `the limits on repeats are whole numbers, a form with one that is not keeps no limits, and numbers past the bounds are sent`() {
        fun limited(limits: LimitsTyped) = filled.copy(limits = limits)
        val first = LimitsTyped.of(ProfileChoices.FIRST_LIMITS)

        assertEquals(setOf(ProfileField.CONSECUTIVE_DAYS), limited(first.copy(consecutiveDays = "")).problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.TIMES_PER_WEEK), limited(first.copy(timesPerWeek = "x")).problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.KIND_PER_DAY), limited(first.copy(kindPerDay = "1.5")).problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.KIND_PER_WEEK), limited(first.copy(kindPerWeek = "two")).problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.PANTRY_COOL_DOWN), limited(first.copy(pantryCoolDown = "")).problems(WeightUnit.KG))
        val fish = limited(first.copy(kindLimits = listOf(KindLimitTyped("fish", perWeek = "x"))))
        assertEquals(mapOf("fish" to setOf(KindPart.WEEK)), fish.kindProblems())
        assertNull(fish.planLimits())
        assertNull(limited(first.copy(timesPerWeek = "")).planLimits())
        assertEquals(PlanLimits(0, 8, 6, 0, emptyList(), 21), limited(LimitsTyped("0", "8", "6", "0", emptyList(), "21")).planLimits())
    }

    @Test
    fun `the API's refusal names the form's fields, a kind of protein's by the kind's place among those sent, until each is changed`() {
        // As the API's answer had it: no name, 200 years, a weight of 500 kg to reach, and every limit past its bounds.
        val refused = filled.copy(
            name = "",
            age = "200",
            targetWeight = "500",
            limits = LimitsTyped("9", "0", "", "", listOf(KindLimitTyped("dairy"), KindLimitTyped("eggs", "9", "50")), "25"),
        )
        val refusals = ProfileRefusals.of(refusal("profile-refused").fieldRefusals, refused)

        assertEquals(
            mapOf(
                ProfileField.NAME to FieldRefusal.TooSmall(1.0, inclusive = true),
                ProfileField.AGE to FieldRefusal.TooBig(120.0, inclusive = true),
                ProfileField.TARGET_WEIGHT to FieldRefusal.TooBig(400.0, inclusive = true),
                ProfileField.CONSECUTIVE_DAYS to FieldRefusal.TooBig(7.0, inclusive = true),
                ProfileField.TIMES_PER_WEEK to FieldRefusal.TooSmall(1.0, inclusive = true),
                ProfileField.PANTRY_COOL_DOWN to FieldRefusal.TooBig(20.0, inclusive = true),
            ),
            refusals.fields,
        )
        // A row with neither number was not sent: the first limit sent is the eggs'.
        assertEquals(
            mapOf("eggs" to mapOf(KindPart.DAY to FieldRefusal.TooBig(5.0, inclusive = true), KindPart.WEEK to FieldRefusal.TooBig(35.0, inclusive = true))),
            refusals.kinds,
        )
        assertEquals(emptySet<String>(), refusals.elsewhere)

        val changed = refused.copy(age = "30", limits = refused.limits.copy(kindLimits = listOf(KindLimitTyped("dairy"), KindLimitTyped("eggs", "2", "50"))))
        val left = refusals.after(refused, changed)
        assertFalse(ProfileField.AGE in left.fields)
        assertTrue(ProfileField.NAME in left.fields)
        assertEquals(mapOf("eggs" to setOf(KindPart.WEEK)), left.kinds.mapValues { it.value.keys })
    }

    @Test
    fun `the calorie preview's refusal names the fields a target is worked out from, and the split as a whole where its shares do not add up`() {
        val figures = filled.copy(age = "5", height = "300", weight = "10", ownTarget = "100")
        val preview = ProfileRefusals.of(refusal("calorie-preview-refused").fieldRefusals, figures)
        val shares = ProfileRefusals.of(refusal("calorie-preview-split-refused").fieldRefusals, filled.copy(dietType = "custom", split = SplitTyped("5", "80", "15")))
        val sum = ProfileRefusals.of(refusal("calorie-preview-split-sum-refused").fieldRefusals, filled.copy(dietType = "custom", split = SplitTyped("30", "30", "30")))

        assertEquals(setOf(ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT, ProfileField.OWN_TARGET), preview.fields.keys)
        assertEquals(FieldRefusal.TooSmall(30.0, inclusive = true), preview.fields[ProfileField.WEIGHT])
        assertEquals(
            mapOf(ProfileField.SPLIT_PROTEIN to FieldRefusal.TooSmall(10.0, inclusive = true), ProfileField.SPLIT_FAT to FieldRefusal.TooBig(75.0, inclusive = true)),
            shares.fields,
        )
        assertEquals(mapOf(ProfileField.SPLIT to FieldRefusal.Other), sum.fields)
        // A preview says nothing of the fields it is not asked with: those a save refused stay.
        val saved = ProfileRefusals(fields = mapOf(ProfileField.NAME to FieldRefusal.TooBig(80.0, inclusive = true), ProfileField.AGE to FieldRefusal.TooBig(120.0, inclusive = true)))
        assertEquals(setOf(ProfileField.NAME, ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT, ProfileField.OWN_TARGET), saved.with(preview, ProfileField.QUESTION).fields.keys)
        assertEquals(setOf(ProfileField.NAME), saved.with(ProfileRefusals(), ProfileField.QUESTION).fields.keys)
    }

    @Test
    fun `the limits on a kind of protein are none while their fields are empty, and an exception gives what it holds`() {
        val first = LimitsTyped.of(ProfileChoices.FIRST_LIMITS)
        assertEquals("", first.kindPerDay)
        assertEquals(ProfileChoices.FIRST_LIMITS, filled.copy(limits = first).planLimits())

        val exceptions = first.copy(
            kindPerWeek = "7",
            kindLimits = listOf(
                KindLimitTyped("fish", perDay = "1"),
                KindLimitTyped("eggs", perDay = " ", perWeek = "3"),
                // A row with neither number says nothing, and is left out.
                KindLimitTyped("dairy"),
            ),
        )
        val limits = filled.copy(limits = exceptions).planLimits()!!

        assertNull(limits.maxMealsPerDaySameKind)
        assertEquals(7, limits.maxMealsPerWeekSameKind)
        assertEquals(listOf(KindLimit("fish", 1, null), KindLimit("eggs", null, 3)), limits.kindLimits)
        assertEquals(listOf(KindLimitTyped("fish", "1", ""), KindLimitTyped("eggs", "", "3")), LimitsTyped.of(limits).kindLimits)
    }

    @Test
    fun `a custom diet's shares are whole numbers sent with that diet alone, and what the API takes of them is its to say`() {
        val custom = filled.copy(dietType = "custom")

        assertEquals(setOf(ProfileField.SPLIT_PROTEIN), custom.copy(split = SplitTyped("", "30", "45")).problems(WeightUnit.KG))
        assertEquals(MacroSplit(25, 30, 45), custom.figures(WeightUnit.KG)!!.customMacroSplit)
        // Shares past their bounds, or that add up to less than a hundred, are sent for the API to refuse.
        assertEquals(MacroSplit(50, 25, 25), custom.copy(split = SplitTyped("50", "25", "25")).figures(WeightUnit.KG)!!.customMacroSplit)
        assertEquals(MacroSplit(30, 30, 30), custom.copy(split = SplitTyped("30", "30", "30")).figures(WeightUnit.KG)!!.customMacroSplit)
        assertNull(filled.copy(split = SplitTyped("30", "30", "30")).figures(WeightUnit.KG)!!.customMacroSplit)
    }

    @Test
    fun `what the shares add up to counts a field still empty as nothing`() {
        assertEquals(75, SplitTyped("", "30", "45").sum)
        assertEquals(100, SplitTyped("25", " 30", "45 ").sum)
    }

    @Test
    fun `a profile on another diet keeps the split it has for when it goes back to the custom one`() {
        val form = ProfileForm.of(kept(dietType = "balanced", split = MacroSplit(40, 30, 30)), WeightUnit.KG)

        assertEquals(MacroSplit(40, 30, 30), form.figures(WeightUnit.KG)!!.customMacroSplit)
        assertNull(form.question(WeightUnit.KG)!!.customMacroSplit)
        assertEquals(SplitTyped("40", "30", "30"), form.split)
    }

    @Test
    fun `a weight typed in pounds is kept in kilograms to the hundredth, and a decimal comma is a decimal`() {
        val figures = filled.copy(weight = "150", height = "172,5").figures(WeightUnit.LB)!!

        assertEquals(68.04, figures.weightKg, 0.0)
        assertEquals(172.5, figures.heightCm, 0.0)
        // 65 lb is under the least the API takes: it is sent, in kilograms, for the API to refuse.
        assertEquals(29.48, filled.copy(weight = "65").figures(WeightUnit.LB)!!.weightKg, 0.0)
    }

    @Test
    fun `a kept profile's weights are shown in the account's unit to one decimal and given back exactly while unchanged`() {
        val form = ProfileForm.of(kept(weightKg = 64.35, targetWeightKg = 60.0), WeightUnit.LB)

        assertEquals("141.9", form.weight)
        assertEquals("132.3", form.targetWeight)
        // Through pounds and back the weight would move by a hair with every save; untouched, it does not.
        assertEquals(64.35, form.figures(WeightUnit.LB)!!.weightKg, 0.0)
        assertEquals(60.0, form.figures(WeightUnit.LB)!!.targetWeightKg!!, 0.0)
        assertEquals(64.41, form.copy(weight = "142").figures(WeightUnit.LB)!!.weightKg, 0.0)
    }

    @Test
    fun `a kept profile's form holds its choices, limits and whether it acknowledged the risk`() {
        val limits = PlanLimits(1, 2, null, 4, listOf(KindLimit("eggs", 1, null)), 0)
        val form = ProfileForm.of(kept(limits = limits, riskAcknowledged = true), WeightUnit.KG)

        assertEquals("female", form.sex)
        assertEquals("light", form.activityLevel)
        assertEquals(4, form.mealCount)
        assertEquals("daily", form.weightReminderCadence)
        assertEquals(setOf("gluten"), form.allergens)
        assertEquals(limits, form.planLimits())
        assertTrue(form.acknowledgeRisk)
    }

    @Test
    fun `the question of what figures come to leaves out the name, the target weight and the limits, and says the risk acknowledged only where the box shows`() {
        val untitled = filled.copy(name = "", targetWeight = "heavy", limits = LimitsTyped("x", "0", "0", "0", emptyList(), "99"))

        val asked = untitled.question(WeightUnit.KG)
        assertNotNull(asked)
        assertEquals(30, asked!!.age)
        assertEquals(64.5, asked.weightKg, 0.0)
        assertNull(untitled.figures(WeightUnit.KG))
        assertNull(untitled.planLimits())
        // Ticked while the API says of no risk, the box is not on the form: nothing is acknowledged.
        assertFalse(filled.copy(age = "16", acknowledgeRisk = true).question(WeightUnit.KG)!!.acknowledgeRisk)
        assertTrue(filled.copy(age = "16", acknowledgeRisk = true).question(WeightUnit.KG, riskShown = true)!!.acknowledgeRisk)
        assertFalse(filled.copy(age = "16").question(WeightUnit.KG, riskShown = true)!!.acknowledgeRisk)
    }

    @Test
    fun `the unit is the account's, or where the phone is when the account names none`() {
        assertEquals(WeightUnit.LB, WeightUnit.of("lb", Locale.forLanguageTag("pl-PL")))
        assertEquals(WeightUnit.KG, WeightUnit.of("kg", Locale.US))
        assertEquals(WeightUnit.LB, WeightUnit.of(null, Locale.US))
        assertEquals(WeightUnit.KG, WeightUnit.of(null, Locale.forLanguageTag("pl-PL")))
        assertEquals(WeightUnit.KG, WeightUnit.of(null, Locale.ENGLISH))
    }

    @Test
    fun `a weight typed in a unit is read with a comma or a point, in kilograms to the hundredth, whatever its size, and what is no number is none`() {
        assertEquals(80.5, WeightUnit.KG.typedKg(" 80,5 ")!!, 0.0)
        assertEquals(81.65, WeightUnit.LB.typedKg("180")!!, 0.0)
        assertEquals(30.03, WeightUnit.LB.typedKg("66.2")!!, 0.0)
        // The API says what it takes, and refuses the rest with its bound.
        assertEquals(29.98, WeightUnit.LB.typedKg("66.1")!!, 0.0)
        assertEquals(400.1, WeightUnit.KG.typedKg("400.1")!!, 0.0)
        assertNull(WeightUnit.KG.typedKg("eighty"))
        assertNull(WeightUnit.KG.typedKg("NaN"))
        assertNull(WeightUnit.KG.typedKg("Infinity"))
    }
}
