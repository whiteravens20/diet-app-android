// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.whiteravens.dietapp.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the app writes into a request: what the API asks to be sent is sent, and nothing it leaves optional is invented. */
class RequestJsonTest {
    private val json = AppModule.requestJson(AppModule.json())

    private inline fun <reified T> written(body: T): JsonObject = json.encodeToJsonElement(body).jsonObject

    @Test
    fun `a change of some parts of a profile sends the parts set, an emptied one as null, and no other`() {
        val body = written(
            ProfilePatchDto(weightKg = JsonPrimitive(72.5), targetWeightKg = JsonNull, customMacroSplit = json.encodeToJsonElement(MacroSplitDto(30, 40, 30))),
        )

        assertEquals(setOf("weightKg", "targetWeightKg", "customMacroSplit"), body.keys)
        assertEquals(JsonPrimitive(72.5), body["weightKg"])
        assertEquals(JsonNull, body["targetWeightKg"])
        assertEquals(setOf("protein", "fat", "carbs"), body.getValue("customMacroSplit").jsonObject.keys)
    }

    @Test
    fun `a change of the limits of a profile's plans sends the ones set, a limit on a kind taken away as null`() {
        val body = written(
            ProfilePreferencesPatchDto(
                maxMealsPerDaySameKind = JsonNull,
                kindLimits = listOf(KindLimitDto(kind = "fish", perDay = 1, perWeek = null)),
                labelLimits = LabelLimitsDto(saltAtMost = 5.0, fiberAtLeast = null, saturatedFatAtMost = null, sugarsAtMost = null),
            ),
        )

        assertEquals(setOf("maxMealsPerDaySameKind", "kindLimits", "labelLimits"), body.keys)
        // No limit on a kind of protein is a null the API reads, and an exception sends both its numbers.
        assertEquals(JsonNull, body["maxMealsPerDaySameKind"])
        val exception = body.getValue("kindLimits").jsonArray.single().jsonObject
        assertEquals(setOf("kind", "perDay", "perWeek"), exception.keys)
        assertEquals(JsonNull, exception["perWeek"])
        // The API takes all four limits from labels: one the profile does not set is a null, never left out.
        val labelLimits = body.getValue("labelLimits").jsonObject
        assertEquals(setOf("saltAtMost", "fiberAtLeast", "saturatedFatAtMost", "sugarsAtMost"), labelLimits.keys)
        assertEquals(JsonNull, labelLimits["sugarsAtMost"])
    }

    @Test
    fun `a new profile with no pace of loss says so, rather than taking the API's pace`() {
        val body = written(
            NewProfileDto(
                name = "Ala", age = 30, sex = "female", heightCm = 170.0, weightKg = 65.0, targetWeightKg = null,
                activityLevel = "moderate", dietType = "balanced", weeklyLossTarget = null, manualCalorieTarget = null,
                mealCount = 3, weightReminderCadence = "weekly", waterTargetMl = 2000, waterTracking = true,
                cookingReminders = true, waterReminders = false, customMacroSplit = null,
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

    @Test
    fun `an entry of a diary sends what its kind takes, and nothing of the others`() {
        val place = CreateDiaryEntryRequest("p", "2026-10-31", "lunch", "recipe", recipeId = "r", servings = 1.5)
        assertEquals(setOf("profileId", "date", "mealType", "kind", "recipeId", "servings"), written(place).keys)

        val ingredient = written(CreateDiaryEntryRequest("p", "2026-10-31", "lunch", "ingredient", ingredientId = "i", amount = DiaryAmountRequest(2.0, "piece")))
        assertEquals(setOf("profileId", "date", "mealType", "kind", "ingredientId", "amount"), ingredient.keys)

        val meal = written(CreateDiaryEntryRequest("p", "2026-10-31", "lunch", "meal", name = "Soup", servings = 2.0, nutrition = DiaryMealNutritionDto(300.0)))
        assertEquals(setOf("profileId", "date", "mealType", "kind", "name", "servings", "nutrition"), meal.keys)
        // A macronutrient left out is one the API estimates: it is not sent at all, not sent as nothing.
        assertEquals(setOf("calories"), meal.getValue("nutrition").jsonObject.keys)
    }

    @Test
    fun `a change of an entry sends what changes, and nothing else`() {
        assertEquals(setOf("quantity"), written(UpdateDiaryEntryRequest(quantity = 300.0)).keys)
        assertEquals(setOf("mealType"), written(UpdateDiaryEntryRequest(mealType = "dinner")).keys)
    }
}
