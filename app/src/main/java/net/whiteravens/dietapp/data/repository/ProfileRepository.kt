// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.CaloriePreviewRequestDto
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.KindLimitDto
import net.whiteravens.dietapp.data.remote.MacroSplitDto
import net.whiteravens.dietapp.data.remote.NewProfileDto
import net.whiteravens.dietapp.data.remote.NewProfilePreferencesDto
import net.whiteravens.dietapp.data.remote.ProfileDto
import net.whiteravens.dietapp.data.remote.ProfilePreferencesPatchDto
import net.whiteravens.dietapp.data.remote.ProfilePreferencesUpdateDto
import net.whiteravens.dietapp.data.remote.ProfileUpdateDto
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.CaloriePreview
import net.whiteravens.dietapp.domain.CalorieQuestion
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.KindLimit
import net.whiteravens.dietapp.domain.LabelLimits
import net.whiteravens.dietapp.domain.MacroSplit
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.PreferencesChange
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
    /** The account's profiles, in the order the API keeps them. */
    suspend fun profiles(): List<ProfileDetails> = apiCall(json) { api.profiles() }.map { it.toDetails() }

    suspend fun profile(id: String): ProfileDetails = apiCall(json) { api.profile(id) }.toDetails()

    /** Moves [id] to keeping its weight, its target weight reached: the profile as it then is. */
    suspend fun keepWeight(id: String): Profile = apiCall(json) { api.keepWeight(id) }.toDomain()

    /** Puts [id]'s offer to keep the weight off for two weeks. */
    suspend fun putOffKeepingWeight(id: String): Profile = apiCall(json) { api.putOffKeepingWeight(id) }.toDomain()

    /**
     * Makes a profile, with [limits] on how often its plans repeat themselves, or the API's own
     * where they are null; [acknowledgeRisk] is said only by a form that showed the risk of its goal.
     */
    suspend fun create(figures: ProfileFigures, limits: PlanLimits?, acknowledgeRisk: Boolean?, labelLimits: LabelLimits? = null): Profile =
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
                    waterTargetMl = figures.waterTargetMl,
                    waterTracking = figures.waterTracking,
                    cookingReminders = figures.cookingReminders,
                    waterReminders = figures.waterReminders,
                    customMacroSplit = figures.customMacroSplit?.toDto(),
                    acknowledgeRisk = acknowledgeRisk,
                    preferences = NewProfilePreferencesDto(
                        allergens = figures.allergens,
                        maxConsecutiveDaysSameMeal = limits?.maxConsecutiveDaysSameMeal,
                        maxTimesPerWeekSameMeal = limits?.maxTimesPerWeekSameMeal,
                        maxMealsPerDaySameKind = limits?.maxMealsPerDaySameKind,
                        maxMealsPerWeekSameKind = limits?.maxMealsPerWeekSameKind,
                        kindLimits = limits?.kindLimits?.map { it.toDto() },
                        inventoryBiasResetEvery = limits?.inventoryBiasResetEvery,
                        labelLimits = labelLimits?.toDto(),
                    ),
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
                    waterTargetMl = figures.waterTargetMl,
                    waterTracking = figures.waterTracking,
                    cookingReminders = figures.cookingReminders,
                    waterReminders = figures.waterReminders,
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
                        maxMealsPerDaySameKind = preferences.maxMealsPerDaySameKind,
                        maxMealsPerWeekSameKind = preferences.maxMealsPerWeekSameKind,
                        kindLimits = preferences.kindLimits.map { it.toDto() },
                        inventoryBiasResetEvery = preferences.inventoryBiasResetEvery,
                        labelLimits = preferences.labelLimits.toDto(),
                    ),
                ),
            )
        }.toDomain()

    suspend fun delete(id: String) = apiCall(json) { api.deleteProfile(id) }

    /** Changes the preferences [change] sets, and no other; the answer is the profile as it is now. */
    suspend fun updatePreferences(id: String, change: PreferencesChange): ProfileDetails =
        apiCall(json) {
            api.updatePreferences(
                id,
                ProfilePreferencesPatchDto(
                    favoriteIngredientIds = change.favoriteIngredientIds,
                    excludedIngredientIds = change.excludedIngredientIds,
                    allergens = change.allergens,
                    dislikedIngredientIds = change.dislikedIngredientIds,
                    preferredCuisines = change.preferredCuisines,
                ),
            )
        }.toDetails()

    /** What the figures of [question] come to, and each pace of loss for the same body. Nothing is kept. */
    suspend fun preview(question: CalorieQuestion): CaloriePreview =
        apiCall(json) {
            api.previewCalories(
                CaloriePreviewRequestDto(
                    age = question.age,
                    sex = question.sex,
                    heightCm = question.heightCm,
                    weightKg = question.weightKg,
                    activityLevel = question.activityLevel,
                    dietType = question.dietType,
                    customMacroSplit = question.customMacroSplit?.toDto(),
                    weeklyLossTarget = question.weeklyLossTarget,
                    manualCalorieTarget = question.manualCalorieTarget,
                    acknowledgeRisk = question.acknowledgeRisk,
                ),
            )
        }.let { answer -> CaloriePreview(answer.calculation.toDomain(), answer.paces.map { it.toDomain() }) }

    /** The catalogue's ingredients whose name holds [search], in the app's language. */
    suspend fun ingredients(search: String): List<IngredientChoice> =
        apiCall(json) { api.ingredients(search = search) }.map { IngredientChoice(it.id, it.name, it.category) }

    /** The ingredients of [ids], to show by their names; none asked for, none asked. */
    suspend fun ingredientsOf(ids: List<String>): List<IngredientChoice> =
        if (ids.isEmpty()) emptyList()
        else apiCall(json) { api.ingredients(ids = ids.joinToString(",")) }.map { IngredientChoice(it.id, it.name, it.category) }
}

private fun MacroSplit.toDto() = MacroSplitDto(protein = protein, fat = fat, carbs = carbs)

private fun KindLimit.toDto() = KindLimitDto(kind = kind, perDay = perDay, perWeek = perWeek)

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
        waterTargetMl = waterTargetMl,
        waterTracking = waterTracking,
        cookingReminders = cookingReminders,
        waterReminders = waterReminders,
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
        maxMealsPerDaySameKind = preferences.maxMealsPerDaySameKind,
        maxMealsPerWeekSameKind = preferences.maxMealsPerWeekSameKind,
        kindLimits = preferences.kindLimits.map { KindLimit(it.kind, it.perDay, it.perWeek) },
        inventoryBiasResetEvery = preferences.inventoryBiasResetEvery,
        labelLimits = preferences.labelLimits.toDomain(),
    ),
    riskAcknowledged = riskAcknowledgedAt != null,
    expenditureCorrectionKcal = expenditureCorrectionKcal,
)
