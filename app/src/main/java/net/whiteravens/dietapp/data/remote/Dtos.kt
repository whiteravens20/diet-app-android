package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.Serializable

/**
 * Wire DTOs — the Kotlin mirror of the web repo's `packages/shared` Zod schemas.
 * Keep these in sync with docs/contracts/api-contract.md; a contract change in
 * the web repo is a coordinated change here.
 */

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresIn: Int)

@Serializable
data class AuthUser(val id: String, val email: String, val displayName: String, val role: String)

@Serializable
data class AuthResponse(val user: AuthUser, val tokens: AuthTokens)

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val dietType: String,
    val mealCount: Int,
)

@Serializable
data class MacrosDto(val protein: Double, val fat: Double, val carbs: Double)

@Serializable
data class CalorieCalculationDto(
    val maintenance: Double,
    val dailyTarget: Double,
    val targetMacros: MacrosDto,
    val source: String,
)

@Serializable
data class NutritionDto(
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
)

@Serializable
data class MealPlanDayDto(val id: String, val date: String, val dayNutrition: NutritionDto)

@Serializable
data class MealPlanDto(
    val id: String,
    val profileId: String,
    val startDate: String,
    val durationDays: Int,
    val days: List<MealPlanDayDto>,
)

@Serializable
data class ShoppingListItemDto(
    val id: String,
    val name: String,
    val category: String,
    val toBuyQuantity: Double,
    val unit: String,
    val checked: Boolean,
)

@Serializable
data class ShoppingListDto(val id: String, val planId: String, val items: List<ShoppingListItemDto>)
