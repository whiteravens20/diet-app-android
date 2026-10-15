// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.CaloriePreviewRequestDto
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.MacroSplitDto
import net.whiteravens.dietapp.data.remote.NewProfileDto
import net.whiteravens.dietapp.data.remote.NewProfilePreferencesDto
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.remote.ProfilePreferencesPatchDto
import net.whiteravens.dietapp.data.remote.ProfilePreferencesUpdateDto
import net.whiteravens.dietapp.data.remote.ProfileUpdateDto
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.CaloriePreview
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.MacroSplit
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.ProfileFigures
import net.whiteravens.dietapp.domain.ProfilePreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An account's profiles as they are made, changed and deleted, and what figures come to before
 * they are kept. Every number here is the API's: the app works out no calorie target of its own.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val api: DietApiService,
    private val json: Json,
) {
    suspend fun profile(id: String): ProfileDetails = apiCall(json) { api.profile(id) }.toDetails()

    /** Makes a profile; [acknowledgeRisk] is said only by a form that showed the risk of its goal. */
    suspend fun create(figures: ProfileFigures, acknowledgeRisk: Boolean?): Profile =
        apiCall(json) {
            api.createProfile(
                NewProfileDto(
                    name = figures.name,
                    age = figures.age,
                    sex = figures.sex,
                    heightCm = figures.heightCm,
                    weightKg = figures.weightKg,
                    targetWeightKg = figures.targetWeightKg,
                    activityLevel = figures.activityLevel,
                    dietType = figures.dietType,
                    weeklyLossTarget = figures.weeklyLossTarget,
                    manualCalorieTarget = figures.manualCalorieTarget,
                    mealCount = figures.mealCount,
                    weightReminderCadence = figures.weightReminderCadence,
                    customMacroSplit = figures.customMacroSplit?.toDto(),
                    acknowledgeRisk = acknowledgeRisk,
                    preferences = NewProfilePreferencesDto(allergens = figures.allergens),
                ),
            )
        }.toDomain()

    /** Keeps a profile as it is from now on: its figures, and its preferences as they were. */
    suspend fun update(id: String, figures: ProfileFigures, preferences: ProfilePreferences, acknowledgeRisk: Boolean?): Profile =
        apiCall(json) {
            api.updateProfile(
                id,
                ProfileUpdateDto(
                    name = figures.name,
                    age = figures.age,
                    sex = figures.sex,
                    heightCm = figures.heightCm,
                    weightKg = figures.weightKg,
                    targetWeightKg = figures.targetWeightKg,
                    activityLevel = figures.activityLevel,
                    dietType = figures.dietType,
                    weeklyLossTarget = figures.weeklyLossTarget,
                    manualCalorieTarget = figures.manualCalorieTarget,
                    mealCount = figures.mealCount,
                    weightReminderCadence = figures.weightReminderCadence,
                    customMacroSplit = figures.customMacroSplit?.toDto(),
                    acknowledgeRisk = acknowledgeRisk,
                    preferences = ProfilePreferencesUpdateDto(
                        favoriteIngredientIds = preferences.favoriteIngredientIds,
                        excludedIngredientIds = preferences.excludedIngredientIds,
                        allergens = figures.allergens,
                        dislikedIngredientIds = preferences.dislikedIngredientIds,
                        preferredCuisines = preferences.preferredCuisines,
                        maxConsecutiveDaysSameMeal = preferences.maxConsecutiveDaysSameMeal,
                        maxTimesPerWeekSameMeal = preferences.maxTimesPerWeekSameMeal,
                        maxWeeklyAmountPerIngredient = preferences.maxWeeklyAmountPerIngredient,
                        inventoryBiasResetEvery = preferences.inventoryBiasResetEvery,
                    ),
                ),
            )
        }.toDomain()

    suspend fun delete(id: String) = apiCall(json) { api.deleteProfile(id) }

    /** Changes the preferences [patch] sets, and no other. */
    suspend fun updatePreferences(id: String, patch: ProfilePreferencesPatchDto): ProfileDetails =
        apiCall(json) { api.updatePreferences(id, patch) }.toDetails()

    /** What [figures] come to, and each pace of loss for the same body. Nothing is kept. */
    suspend fun preview(figures: ProfileFigures, acknowledgeRisk: Boolean): CaloriePreview =
        apiCall(json) {
            api.previewCalories(
                CaloriePreviewRequestDto(
                    age = figures.age,
                    sex = figures.sex,
                    heightCm = figures.heightCm,
                    weightKg = figures.weightKg,
                    activityLevel = figures.activityLevel,
                    dietType = figures.dietType,
                    customMacroSplit = figures.customMacroSplit?.toDto(),
                    weeklyLossTarget = figures.weeklyLossTarget,
                    manualCalorieTarget = figures.manualCalorieTarget,
                    acknowledgeRisk = acknowledgeRisk,
                ),
            )
        }.let { answer -> CaloriePreview(answer.calculation.toDomain(), answer.paces.map { it.toDomain() }) }

    /** The catalogue's ingredients whose name holds [search], in the app's language. */
    suspend fun ingredients(search: String): List<IngredientChoice> =
        apiCall(json) { api.ingredients(search) }.map { IngredientChoice(it.id, it.name, it.category) }
}

private fun MacroSplit.toDto() = MacroSplitDto(protein = protein, fat = fat, carbs = carbs)

fun ProfileDto.toDetails() = ProfileDetails(
    id = id,
    figures = ProfileFigures(
        name = name,
        age = age,
        sex = sex,
        heightCm = heightCm,
        weightKg = weightKg,
        targetWeightKg = targetWeightKg,
        activityLevel = activityLevel,
        dietType = dietType,
        weeklyLossTarget = weeklyLossTarget,
        manualCalorieTarget = manualCalorieTarget,
        mealCount = mealCount,
        weightReminderCadence = weightReminderCadence,
        customMacroSplit = customMacroSplit?.let { MacroSplit(it.protein, it.fat, it.carbs) },
        allergens = preferences.allergens,
    ),
    preferences = ProfilePreferences(
        favoriteIngredientIds = preferences.favoriteIngredientIds,
        excludedIngredientIds = preferences.excludedIngredientIds,
        dislikedIngredientIds = preferences.dislikedIngredientIds,
        preferredCuisines = preferences.preferredCuisines,
        maxConsecutiveDaysSameMeal = preferences.maxConsecutiveDaysSameMeal,
        maxTimesPerWeekSameMeal = preferences.maxTimesPerWeekSameMeal,
        maxWeeklyAmountPerIngredient = preferences.maxWeeklyAmountPerIngredient,
        inventoryBiasResetEvery = preferences.inventoryBiasResetEvery,
    ),
    riskAcknowledged = riskAcknowledgedAt != null,
)
