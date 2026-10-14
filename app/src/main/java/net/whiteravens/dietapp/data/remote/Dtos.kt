// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.Serializable

/**
 * Wire DTOs: what the client sends to the API and reads from it. `ContractTest`
 * holds every call to the copy of the API's contract in contract/; moving to
 * another version of the API is described in docs/contracts/api-contract.md.
 *
 * Enum-like fields are deliberately kept as `String` (allowed values documented
 * per field) so a value added on the platform never breaks deserialisation —
 * the same tolerance `Json { ignoreUnknownKeys }` gives us for new fields.
 * Defaults mirror the Zod `.default()` / `.optional()` markers.
 */

// ── Error envelope (packages/shared/src/error.ts) ───────────────────────────

@Serializable
data class ApiErrorIssueDto(val path: String, val message: String)

/** Uniform error envelope for every non-2xx API response. */
@Serializable
data class ApiErrorDto(
    val statusCode: Int,
    /** Stable machine-readable code, e.g. `AUTH_INVALID_CREDENTIALS`. Translate from this, not [message]. */
    val error: String,
    val message: String,
    val issues: List<ApiErrorIssueDto>? = null,
)

// ── Config (GET /config) ────────────────────────────────────────────────────

/** What the instance tells anybody, signed in or not; the app reads its version from it. */
@Serializable
data class PublicConfigDto(val instance: InstanceDto)

@Serializable
data class InstanceDto(val version: String)

// ── Auth (packages/shared/src/auth.ts) ──────────────────────────────────────

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Access-token TTL, seconds. */
    val expiresIn: Int,
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String,
    /** `user` | `admin`. */
    val role: String,
    val emailVerified: Boolean,
)

/** Returned by login / register / refresh alike. */
@Serializable
data class AuthResponse(val user: AuthUser, val tokens: AuthTokens)

// ── Session user & settings (packages/shared/src/settings.ts) ───────────────

/** `AuthUser` + preference fields; returned by `GET /users/me` and every settings mutation. */
@Serializable
data class SessionUserDto(
    val id: String,
    val email: String,
    val displayName: String,
    /** `user` | `admin`. */
    val role: String,
    val emailVerified: Boolean,
    /** Platform `Locale` enum: `en` | `pl` (may grow — render as-is). */
    val locale: String,
    /** `light` | `dark` | `system`. */
    val theme: String,
    /** Palette family, e.g. `default` | `ocean` | `forest` | `sunset` | `mono` | `rose`. */
    val palette: String,
    /** `none` | `admin` | `byok`. */
    val aiMode: String,
)

/** PATCH `/users/me` — every field optional; omitted fields are left untouched. */
@Serializable
data class UpdateUserSettingsRequest(
    val displayName: String? = null,
    val locale: String? = null,
    val theme: String? = null,
    val palette: String? = null,
    val aiMode: String? = null,
)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class DeleteAccountRequest(val currentPassword: String)

// ── Nutrition (packages/shared/src/nutrition.ts) ────────────────────────────

/** Macronutrient triple, grams. */
@Serializable
data class MacrosDto(val protein: Double, val fat: Double, val carbs: Double)

/** Full nutrition snapshot — calories plus macros. */
@Serializable
data class NutritionDto(
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
)

/** Result of the deterministic calorie engine — rendered, never recomputed here. */
@Serializable
data class CalorieCalculationDto(
    /** Mifflin-St Jeor basal metabolic rate. */
    val bmr: Double,
    /** bmr * activity multiplier (TDEE). */
    val maintenance: Double,
    /** `0.25` | `0.5` | `0.75` | `1.0`, or null when maintaining. */
    val weeklyLossTarget: String? = null,
    /** kcal/day subtracted from maintenance. */
    val dailyDeficit: Double,
    /** Final daily calorie target. */
    val dailyTarget: Double,
    val targetMacros: MacrosDto,
    /** `calculated` | `manual_override`. */
    val source: String,
    /** True when the target was clamped to a safe minimum. */
    val safetyFloorApplied: Boolean,
)

// ── Profiles (packages/shared/src/profile.ts) ───────────────────────────────

@Serializable
data class ProfilePreferencesDto(
    val favoriteIngredientIds: List<String> = emptyList(),
    val excludedIngredientIds: List<String> = emptyList(),
    val allergens: List<String> = emptyList(),
    val dislikedFoods: List<String> = emptyList(),
    val preferredCuisines: List<String> = emptyList(),
    val maxConsecutiveDaysSameMeal: Int = 2,
    val maxTimesPerWeekSameMeal: Int = 3,
    val inventoryBiasResetEvery: Int = 5,
)

@Serializable
data class ProfileDto(
    val id: String,
    val userId: String,
    val name: String,
    val age: Int,
    /** `male` | `female`, or null. */
    val sex: String? = null,
    val heightCm: Double,
    val weightKg: Double,
    /** `sedentary` | `light` | `moderate` | `active` | `very_active`. */
    val activityLevel: String,
    /** Platform `DietType` enum, e.g. `balanced` | `high_protein` | `keto` | `custom` … */
    val dietType: String,
    /** `0.25` | `0.5` | `0.75` | `1.0`, or null. */
    val weeklyLossTarget: String? = null,
    /** Manual kcal target; overrides the calculated value when set. */
    val manualCalorieTarget: Int? = null,
    /** Default meal count (2-5) for new plans. */
    val mealCount: Int,
    /** F19 weight-log nudge cadence, e.g. `weekly`. */
    val weightReminderCadence: String,
    val preferences: ProfilePreferencesDto = ProfilePreferencesDto(),
    val createdAt: String,
    val updatedAt: String,
)

// ── Recipes (packages/shared/src/recipe.ts) ─────────────────────────────────

@Serializable
data class RecipeIngredientDto(
    val ingredientId: String,
    /** Display name, already resolved to the requested locale (F14). */
    val name: String,
    val quantity: Double,
    /** `g` | `ml` | `piece`. */
    val unit: String,
    /** g per piece — when present, UIs render the line in grams. */
    val gramsPerPiece: Double? = null,
    val note: String? = null,
)

@Serializable
data class RecipeDto(
    val id: String,
    val title: String,
    val description: String,
    val servings: Int,
    /** Platform `MealType` values, e.g. `breakfast` | `lunch` | `dinner` | `snack` | `second_breakfast`. */
    val mealTypes: List<String>,
    val dietTags: List<String>,
    val ingredients: List<RecipeIngredientDto>,
    val steps: List<String>,
    val prepMinutes: Int,
    val cookMinutes: Int,
    /** `easy` | `medium` | `hard`. */
    val difficulty: String,
    val allergens: List<String>,
    /** Per-serving nutrition, engine-computed. */
    val nutritionPerServing: NutritionDto,
    /** `seed` | `ai` | `user` | `curated`. */
    val origin: String,
)

/** Paginated recipe list — returned by `GET /recipes`. */
@Serializable
data class RecipeSearchPageDto(
    val items: List<RecipeDto>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int,
)

// ── Meal plans (packages/shared/src/meal-plan.ts) ───────────────────────────

/** A single scheduled meal within a plan day. */
@Serializable
data class PlannedMealDto(
    val id: String,
    val mealType: String,
    /** Null for an F22 user-authored custom meal. */
    val recipe: RecipeDto? = null,
    /** `CATALOGUE` | `USER_CUSTOM`. */
    val source: String,
    /** Display name for a custom meal (null for catalogue meals). */
    val customName: String? = null,
    val servings: Double,
    /** F22 rebalancer multiplier; effective amount = servings * quantityScale. */
    val quantityScale: Double,
    /** Mark-eaten timestamp, or null. */
    val eatenAt: String? = null,
    /** True when a swapped-in favourite is off the plan's diet type. */
    val dietOverride: Boolean,
    /** Effective: per-serving macros * servings * quantityScale. */
    val nutrition: NutritionDto,
)

@Serializable
data class LockedSlotDto(val mealType: String, val recipeId: String)

/** F17 per-day overrides as surfaced on a generated day; null for basic-flow days. */
@Serializable
data class MealPlanDayOverridesDto(
    val mealCount: Int? = null,
    val skip: Boolean? = null,
    val calorieTarget: Int? = null,
    /** `normal` | `rest` | `training`. */
    val dayType: String? = null,
    val cookTimeBudgetMinutes: Int? = null,
    val useUpBy: Boolean? = null,
    val lockedSlots: List<LockedSlotDto>? = null,
)

@Serializable
data class MealPlanDayDto(
    val id: String,
    val date: String,
    val meals: List<PlannedMealDto>,
    /** Sum of meal nutrition. */
    val dayNutrition: NutritionDto,
    val calorieTarget: Double,
    /** Signed delta vs. target — positive means over budget. */
    val calorieDelta: Double,
    val overrides: MealPlanDayOverridesDto? = null,
)

@Serializable
data class MealPlanDto(
    val id: String,
    val profileId: String,
    val startDate: String,
    val durationDays: Int,
    val dietType: String,
    val days: List<MealPlanDayDto>,
    val averageDailyNutrition: NutritionDto,
    val targetMacros: MacrosDto,
    /** 0-1 optimiser reuse score. */
    val ingredientReuseScore: Double,
    /** `deterministic` | `ai_assisted`. */
    val generationMode: String,
    val createdAt: String,
)

/** One plan in the list of a profile's plans: what the list shows, without its days. */
@Serializable
data class MealPlanSummaryDto(
    val id: String,
    val profileId: String,
    val startDate: String,
    val durationDays: Int,
    val dietType: String,
    val averageDailyNutrition: NutritionDto,
    val createdAt: String,
)

/** `GET /meal-plans`: a page of a profile's plans. A plan's days come with `GET /meal-plans/:id`. */
@Serializable
data class MealPlanPageDto(
    val items: List<MealPlanSummaryDto>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int,
)

/**
 * POST `/meal-plans/generate`. The F17 `dayOverrides` advanced flow is not yet
 * used by the app — the field is sparse-optional on the platform, so omitting
 * it is contract-safe.
 */
@Serializable
data class GeneratePlanRequest(
    val profileId: String,
    val startDate: String,
    val durationDays: Int,
    val dietType: String? = null,
    val mealCount: Int? = null,
    val calorieTargetOverride: Int? = null,
    val mealPrepFriendly: Boolean = false,
    val respectExclusions: Boolean = true,
    val respectFavorites: Boolean = true,
    val respectInventory: Boolean = true,
    val maxRepeatsPerRecipe: Int? = null,
)

/** POST `/meal-plans/swap-meal/preview`: ask for a recipe to put in the place of a meal. Nothing is written. */
@Serializable
data class SwapOfferRequest(
    val planId: String,
    val plannedMealId: String,
    /** `random` | `favorite` | `favorite_ingredients`. */
    val strategy: String,
    /** Required when strategy is `favorite`. */
    val favoriteRecipeId: String? = null,
    val respectInventory: Boolean = true,
    val allowOffDiet: Boolean = false,
    /** Recipes offered already for the meal, which the next offer leaves out. */
    val skip: List<String> = emptyList(),
)

/** A recipe offered in the place of a meal, with what the swap would do to the meal and to its day. */
@Serializable
data class MealSwapPreviewDto(
    val recipe: RecipeDto,
    /** Servings of the recipe the meal would be: as many as come nearest to what the meal holds now. */
    val servings: Double,
    val before: NutritionDto,
    val after: NutritionDto,
    /** The day now and with the new meal, before its other meals are balanced around it. */
    val dayBefore: NutritionDto,
    val dayAfter: NutritionDto,
    /** What the day aims at, in kilocalories. */
    val calorieTarget: Double,
    /** The revision of the plan the offer was made from: sent back to take it. */
    val revision: Int,
)

/**
 * POST `/meal-plans/swap-meal`: take an offer. A plan changed since the offer's revision is
 * refused with `PLAN_CHANGED`, and the client reloads it.
 */
@Serializable
data class ApplyMealSwapRequest(
    val planId: String,
    val plannedMealId: String,
    val recipeId: String,
    val revision: Int,
    /** The strategy the offer was asked with. */
    val strategy: String,
    val allowOffDiet: Boolean = false,
)

@Serializable
data class RebalanceChangeDto(val mealId: String, val before: Double, val after: Double)

@Serializable
data class RebalanceSummaryDto(
    /** `day` | `week`. */
    val scope: String,
    /** `in-window` | `best-effort`. */
    val feasibility: String,
    val changes: List<RebalanceChangeDto>,
    val macrosBefore: NutritionDto,
    val macrosAfter: NutritionDto,
)

/** Envelope returned by every F22 edit that may rebalance (swap, custom-add, eaten toggle …). */
@Serializable
data class RebalanceResultDto(val plan: MealPlanDto, val rebalance: RebalanceSummaryDto? = null)

// ── Shopping lists (packages/shared/src/shopping-list.ts) ───────────────────

@Serializable
data class ShoppingListItemDto(
    val id: String,
    val ingredientId: String,
    /** Locale-resolved display name (F14). */
    val name: String,
    /** Platform `ProductCategory` value, e.g. `vegetables` | `dairy` | `pantry` … */
    val category: String,
    /** Total needed, canonical unit, duplicates merged. */
    val totalQuantity: Double,
    /** `g` | `ml` | `piece`. */
    val unit: String,
    /** Pantry coverage snapshot at generation time (the "from pantry" chip). */
    val alreadyHaveQuantity: Double = 0.0,
    /** totalQuantity - alreadyHaveQuantity, floored at 0. */
    val toBuyQuantity: Double,
    /** F15.1 user-entered obtained quantity, incl. pantry pre-credit. Null until first edit. */
    val purchasedQuantity: Double? = null,
    /** Earliest best-before among contributing pantry rows, or null. */
    val pantryBestBefore: String? = null,
    /** Estimated calories contributed by this line (informational). */
    val estimatedCalories: Double,
    val checked: Boolean = false,
)

@Serializable
data class ShoppingListGroupDto(val category: String, val items: List<ShoppingListItemDto>)

@Serializable
data class ShoppingListDto(
    val id: String,
    val planId: String,
    val fromDate: String,
    val toDate: String,
    val groups: List<ShoppingListGroupDto>,
    val totalEstimatedCalories: Double,
    val createdAt: String,
)

/** POST `/shopping-lists/generate` — dates default to the plan's full range. */
@Serializable
data class GenerateShoppingListRequest(
    val planId: String,
    val fromDate: String? = null,
    val toDate: String? = null,
)

/**
 * PATCH `/shopping-lists/:id/items/:itemId`. Values >= totalQuantity auto-check
 * the row server-side. Note: the platform accepts an explicit `purchasedQuantity:
 * null` to clear the field, but our Json encoder drops nulls (`explicitNulls =
 * false`) for PATCH semantics — clearing is not yet supported from Android.
 */
@Serializable
data class UpdateShoppingItemRequest(
    val purchasedQuantity: Double? = null,
    val checked: Boolean? = null,
)
