// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.whiteravens.dietapp.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What the app writes into a request: what the API asks to be sent is sent, and nothing it leaves optional is invented. */
class RequestJsonTest {
    private val json = AppModule.requestJson(AppModule.json())

    private inline fun <reified T> written(body: T): JsonObject = json.encodeToJsonElement(body).jsonObject

    private val preferences = ProfilePreferencesUpdateDto(
        favoriteIngredientIds = emptyList(),
        excludedIngredientIds = emptyList(),
        allergens = emptyList(),
        dislikedIngredientIds = emptyList(),
        preferredCuisines = emptyList(),
        maxConsecutiveDaysSameMeal = 2,
        maxTimesPerWeekSameMeal = 3,
        maxMealsPerDaySameKind = null,
        maxMealsPerWeekSameKind = 7,
        kindLimits = listOf(KindLimitDto(kind = "fish", perDay = 1, perWeek = null)),
        inventoryBiasResetEvery = 5,
    )

    @Test
    fun `a profile kept as it is from now on sends every field, an empty one as null, and every preference`() {
        val body = written(
            ProfileUpdateDto(
                name = "Ala", age = 30, sex = null, heightCm = 170.0, weightKg = 65.0, targetWeightKg = null,
                activityLevel = "moderate", dietType = "balanced", weeklyLossTarget = null, manualCalorieTarget = null,
                mealCount = 3, weightReminderCadence = "weekly", customMacroSplit = null, preferences = preferences,
            ),
        )

        for (field in listOf("sex", "targetWeightKg", "weeklyLossTarget", "manualCalorieTarget", "customMacroSplit")) {
            assertEquals(field, JsonNull, body[field])
        }
        assertFalse("acknowledgeRisk is said only by a form that showed the risk", "acknowledgeRisk" in body)
        val sent = body.getValue("preferences").jsonObject
        assertEquals(ProfilePreferencesUpdateDto.serializer().descriptor.elementsCount, sent.size)
        // No limit on a kind of protein is a null the API reads, and an exception sends both its numbers.
        assertEquals(JsonNull, sent["maxMealsPerDaySameKind"])
        val exception = sent.getValue("kindLimits").jsonArray.single().jsonObject
        assertEquals(setOf("kind", "perDay", "perWeek"), exception.keys)
        assertEquals(JsonNull, exception["perWeek"])
    }

    @Test
    fun `a new profile with no pace of loss says so, rather than taking the API's pace`() {
        val body = written(
            NewProfileDto(
                name = "Ala", age = 30, sex = "female", heightCm = 170.0, weightKg = 65.0, targetWeightKg = null,
                activityLevel = "moderate", dietType = "balanced", weeklyLossTarget = null, manualCalorieTarget = null,
                mealCount = 3, weightReminderCadence = "weekly", customMacroSplit = null,
                preferences = NewProfilePreferencesDto(allergens = listOf("gluten")),
            ),
        )

        assertEquals(JsonNull, body["weeklyLossTarget"])
        assertEquals(setOf("allergens"), body.getValue("preferences").jsonObject.keys)
    }

    @Test
    fun `a day's settings send a target and a limit it has none of as null, and the revision with a change only`() {
        val asked = written(DaySettingsDto(dayType = "normal", skip = false, mealCount = 3, calorieTarget = null, cookTimeBudgetMinutes = null, useUpBy = false))
        val changed = written(DaySettingsDto(dayType = "training", skip = false, mealCount = 4, calorieTarget = null, cookTimeBudgetMinutes = 30, useUpBy = false, revision = 7))

        assertEquals(setOf("dayType", "skip", "mealCount", "calorieTarget", "cookTimeBudgetMinutes", "useUpBy"), asked.keys)
        assertEquals(JsonNull, asked["calorieTarget"])
        assertEquals(JsonNull, asked["cookTimeBudgetMinutes"])
        assertEquals(setOf("dayType", "skip", "mealCount", "calorieTarget", "cookTimeBudgetMinutes", "useUpBy", "revision"), changed.keys)
    }

    @Test
    fun `an optional field the app leaves unset is not sent`() {
        assertEquals(setOf("email", "password"), written(LoginRequest("ala@example.test", "A-long-password-1")).keys)
        assertEquals(setOf("allergens"), written(ProfilePreferencesPatchDto(allergens = listOf("gluten"))).keys)
    }
}
