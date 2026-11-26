// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.MacroSplit
import net.whiteravens.dietapp.domain.KindLimit
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.ProfileFigures
import net.whiteravens.dietapp.domain.ProfilePreferences
import net.whiteravens.dietapp.domain.LabelLimit
import net.whiteravens.dietapp.domain.LabelLimits
import net.whiteravens.dietapp.testing.refusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
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
        perKg: Double? = null,
        limits: PlanLimits = ProfileChoices.FIRST_LIMITS,
        labelLimits: LabelLimits = LabelLimits(),
        waterTargetMl: Int? = 1500,
        waterTracking: Boolean = true,
    ) = ProfileDetails(
        id = "p",
        figures = ProfileFigures(
            name = "Ala", age = 30, sex = "female", heightCm = 168.0, weightKg = weightKg, targetWeightKg = targetWeightKg,
            activityLevel = "light", dietType = dietType, weeklyLossTarget = "0.5", manualCalorieTarget = null,
            mealCount = 4, weightReminderCadence = "daily", waterTargetMl = waterTargetMl, waterTracking = waterTracking,
            cookingReminders = false, waterReminders = true, customMacroSplit = split, allergens = listOf("gluten"),
            customProteinPerKg = perKg,
        ),
        preferences = ProfilePreferences(
            favoriteIngredientIds = emptyList(), excludedIngredientIds = emptyList(), dislikedIngredientIds = emptyList(),
            preferredCuisines = emptyList(), maxConsecutiveDaysSameMeal = limits.maxConsecutiveDaysSameMeal,
            maxTimesPerWeekSameMeal = limits.maxTimesPerWeekSameMeal, maxMealsPerDaySameKind = limits.maxMealsPerDaySameKind,
            maxMealsPerWeekSameKind = limits.maxMealsPerWeekSameKind, kindLimits = limits.kindLimits,
            inventoryBiasResetEvery = limits.inventoryBiasResetEvery, labelLimits = labelLimits,
        ),
    )

    @Test
    fun `a figure that may be left empty changes its part only when its value changes, however the value was boxed`() {
        // Each number read here is a box of its own, as a number the form reads and one the API answered are: a
        // comparison of the boxes would find a change in every one of them.
        val before = kept().figures.copy(
            targetWeightKg = "72.5".toDouble(), manualCalorieTarget = "2100".toInt(), waterTargetMl = "1500".toInt(),
            dietType = "custom", customMacroSplit = MacroSplit(protein = 25, fat = 30, carbs = 45), customProteinPerKg = "1.6".toDouble(),
        )
        val after = before.copy(
            targetWeightKg = "72.5".toDouble(), manualCalorieTarget = "2100".toInt(), waterTargetMl = "1500".toInt(),
            customProteinPerKg = "1.6".toDouble(),
        )
        assertNotSame(before.targetWeightKg, after.targetWeightKg)
        assertNotSame(before.manualCalorieTarget, after.manualCalorieTarget)
        assertNotSame(before.customProteinPerKg, after.customProteinPerKg)

        assertEquals(emptyList<Set<String>>(), ProfileFormViewModel.partsChanged(before, after))
        assertEquals(listOf(setOf("targetWeightKg")), ProfileFormViewModel.partsChanged(before, after.copy(targetWeightKg = 70.0)))
        assertEquals(
            listOf(setOf("customMacroSplit", "customProteinPerKg")),
            ProfileFormViewModel.partsChanged(before, after.copy(customProteinPerKg = 1.8)),
        )
    }

    @Test
    fun `the limits from labels start turned off at the values they start from, and a kept profile's are those it set`() {
        assertTrue(ProfileForm().labels.values.none { it.on })
        assertEquals(LabelLimits(), filled.labelLimits())
        assertEquals("5", filled.labels.getValue(LabelLimit.SALT_AT_MOST).grams)
        assertEquals("90", filled.labels.getValue(LabelLimit.SUGARS_AT_MOST).grams)

        val form = ProfileForm.of(kept(labelLimits = LabelLimits(saltAtMost = 4.5, fiberAtLeast = 30.0)), WeightUnit.KG)
        assertEquals(LabelLimitTyped(on = true, grams = "4.5"), form.labels.getValue(LabelLimit.SALT_AT_MOST))
        assertEquals(LabelLimitTyped(on = true, grams = "30"), form.labels.getValue(LabelLimit.FIBER_AT_LEAST))
        assertEquals(LabelLimitTyped(on = false, grams = "20"), form.labels.getValue(LabelLimit.SATURATED_FAT_AT_MOST))
        assertEquals(LabelLimits(saltAtMost = 4.5, fiberAtLeast = 30.0), form.labelLimits())
    }

    @Test
    fun `a limit from labels turned on takes grams with a decimal comma, holds no number as a problem, and one turned off is sent as none`() {
        fun with(limit: LabelLimit, typed: LabelLimitTyped) = filled.copy(labels = filled.labels + (limit to typed))

        assertEquals(LabelLimits(saltAtMost = 6.5), with(LabelLimit.SALT_AT_MOST, LabelLimitTyped(on = true, grams = "6,5")).labelLimits())
        val empty = with(LabelLimit.SUGARS_AT_MOST, LabelLimitTyped(on = true, grams = " "))
        assertEquals(setOf(ProfileField.LABEL_SUGARS), empty.problems(WeightUnit.KG))
        assertNull(empty.labelLimits())
        assertEquals(LabelLimits(), with(LabelLimit.SUGARS_AT_MOST, LabelLimitTyped(on = false, grams = "much")).labelLimits())
        assertEquals(emptySet<ProfileField>(), with(LabelLimit.SUGARS_AT_MOST, LabelLimitTyped(on = false, grams = "much")).problems(WeightUnit.KG))
        // What the API takes of a number is the API's to say, by the field's path.
        assertEquals(LabelLimits(fiberAtLeast = 500.0), with(LabelLimit.FIBER_AT_LEAST, LabelLimitTyped(on = true, grams = "500")).labelLimits())
        assertEquals(ProfileField.LABEL_FIBER, ProfileField.entries.first { it.path == "preferences.labelLimits.fiberAtLeast" })
    }

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
        val split = filled.copy(dietType = "custom", own = OwnTargetsTyped(percent = SplitTyped("30", "30", "30")))
        assertEquals(MacroSplit(30, 30, 30), split.question(WeightUnit.KG)!!.customMacroSplit)
        assertEquals(setOf(ProfileField.SPLIT_FAT), split.copy(own = OwnTargetsTyped(percent = SplitTyped("30", "x", "40"))).problems(WeightUnit.KG))
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
        val shares = ProfileRefusals.of(refusal("calorie-preview-split-refused").fieldRefusals, filled.copy(dietType = "custom", own = OwnTargetsTyped(percent = SplitTyped("5", "80", "15"))))
        val sum = ProfileRefusals.of(refusal("calorie-preview-split-sum-refused").fieldRefusals, filled.copy(dietType = "custom", own = OwnTargetsTyped(percent = SplitTyped("30", "30", "30"))))

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

        assertEquals(setOf(ProfileField.SPLIT_PROTEIN), custom.copy(own = OwnTargetsTyped(percent = SplitTyped("", "30", "45"))).problems(WeightUnit.KG))
        assertEquals(MacroSplit(25, 30, 45), custom.figures(WeightUnit.KG)!!.customMacroSplit)
        // Shares past their bounds, or that add up to less than a hundred, are sent for the API to refuse.
        assertEquals(MacroSplit(50, 25, 25), custom.copy(own = OwnTargetsTyped(percent = SplitTyped("50", "25", "25"))).figures(WeightUnit.KG)!!.customMacroSplit)
        assertEquals(MacroSplit(30, 30, 30), custom.copy(own = OwnTargetsTyped(percent = SplitTyped("30", "30", "30"))).figures(WeightUnit.KG)!!.customMacroSplit)
        assertNull(filled.copy(own = OwnTargetsTyped(percent = SplitTyped("30", "30", "30"))).figures(WeightUnit.KG)!!.customMacroSplit)
    }

    @Test
    fun `protein per kilogram is worked out only from figures inside its fields' bounds, by tenths of a gram`() {
        val typed = OwnTargetsTyped(way = OwnWay.PER_KG, perKg = "1,6", perKgFat = "30")
        assertNotNull(typed.perKgAt(2298, 80.0))
        for (perKg in listOf("0,5", "3,5", "-1", "", "1,65", "x")) {
            assertFalse(perKg, typed.copy(perKg = perKg).perKgTypedFits())
            assertNull(perKg, typed.copy(perKg = perKg).perKgAt(2298, 80.0))
        }
        for (fat in listOf("0", "95", "", "30,5")) assertNull(fat, typed.copy(perKgFat = fat).perKgAt(2298, 80.0))
        // A form that has no weight yet waits for it with good figures.
        assertTrue(typed.perKgTypedFits())
        assertNull(typed.perKgAt(2298, null))
    }

    @Test
    fun `protein per kilogram is sent with a split around fat's share, which the API takes for fat alone`() {
        val custom = filled.copy(dietType = "custom", own = OwnTargetsTyped(way = OwnWay.PER_KG, perKg = "1,8", perKgFat = "35"))

        val asked = custom.question(WeightUnit.KG)!!
        assertEquals(1.8, asked.customProteinPerKg!!, 0.0)
        assertEquals(MacroSplit(25, 35, 40), asked.customMacroSplit)
        assertEquals(1.8, custom.figures(WeightUnit.KG)!!.customProteinPerKg!!, 0.0)
        // A fat that leaves protein no room is sent for the API to refuse, and the form does not fail on it.
        assertEquals(90, custom.copy(own = custom.own.copy(perKgFat = "90")).question(WeightUnit.KG)!!.customMacroSplit!!.fat)
        assertEquals(setOf(ProfileField.PER_KG), custom.copy(own = custom.own.copy(perKg = "dużo")).problems(WeightUnit.KG))
        // The split's way sends no protein per kilogram, and another diet sends none of the targets.
        assertNull(custom.copy(own = custom.own.copy(way = OwnWay.SPLIT)).question(WeightUnit.KG)!!.customProteinPerKg)
        assertNull(custom.copy(dietType = "balanced").question(WeightUnit.KG)!!.customProteinPerKg)
    }

    @Test
    fun `protein per kilogram begins with the fat of the split as it is typed, in per cent or in grams`() {
        val typed = OwnTargetsTyped(percent = SplitTyped("30", "35", "35"))

        assertEquals("35", typed.inWay(OwnWay.PER_KG).perKgFat)
        assertEquals("29", typed.copy(unit = SplitUnit.GRAMS, grams = SplitTyped("150", "74", "249")).inWay(OwnWay.PER_KG).perKgFat)
        // A split still being typed leaves the fat as it was.
        assertEquals("30", OwnTargetsTyped(percent = SplitTyped("30", "", "35")).inWay(OwnWay.PER_KG).perKgFat)
        assertEquals(OwnWay.SPLIT, typed.inWay(OwnWay.PER_KG).inWay(OwnWay.SPLIT).way)
    }

    @Test
    fun `a kept profile's protein per kilogram stays as it is while neither it nor the fat changes, whatever its split comes to`() {
        // At 64.35 kg the API says the split comes to 21, 30 and 49 %: the form, which holds 21 and the fat, changes nothing.
        val details = kept(dietType = "custom", split = MacroSplit(21, 30, 49), perKg = 1.6)
        val form = ProfileForm.of(details, WeightUnit.KG)
        assertEquals(OwnWay.PER_KG, form.own.way)
        assertEquals(details.figures, form.keepable(details.figures, WeightUnit.KG, target = 2000))
        // The split the API answers moves with the weight; the profile's targets do not.
        val heavier = details.figures.copy(customMacroSplit = MacroSplit(24, 30, 46))
        assertEquals(heavier, form.keepable(heavier, WeightUnit.KG, target = 2000))

        val more = form.copy(own = form.own.copy(perKg = "2")).keepable(details.figures, WeightUnit.KG, target = 2000)
        assertEquals(2.0, more.customProteinPerKg!!, 0.0)
        assertEquals(30, more.customMacroSplit!!.fat)
    }

    @Test
    fun `targets wrong at the target are not kept, and the form names the field`() {
        val details = kept(dietType = "custom", split = MacroSplit(25, 30, 45))
        val form = ProfileForm.of(details, WeightUnit.KG)

        // 3 g a kilogram of 64.35 kg is 772 kcal: 51 % of 1500, past the 45 % of protein.
        val held = form.copy(own = form.own.copy(way = OwnWay.PER_KG, perKg = "3"))
        assertEquals(setOf(ProfileField.PER_KG), held.notKept(WeightUnit.KG, target = 1500))
        assertEquals(details.figures, held.keepable(details.figures, WeightUnit.KG, target = 1500))
        // Without a target the form cannot say, and sends it for the API to judge.
        assertEquals(3.0, held.keepable(details.figures, WeightUnit.KG).customProteinPerKg!!, 0.0)
        assertEquals(emptySet<ProfileField>(), held.notKept(WeightUnit.KG, target = 2600))

        val grams = form.copy(own = form.own.copy(unit = SplitUnit.GRAMS, grams = SplitTyped("30", "74", "249")))
        assertEquals(setOf(ProfileField.SPLIT), grams.notKept(WeightUnit.KG, target = 2000))
        assertEquals(details.figures, grams.keepable(details.figures, WeightUnit.KG, target = 2000))
        val sum = form.copy(own = form.own.copy(percent = SplitTyped("30", "30", "30")))
        assertEquals(setOf(ProfileField.SPLIT), sum.notKept(WeightUnit.KG))
    }

    @Test
    fun `grams begin at what the split comes to at the target, are kept as the shares of their energy, and go back as them`() {
        val form = filled.copy(dietType = "custom")

        val grams = form.own.inUnit(SplitUnit.GRAMS, 2209)
        assertEquals(SplitTyped("138", "74", "249"), grams.grams)
        val typed = grams.copy(grams = SplitTyped("150", "74", "249"))
        assertEquals(MacroSplit(27, 29, 44), form.copy(own = typed).figures(WeightUnit.KG)!!.customMacroSplit)
        assertEquals(SplitTyped("27", "29", "44"), typed.inUnit(SplitUnit.PERCENT, 2209).percent)
        // Grams past a limit leave the shares in per cent as they were.
        val past = grams.copy(grams = SplitTyped("30", "74", "249"))
        assertEquals(OwnFault.LIMITS, past.fault(2209, 64.5))
        assertEquals(form.own.percent, past.inUnit(SplitUnit.PERCENT, 2209).percent)
        // Without a target the grams are left to be typed.
        assertEquals(SplitTyped("", "", ""), form.own.inUnit(SplitUnit.GRAMS, null).grams)
    }

    @Test
    fun `protein per kilogram goes with the split where either has it, so that a split sent alone is the split's way`() {
        val split = kept(dietType = "custom", split = MacroSplit(25, 30, 45)).figures
        val perKg = split.copy(customProteinPerKg = 1.6)

        assertEquals(listOf(setOf("customMacroSplit")), ProfileFormViewModel.partsChanged(split, split.copy(customMacroSplit = MacroSplit(30, 30, 40))))
        assertEquals(listOf(setOf("customMacroSplit", "customProteinPerKg")), ProfileFormViewModel.partsChanged(split, perKg))
        assertEquals(listOf(setOf("customMacroSplit", "customProteinPerKg")), ProfileFormViewModel.partsChanged(perKg, split))
        // Back to the custom diet with the targets it kept.
        assertEquals(
            listOf(setOf("dietType", "customMacroSplit", "customProteinPerKg")),
            ProfileFormViewModel.partsChanged(perKg.copy(dietType = "balanced"), perKg),
        )
        assertEquals(listOf(setOf("dietType", "customMacroSplit")), ProfileFormViewModel.partsChanged(split.copy(dietType = "balanced"), split))
    }

    @Test
    fun `the way the targets are set is kept the moment it is chosen, and the unit they are typed in is not`() {
        assertTrue(filled.choseOtherwise(filled.copy(own = filled.own.copy(way = OwnWay.PER_KG))))
        assertFalse(filled.choseOtherwise(filled.copy(own = filled.own.inUnit(SplitUnit.GRAMS, 2000))))
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
        assertEquals(SplitTyped("40", "30", "30"), form.own.percent)
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
    fun `a kept profile's form holds its choices and limits`() {
        val limits = PlanLimits(1, 2, null, 4, listOf(KindLimit("eggs", 1, null)), 0)
        val form = ProfileForm.of(kept(limits = limits), WeightUnit.KG)

        assertEquals("female", form.sex)
        assertEquals("light", form.activityLevel)
        assertEquals(4, form.mealCount)
        assertEquals("daily", form.weightReminderCadence)
        assertEquals("1500", form.waterTarget)
        assertFalse(form.cookingReminders)
        assertTrue(form.waterReminders)
        assertEquals(setOf("gluten"), form.allergens)
        assertEquals(limits, form.planLimits())
        // A profile with no water target shows the field empty, and gives none back.
        val dry = ProfileForm.of(kept(waterTargetMl = null), WeightUnit.KG)
        assertEquals("", dry.waterTarget)
        assertEquals(null, dry.figures(WeightUnit.KG)!!.waterTargetMl)
    }

    @Test
    fun `a new profile aims at the API's 2 l a day, reminded to cook ahead and not to drink, and the water target is the form's to read as a whole number`() {
        val figures = filled.figures(WeightUnit.KG)!!
        assertEquals(2000, figures.waterTargetMl)
        assertTrue(figures.cookingReminders)
        assertFalse(figures.waterReminders)

        assertEquals(null, filled.copy(waterTarget = " ").figures(WeightUnit.KG)!!.waterTargetMl)
        assertTrue(ProfileField.WATER_TARGET in filled.copy(waterTarget = "2,5").problems(WeightUnit.KG))
        assertEquals(null, filled.copy(waterTarget = "2,5").figures(WeightUnit.KG))
        // Past the API's bounds is the API's to refuse, by the field's own name.
        val past = filled.copy(waterTarget = "20000")
        assertEquals(20000, past.figures(WeightUnit.KG)!!.waterTargetMl)
        assertEquals(
            mapOf(ProfileField.WATER_TARGET to FieldRefusal.TooBig(10000.0, inclusive = true)),
            ProfileRefusals.of(refusal("profile-water-target-refused").fieldRefusals, past).fields,
        )
    }

    @Test
    fun `a profile tracks water to begin with, and one that stops keeps the target and the reminder it had, whatever the hidden field holds`() {
        assertTrue(filled.waterTracking)
        assertTrue(filled.figures(WeightUnit.KG)!!.waterTracking)
        assertTrue(ProfileForm.of(kept(), WeightUnit.KG).waterTracking)
        assertFalse(ProfileForm.of(kept(waterTracking = false), WeightUnit.KG).waterTracking)

        // Typed wrong and unticked out of sight: nothing holds the form back, and the profile keeps its 1.5 l and its reminder.
        val stopped = ProfileForm.of(kept(), WeightUnit.KG).copy(waterTarget = "2,5", waterReminders = false, waterTracking = false)
        assertEquals(emptySet<ProfileField>(), stopped.problems(WeightUnit.KG))
        val figures = stopped.figures(WeightUnit.KG)!!
        assertFalse(figures.waterTracking)
        assertEquals(1500, figures.waterTargetMl)
        assertTrue(figures.waterReminders)
        // A kept profile with no target keeps none.
        assertEquals(null, ProfileForm.of(kept(waterTargetMl = null), WeightUnit.KG).copy(waterTarget = "3000", waterTracking = false).figures(WeightUnit.KG)!!.waterTargetMl)
        // A new profile that does not track water starts at what a new one starts with.
        val fresh = filled.copy(waterTarget = "", waterReminders = true, waterTracking = false).figures(WeightUnit.KG)!!
        assertEquals(2000, fresh.waterTargetMl)
        assertFalse(fresh.waterReminders)
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
