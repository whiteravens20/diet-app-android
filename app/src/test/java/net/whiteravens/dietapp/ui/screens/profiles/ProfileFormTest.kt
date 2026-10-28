// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import net.whiteravens.dietapp.domain.MacroSplit
import net.whiteravens.dietapp.domain.KindLimit
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.ProfileFigures
import net.whiteravens.dietapp.domain.ProfilePreferences
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
        limits: PlanLimits = ProfileBounds.FIRST_LIMITS,
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
    fun `an empty form refuses what a profile needs, and asks nothing`() {
        val empty = ProfileForm()

        assertEquals(setOf(ProfileField.NAME, ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT), empty.problems(WeightUnit.KG))
        assertNull(empty.question(WeightUnit.KG))
        assertEquals(emptySet<ProfileField>(), filled.problems(WeightUnit.KG))
    }

    @Test
    fun `figures outside the API's bounds are refused, and an empty optional one is not`() {
        assertEquals(setOf(ProfileField.AGE), filled.copy(age = "12").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.HEIGHT), filled.copy(height = "99").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.WEIGHT), filled.copy(weight = "29.9").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.TARGET_WEIGHT), filled.copy(targetWeight = "401").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.OWN_TARGET), filled.copy(ownTarget = "700").problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.NAME), filled.copy(name = "x".repeat(81)).problems(WeightUnit.KG))
        assertEquals(emptySet<ProfileField>(), filled.copy(targetWeight = "", ownTarget = "").problems(WeightUnit.KG))
    }

    @Test
    fun `the limits on repeats are held to the API's bounds, and a form with one refused keeps no limits`() {
        fun limited(limits: LimitsTyped) = filled.copy(limits = limits)
        val first = LimitsTyped.of(ProfileBounds.FIRST_LIMITS)

        assertEquals(setOf(ProfileField.CONSECUTIVE_DAYS), limited(first.copy(consecutiveDays = "0")).problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.TIMES_PER_WEEK), limited(first.copy(timesPerWeek = "8")).problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.KIND_PER_DAY), limited(first.copy(kindPerDay = "6")).problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.KIND_PER_WEEK), limited(first.copy(kindPerWeek = "0")).problems(WeightUnit.KG))
        assertEquals(
            setOf(ProfileField.KIND_LIMITS),
            limited(first.copy(kindLimits = listOf(KindLimitTyped("fish", perWeek = "36")))).problems(WeightUnit.KG),
        )
        assertEquals(setOf(ProfileField.PANTRY_COOL_DOWN), limited(first.copy(pantryCoolDown = "21")).problems(WeightUnit.KG))
        assertNull(limited(first.copy(timesPerWeek = "")).planLimits())
        assertNull(limited(first.copy(kindLimits = listOf(KindLimitTyped("fish", perDay = "x")))).planLimits())
        assertEquals(
            PlanLimits(1, 7, 5, 35, emptyList(), 0),
            limited(LimitsTyped("1", "7", "5", "35", emptyList(), "0")).planLimits(),
        )
    }

    @Test
    fun `the limits on a kind of protein are none while their fields are empty, and an exception gives what it holds`() {
        val first = LimitsTyped.of(ProfileBounds.FIRST_LIMITS)
        assertEquals("", first.kindPerDay)
        assertEquals(ProfileBounds.FIRST_LIMITS, filled.copy(limits = first).planLimits())

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
    fun `a custom diet's split is refused unless its shares are in bounds and add up to 100, and sent with that diet alone`() {
        val custom = filled.copy(dietType = "custom")

        assertEquals(setOf(ProfileField.SPLIT), custom.copy(split = SplitTyped("30", "30", "30")).problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.SPLIT), custom.copy(split = SplitTyped("50", "25", "25")).problems(WeightUnit.KG))
        assertEquals(setOf(ProfileField.SPLIT), custom.copy(split = SplitTyped("", "30", "45")).problems(WeightUnit.KG))
        assertEquals(MacroSplit(25, 30, 45), custom.figures(WeightUnit.KG)!!.customMacroSplit)
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
        assertEquals(setOf(ProfileField.WEIGHT), filled.copy(weight = "65").problems(WeightUnit.LB))
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
        val untitled = filled.copy(name = "", targetWeight = "9999", limits = LimitsTyped("0", "0", "0", "0", emptyList(), "99"))

        val asked = untitled.question(WeightUnit.KG)
        assertNotNull(asked)
        assertEquals(30, asked!!.age)
        assertEquals(64.5, asked.weightKg, 0.0)
        assertNull(untitled.figures(WeightUnit.KG))
        // Ticked while the API says of no risk, the box is not on the form: nothing is acknowledged.
        assertFalse(filled.copy(age = "16", acknowledgeRisk = true).question(WeightUnit.KG)!!.acknowledgeRisk)
        assertTrue(filled.copy(age = "16", acknowledgeRisk = true).question(WeightUnit.KG, riskShown = true)!!.acknowledgeRisk)
        assertFalse(filled.copy(age = "16").question(WeightUnit.KG, riskShown = true)!!.acknowledgeRisk)
    }

    @Test
    fun `the bounds of a weight in pounds are rounded inwards, so that neither is refused`() {
        assertEquals(66.2, WeightUnit.LB.bounds.start, 0.0)
        assertEquals(881.8, WeightUnit.LB.bounds.endInclusive, 0.0)
        assertEquals(30.0, WeightUnit.KG.bounds.start, 0.0)
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
