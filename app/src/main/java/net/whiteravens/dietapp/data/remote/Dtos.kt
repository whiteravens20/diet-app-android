// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    /** What the code has more to say, where it has any (the meal no recipe was left for, …). */
    val details: JsonObject? = null,
)

// ── Config (GET /config) ────────────────────────────────────────────────────

/** What the instance tells anybody, signed in or not: its version, and whether it asks for the anti-abuse check. */
@Serializable
data class PublicConfigDto(
    val instance: InstanceDto,
    val turnstile: TurnstileDto,
    val accounts: AccountsConfigDto = AccountsConfigDto(),
    val email: EmailConfigDto = EmailConfigDto(),
    val catalogue: CatalogueConfigDto = CatalogueConfigDto(),
    val plans: PlansConfigDto = PlansConfigDto(),
)

/** The most a recipe repeats in a plan that names no number, where the instance sets one. */
@Serializable
data class PlansConfigDto(val maxRepeatsPerRecipe: Int? = null)

/** How many profiles an account may have on the instance; two where it does not say, as the API's default. */
@Serializable
data class AccountsConfigDto(val maxProfiles: Int = 2)

/**
 * How many recipes the catalogue has for the meal a diet has the fewest for, at each number of
 * meals a day (`"2"` to `"5"`): what tells a diet with no recipe, or few, for a plan.
 */
@Serializable
data class CatalogueConfigDto(val depth: Map<String, Map<String, Int>> = emptyMap())

/** Whether the instance sends mail at all: a confirmation mail or a password reset needs it. */
@Serializable
data class EmailConfigDto(val enabled: Boolean = false)

/** Cloudflare Turnstile: on or off, and the site key a page shows the check with. */
@Serializable
data class TurnstileDto(val enabled: Boolean, val siteKey: String? = null)

@Serializable
data class InstanceDto(val version: String)

// ── Health (GET /health) ────────────────────────────────────────────────────

/** Whether the API is up and reaches its database (`db`: `up` or `down`). */
@Serializable
data class HealthDto(val status: String, val db: String)

// ── Auth (packages/shared/src/auth.ts) ──────────────────────────────────────

/** A mail with a link to set a new password, asked for an address; [turnstileToken] as for a sign-in. */
@Serializable
data class PasswordResetRequestDto(val email: String, val turnstileToken: String? = null)

/** [turnstileToken]: the anti-abuse check's answer, where the instance has the check on. */
@Serializable
data class LoginRequest(val email: String, val password: String, val turnstileToken: String? = null)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val turnstileToken: String? = null,
)

/** POST `/auth/login/second-factor`: the sign-in's challenge, with a code from the authenticator app or a recovery code. */
@Serializable
data class SecondFactorSignInRequest(val challenge: String, val code: String)

/** The second step of a sign-in: the challenge to answer, and for how many seconds it holds. */
@Serializable
data class SecondFactorChallengeDto(val challenge: String, val expiresIn: Double)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthTokens(
    val accessToken: String,
    /** Always in the answer to a client that does not ask for it as a cookie, as this one never does. */
    val refreshToken: String,
    /** Access-token TTL, seconds. */
    val expiresIn: Double,
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

/** Returned by register, refresh and the second step of a sign-in. */
@Serializable
data class AuthResponse(val user: AuthUser, val tokens: AuthTokens)

/**
 * `POST /auth/login` answers one of two shapes: a session ([user] and [tokens]), or, for an
 * account with two-step sign-in, a challenge ([secondFactor]) to answer with a code.
 */
@Serializable
data class LoginResponseDto(
    val user: AuthUser? = null,
    val tokens: AuthTokens? = null,
    val secondFactor: SecondFactorChallengeDto? = null,
)

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
    /** `kg` | `lb`, or null for the unit of the account's language. */
    val weightUnit: String? = null,
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

/** What a plan holds and what it aims at. */
@Serializable
data class PlannedTargetDto(val planned: Double, val target: Double)

/** The grams of each macro a plan holds and aims at. */
@Serializable
data class PlannedTargetMacrosDto(val planned: MacrosDto, val target: MacrosDto)

/** A number of meals out of how many there are. */
@Serializable
data class ShareDto(val meals: Int, val of: Int)

/** The plan an overview is of. */
@Serializable
data class OverviewPlanDto(val id: String, val startDate: String, val durationDays: Int, val dietType: String, val status: String)

/** The shopping lists of a plan: how many, and how many of their entries are ticked. */
@Serializable
data class OverviewShoppingDto(val lists: Int, val entries: Int, val ticked: Int)

/** A day of a plan as its overview counts it. */
@Serializable
data class OverviewDayDto(val date: String, val calories: Double, val target: Double, val skipped: Boolean)

/** What a profile's plan of the moment comes to. */
@Serializable
data class PlanOverviewDto(
    val plan: OverviewPlanDto,
    val energy: PlannedTargetDto,
    val macros: PlannedTargetMacrosDto,
    val eaten: ShareDto,
    val onDiet: ShareDto,
    val favourites: ShareDto,
    val ingredientReuse: Double,
    val shopping: OverviewShoppingDto,
    val days: List<OverviewDayDto>,
)

/** GET `/meal-plans/overview`: the overview, or none for a profile with no plan. */
@Serializable
data class PlanOverviewAnswerDto(val overview: PlanOverviewDto? = null)

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
    /** kcal a day the chosen pace asks for, before any limit. */
    val requestedDeficit: Double,
    /** kcal/day subtracted from maintenance. */
    val dailyDeficit: Double,
    /** Final daily calorie target. */
    val dailyTarget: Double,
    val targetMacros: MacrosDto,
    /** `calculated` | `manual_override`. */
    val source: String,
    /** The lowest a calculated target goes for this profile. */
    val safetyFloor: Double,
    /** The pace asked for more than a quarter of maintenance, and was slowed to that. */
    val deficitCapped: Boolean,
    /** The target was raised to the floor. */
    val safetyFloorApplied: Boolean,
    /** Maintenance itself is under the floor: no loss is planned, the target is maintenance. */
    val maintenanceUnderFloor: Boolean,
    /**
     * The profile asks to lose weight, is under 18 or underweight, and has not said it
     * understands the risk: the target is held at maintenance until it does.
     */
    val goalHeld: Boolean,
    /** `minor` | `underweight` | `below_floor` | `below_bmr`. */
    val warnings: List<String>,
)

// ── Profiles (packages/shared/src/profile.ts) ───────────────────────────────

@Serializable
data class ProfilePreferencesDto(
    val favoriteIngredientIds: List<String> = emptyList(),
    val excludedIngredientIds: List<String> = emptyList(),
    val allergens: List<String> = emptyList(),
    /**
     * Ingredients the profile would rather not eat: a recipe holding one is taken only where
     * every recipe left for the meal does. To rule one out there is [excludedIngredientIds].
     */
    val dislikedIngredientIds: List<String> = emptyList(),
    val preferredCuisines: List<String> = emptyList(),
    val maxConsecutiveDaysSameMeal: Int = 2,
    val maxTimesPerWeekSameMeal: Int = 3,
    /** The most of one ingredient, in g or ml, the meals of any seven days of a plan hold together. */
    val maxWeeklyAmountPerIngredient: Int = 3000,
    val inventoryBiasResetEvery: Int = 5,
)

/** The split of a day's energy a profile on the `custom` diet aims at, in percent. */
@Serializable
data class MacroSplitDto(val protein: Int, val fat: Int, val carbs: Int)

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
    /** The weight the profile works towards, in kg; null when it named none. */
    val targetWeightKg: Double? = null,
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
    /** How often the profile is reminded to log its weight: `off` | `daily` | `weekly`. */
    val weightReminderCadence: String,
    /** The split of energy on the `custom` diet; null on another diet that kept none. */
    val customMacroSplit: MacroSplitDto? = null,
    val preferences: ProfilePreferencesDto = ProfilePreferencesDto(),
    /** When the profile said it understands the risk of a target held at maintenance, or null. */
    val riskAcknowledgedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * A new profile (`POST /profiles`). Every field the form shows is sent, an empty one as null, so
 * that a choice of none (no pace of loss, no sex given, no own target) is the profile's own and not
 * the API's default; [acknowledgeRisk] only by a form that showed the risk of losing weight.
 */
@Serializable
data class NewProfileDto(
    val name: String,
    val age: Int,
    val sex: String?,
    val heightCm: Double,
    val weightKg: Double,
    val targetWeightKg: Double?,
    val activityLevel: String,
    val dietType: String,
    val weeklyLossTarget: String?,
    val manualCalorieTarget: Int?,
    val mealCount: Int,
    val weightReminderCadence: String,
    val customMacroSplit: MacroSplitDto?,
    val acknowledgeRisk: Boolean? = null,
    val preferences: NewProfilePreferencesDto,
)

/**
 * What a new profile is given of its preferences: its allergens, and the limits on how often a plan
 * repeats itself where the form asked for them; the API sets the rest.
 */
@Serializable
data class NewProfilePreferencesDto(
    val allergens: List<String>,
    val maxConsecutiveDaysSameMeal: Int? = null,
    val maxTimesPerWeekSameMeal: Int? = null,
    val maxWeeklyAmountPerIngredient: Int? = null,
    val inventoryBiasResetEvery: Int? = null,
)

/** A profile as it is from now on (`PUT /profiles/:id`): every part of it, its preferences too. */
@Serializable
data class ProfileUpdateDto(
    val name: String,
    val age: Int,
    val sex: String?,
    val heightCm: Double,
    val weightKg: Double,
    val targetWeightKg: Double?,
    val activityLevel: String,
    val dietType: String,
    val weeklyLossTarget: String?,
    val manualCalorieTarget: Int?,
    val mealCount: Int,
    val weightReminderCadence: String,
    val customMacroSplit: MacroSplitDto?,
    val acknowledgeRisk: Boolean? = null,
    val preferences: ProfilePreferencesUpdateDto,
)

/** Every preference of a profile, as a `PUT` sends them: none is left to a default. */
@Serializable
data class ProfilePreferencesUpdateDto(
    val favoriteIngredientIds: List<String>,
    val excludedIngredientIds: List<String>,
    val allergens: List<String>,
    val dislikedIngredientIds: List<String>,
    val preferredCuisines: List<String>,
    val maxConsecutiveDaysSameMeal: Int,
    val maxTimesPerWeekSameMeal: Int,
    val maxWeeklyAmountPerIngredient: Int,
    val inventoryBiasResetEvery: Int,
)

/** Some of a profile's preferences (`PATCH /profiles/:id/preferences`): the ones set, and no other. */
@Serializable
data class ProfilePreferencesPatchDto(
    val favoriteIngredientIds: List<String>? = null,
    val excludedIngredientIds: List<String>? = null,
    val allergens: List<String>? = null,
    val dislikedIngredientIds: List<String>? = null,
    val preferredCuisines: List<String>? = null,
    val maxWeeklyAmountPerIngredient: Int? = null,
)

/**
 * What typed figures come to (`POST /profiles/calories`), before there is a profile or without
 * changing one: the same fields as a profile's, nothing kept.
 */
@Serializable
data class CaloriePreviewRequestDto(
    val age: Int,
    val sex: String?,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: String,
    val dietType: String,
    val customMacroSplit: MacroSplitDto?,
    val weeklyLossTarget: String?,
    val manualCalorieTarget: Int?,
    val acknowledgeRisk: Boolean,
)

/** The calculation for the typed figures, and the same body at each pace of loss, slowest first. */
@Serializable
data class CaloriePreviewDto(val calculation: CalorieCalculationDto, val paces: List<CalorieCalculationDto>)

// ── Weights (packages/shared/src/weight.ts) ─────────────────────────────────

/** A weight logged for a profile, in kilograms whatever unit the account reads it in. */
@Serializable
data class WeightEntryDto(
    val id: String,
    val profileId: String,
    val kg: Double,
    /** When the weight was taken (ISO-8601). */
    val recordedAt: String,
    val createdAt: String,
)

/** POST `/weights`: a weight of a profile, taken the moment it is logged. */
@Serializable
data class LogWeightRequest(val profileId: String, val kg: Double)

// ── Ingredients (packages/shared/src/ingredient.ts) ─────────────────────────

/** An ingredient of the catalogue, as a picker shows it: its name in the language asked for. */
@Serializable
data class IngredientDto(val id: String, val name: String, val category: String, val allergens: List<String> = emptyList())

// ── Recipes (packages/shared/src/recipe.ts) ─────────────────────────────────

/** An amount as it is shown: rounded, in `g` | `ml` | `piece` | `slice` | `clove` | `handful`. */
@Serializable
data class DisplayAmountDto(val quantity: Double, val unit: String)

@Serializable
data class RecipeIngredientDto(
    val ingredientId: String,
    /** The ingredient's name in the language the request asked for. */
    val name: String,
    val quantity: Double,
    /** `g` | `ml` | `piece`. */
    val unit: String,
    /** The quantity as it is shown. */
    val display: DisplayAmountDto,
    val note: String? = null,
)

/** Where a recipe taken in from somewhere else is from, and the licence it may be passed on under. */
@Serializable
data class RecipeSourceDto(val url: String? = null, val license: String)

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
    /** The cuisine the recipe belongs to, when it belongs to one. */
    val cuisine: String? = null,
    val allergens: List<String>,
    /** Per-serving nutrition, engine-computed. */
    val nutritionPerServing: NutritionDto,
    /** `seed` | `ai` | `user` | `curated`. */
    val origin: String,
    /**
     * `written`: a recipe of the user's a model wrote; `changed`: one in which a model chose the
     * ingredient swapped in, or reworded the text around it; null otherwise.
     */
    val byModel: String? = null,
    /** To be shown with a recipe taken in from somewhere else; null for one written here. */
    val source: RecipeSourceDto? = null,
)

/**
 * POST `/recipes/drafts/from-prompt`: a recipe of the account's own a model writes from these
 * choices, none of them a text of the user's. What is left null is the model's to choose; the
 * profile's allergens hold whatever is chosen.
 */
@Serializable
data class AiDraftRecipeRequest(
    val profileId: String,
    val mealType: String? = null,
    val dietType: String? = null,
    val cuisine: String? = null,
    val cookingMethod: String? = null,
    val complexity: String? = null,
    /** What a serving is to hold, 200 to 1200 kcal. */
    val kcalTarget: Int? = null,
    /** The most minutes of preparing and cooking, 5 to 120. */
    val prepTimeMaxMinutes: Int? = null,
    val useFavoriteIngredients: Boolean = false,
    val respectExclusions: Boolean = true,
    val addToFavorites: Boolean = true,
)

/** The recipe a model wrote, with the nutrition the engine worked out, and what the model was. */
@Serializable
data class AiDraftRecipeResponseDto(val recipe: RecipeDto, val aiMeta: AiGenerationMetaDto)

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

/** A line of a meal: what the meal takes of an ingredient, scaled and rounded to the meal. */
@Serializable
data class PlannedIngredientDto(
    val ingredientId: String,
    val name: String,
    val quantity: Double,
    /** `g` | `ml` | `piece`. */
    val unit: String,
    val display: DisplayAmountDto,
    val note: String? = null,
)

/** Set when a model chose the recipe of a meal: the reason it gave, when it gave one. */
@Serializable
data class ModelPickDto(val reason: String? = null)

/** A single scheduled meal within a plan day. */
@Serializable
data class PlannedMealDto(
    val id: String,
    val mealType: String,
    /** Null for a meal of the user's own. */
    val recipe: RecipeDto? = null,
    /**
     * What this meal takes of each ingredient, scaled to the meal, where [recipe] holds the
     * recipe as written for its own servings. Empty for a meal of the user's own.
     */
    val ingredients: List<PlannedIngredientDto>,
    /** `CATALOGUE` | `USER_CUSTOM`. */
    val source: String,
    /** Display name for a custom meal (null for catalogue meals). */
    val customName: String? = null,
    val servings: Double,
    /** The multiplier a rebalance gave the meal; the amount eaten is servings * quantityScale. */
    val quantityScale: Double,
    /** Mark-eaten timestamp, or null. */
    val eatenAt: String? = null,
    /** True when a swapped-in favourite is off the plan's diet type. */
    val dietOverride: Boolean,
    /**
     * What the profile has ruled out since the meal was planned (`allergen` |
     * `excluded_ingredient` | `avoided`): the meal stays, and says so.
     */
    val restrictionConflicts: List<String>,
    /** The meal's ingredients the profile would rather not eat, as the profile has them now. */
    val dislikedIngredientIds: List<String>,
    val modelPick: ModelPickDto? = null,
    /**
     * In a plan made for cooking ahead: `cook` on the meal cooked for the next one as well,
     * `cooked` on the meal eaten from it; null otherwise.
     */
    val cookAhead: String? = null,
    /** Effective: per-serving macros * servings * quantityScale. */
    val nutrition: NutritionDto,
)

@Serializable
data class LockedSlotDto(val mealType: String, val recipeId: String)

/** What was asked of one day of a plan, where it differs from the plan; null on a day asked nothing of. */
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
    /** What the day aims at of protein, fat and carbohydrate, in grams. */
    val targetMacros: MacrosDto,
    /** Signed delta vs. target — positive means over budget. */
    val calorieDelta: Double,
    /** `over` | `under` when the day is outside a tenth either side of its target; null inside it. */
    val offTarget: String? = null,
    /** The user skips the day: no meals, no shortfall, left out of the plan's averages. */
    val skipped: Boolean,
    /** A target somebody typed has put the day under the lowest a calculated plan goes. */
    val belowSafetyFloor: Boolean,
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
    /** What the menu, as it is now, buys and uses of packs that do not keep. */
    val packs: PlanPacksDto,
    /** `deterministic` | `ai_assisted`. */
    val generationMode: String,
    /** What the plan was asked for. */
    val options: PlanOptionsDto,
    /** What its generation had to do; null on a plan made before that was kept. */
    val report: GenerationReportDto? = null,
    /** How many times the plan has been changed; a change from an older one is refused with `PLAN_CHANGED`. */
    val revision: Int,
    val createdAt: String,
)

/** A pack the menu opens and does not finish. */
@Serializable
data class PlanOpenPackDto(
    val ingredientId: String,
    val name: String,
    /** `g` | `ml`: what the pack and what is left of it are counted in. */
    val unit: String,
    val packSize: Double,
    /** How many packs the menu takes of the ingredient. */
    val packs: Int,
    /** What the menu leaves of the last of them. */
    val left: Int,
    /** More of it is left than the fifth of a pack a plan accepts. */
    val wasted: Boolean,
)

@Serializable
data class PlanPacksDto(val bought: Double, val used: Double, val open: List<PlanOpenPackDto>)

/** What a plan was asked for when it was made. */
@Serializable
data class PlanOptionsDto(
    val dietType: String? = null,
    val mealCount: Int? = null,
    val calorieTargetOverride: Int? = null,
    val mealPrepFriendly: Boolean,
    val respectExclusions: Boolean,
    val respectFavorites: Boolean,
    val respectInventory: Boolean,
    val maxRepeatsPerRecipe: Int? = null,
    /** The limits the plan may give way on when no recipe is left inside them. */
    val allowRelaxations: List<String>,
)

/** A meal outside a limit, because no recipe was left inside it, and the limits it gave way on. */
@Serializable
data class RelaxedMealDto(val date: String, val slot: String, val gaveWay: List<String>)

/** A meal for which the diet and the meal left one recipe to choose from. */
@Serializable
data class SingleCandidateDto(val date: String, val slot: String)

/** What the generation of a plan had to do to fill it, as far as it still holds. */
@Serializable
data class GenerationReportDto(
    val relaxations: List<RelaxedMealDto>,
    val singleCandidate: List<SingleCandidateDto>,
    /** `counted` | `left_out` | `unused` | `empty` | `not_asked`. */
    val pantry: String,
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
    /** 0-1 optimiser reuse score. */
    val ingredientReuseScore: Double,
    /** `deterministic` | `ai_assisted`. */
    val generationMode: String,
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

/** POST `/meal-plans/generate`: a plan asked for, and the days that differ from it (`dayOverrides`). */
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
    /** The limits that may give way when no recipe is left inside them, in the order they do. */
    val allowRelaxations: List<String>? = null,
    val dayOverrides: List<DayOverrideDto>? = null,
)

/** The days of a profile's plans in a span of days: those no plan holds are left out. */
@Serializable
data class PlanCalendarDto(val days: List<PlanCalendarDayDto>)

/**
 * A day a plan holds: the plan made last that holds it, how many plans hold it, what it holds and
 * aims at in kcal, whether it is more than a tenth off (`over` | `under`), its meals eaten out of
 * its meals, and whether the plan skips it.
 */
@Serializable
data class PlanCalendarDayDto(
    val date: String,
    val planId: String,
    val plans: Int,
    val calories: Double,
    val target: Double,
    val offTarget: String? = null,
    val eaten: EatenMealsDto,
    val skipped: Boolean,
)

@Serializable
data class EatenMealsDto(val meals: Int, val of: Int)

/** What one day of a new plan is made with where it differs from the plan; a day that differs in nothing is not sent. */
@Serializable
data class DayOverrideDto(
    val date: String,
    val mealCount: Int? = null,
    val skip: Boolean? = null,
    val calorieTarget: Int? = null,
    /** `rest` | `training`; a normal day says nothing. */
    val dayType: String? = null,
    val cookTimeBudgetMinutes: Int? = null,
    val useUpBy: Boolean? = null,
    val lockedSlots: List<LockedSlotDto>? = null,
)

/**
 * What a refusal with the code `PLAN_NEEDS_RELAXATION` carries in its details: the limits that
 * would have to give way, in the order they do, and the meals that would go outside them.
 */
@Serializable
data class RelaxationNeededDto(val needs: List<String>, val meals: List<RelaxedMealDto>)

/** A recipe a profile marked (`favorite`, `often` or `avoid`) under tags of its own, with the recipe itself. */
@Serializable
data class MarkedRecipeDto(
    val id: String,
    val tags: List<String> = emptyList(),
    val sentiment: String,
    val markedAt: String,
    val recipe: RecipeDto,
)

/** POST `/favorites`: how a profile marks a recipe, and under which of its tags. */
@Serializable
data class AddFavoriteRequest(val profileId: String, val recipeId: String, val tags: List<String>, val sentiment: String)

/** A profile's favourite set: a recipe for each meal it names. */
@Serializable
data class FavoriteSetDto(val id: String, val profileId: String, val label: String, val slots: Map<String, String>)

/** POST `/favorite-sets`: a set of a profile, named, with a recipe for each meal it names. */
@Serializable
data class CreateFavoriteSetRequest(val profileId: String, val label: String, val slots: Map<String, String>)

/** PATCH `/favorite-sets/{id}`: a set's name and its recipes, both said anew. */
@Serializable
data class UpdateFavoriteSetRequest(val label: String, val slots: Map<String, String>)

/** POST `/favorite-sets/{id}/apply`: the plan, and the days of it (yyyy-MM-dd) the set's recipes go on. */
@Serializable
data class ApplyFavoriteSetRequest(val planId: String, val dayDates: List<String>)

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
    /** The strategy the offer was asked with; `random` for one a model picked. */
    val strategy: String,
    val allowOffDiet: Boolean = false,
    /** The note that came with an offer a model picked: the meal then says that a model chose its recipe. */
    val pick: String? = null,
)

/** POST `/meal-plans/ai-swap-meal/preview`: a model asked which of the recipes the engine puts first to take. */
@Serializable
data class AiSwapOfferRequest(
    val planId: String,
    val plannedMealId: String,
    val respectInventory: Boolean = true,
    val skip: List<String> = emptyList(),
)

/** Who picked an offer a model was asked for: the model, or the engine and why. */
@Serializable
data class AiGenerationMetaDto(
    val provider: String? = null,
    val model: String? = null,
    val failoverChain: List<String> = emptyList(),
    val usedDeterministicFallback: Boolean,
    /** Why the engine picked; null when the model did. */
    val fallbackReason: String? = null,
    /** The model's own sentence on its pick. */
    val reason: String? = null,
)

/** An offer a model was asked for: the offer, who picked it, and the note of a model's pick (null when the engine picked). */
@Serializable
data class AiMealSwapPreviewDto(
    val recipe: RecipeDto,
    val servings: Double,
    val before: NutritionDto,
    val after: NutritionDto,
    val dayBefore: NutritionDto,
    val dayAfter: NutritionDto,
    val calorieTarget: Double,
    val revision: Int,
    val aiMeta: AiGenerationMetaDto,
    val pick: String? = null,
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
    /** What the balancing could not take back into the day or the week, in kilocalories. */
    val unabsorbedKcal: Double,
    val macrosBefore: NutritionDto,
    val macrosAfter: NutritionDto,
)

/** What a change of a plan that may balance it answers: a swap, a meal of one's own, a meal marked eaten. */
@Serializable
data class RebalanceResultDto(val plan: MealPlanDto, val rebalance: RebalanceSummaryDto? = null)

/** An ingredient the catalogue's own rules name in the place of one of a planned meal. */
@Serializable
data class SubstituteSuggestionDto(val fromIngredientId: String, val to: IngredientDto, val moved: List<String> = emptyList())

/** POST `/meal-plans/swap-ingredient/preview` and `/apply`: the line that leaves and the ingredient that comes in its place. */
@Serializable
data class SwapIngredientRequest(
    val planId: String,
    val plannedMealId: String,
    val fromIngredientId: String,
    val toIngredientId: String,
    /** A model rewords the recipe's text around the substitute: one of the account's AI requests. */
    val rewriteWithAi: Boolean = false,
    /** Made although it moves what a serving holds by more than a tenth. */
    val acceptChange: Boolean = false,
    /** The note that came with a substitute a model suggested. */
    val pick: String? = null,
)

/** What a substitution would do to the meal it is made in, the portion on the plate, before anything is written. */
@Serializable
data class SwapPreviewDto(
    val before: NutritionDto,
    val after: NutritionDto,
    /** What the meal takes of the ingredient that leaves, all its lines together. */
    val from: PlannedIngredientDto,
    /** What it would take of the one that comes in its place. */
    val to: PlannedIngredientDto,
    /** `energy` | `capped` | `mass`: how the amount of the substitute was decided. */
    val sizing: String,
    /** The recipe has the substitute already: its amount grows. */
    val merged: Boolean,
    /** `off_diet` | `allergen` | `excluded_ingredient`. */
    val violations: List<String>,
    /** `calories` | `protein` | `fat` | `carbs`: what of a serving moves by more than a tenth. */
    val moved: List<String>,
    val valid: Boolean,
)

/** POST `/meal-plans/swap-ingredient/ai-suggest`: a model asked which ingredient of the engine's pool to take. */
@Serializable
data class AiSuggestIngredientRequest(val planId: String, val plannedMealId: String, val fromIngredientId: String)

@Serializable
data class AiSuggestIngredientResponseDto(val toIngredient: IngredientDto, val aiMeta: AiGenerationMetaDto, val pick: String? = null)

/** What a substitution answers: the plan, what it rebalanced, and who reworded the text where a model was asked to. */
@Serializable
data class SwapIngredientResponseDto(val plan: MealPlanDto, val rebalance: RebalanceSummaryDto? = null, val aiMeta: AiGenerationMetaDto? = null)

/**
 * POST `/meal-plans/:id/regenerate` and `/meal-plans/:id/days/:dayId/regenerate`: the days from
 * [from] to [to] get other meals (the whole plan where both are left out); what is otherwise left
 * alone may go as well; a lock whose recipe may no longer go in is dropped instead of refusing;
 * [allowRelaxations] are the limits that may give way this time (those of the plan where left out).
 */
@Serializable
data class RegenerateRequest(
    val from: String? = null,
    val to: String? = null,
    val includePast: Boolean = false,
    val replaceEaten: Boolean = false,
    val replaceCustom: Boolean = false,
    val dropIneligibleLocks: Boolean = false,
    val allowRelaxations: List<String>? = null,
)

/** Whether a model is asked to put the engine's proposals in order and say a sentence of each. */
@Serializable
data class WeighRequest(val weigh: Boolean = false)

/** What a menu buys in packs that do not keep against what it uses of them, and how many it leaves more than a fifth of. */
@Serializable
data class PackTallyDto(val bought: Double, val used: Double, val wasted: Int)

/** A meal of a day as another way to fill the day has it. */
@Serializable
data class DayVariationMealDto(
    val mealType: String,
    /** The name of its recipe, or of a meal of the user's own. */
    val title: String,
    val recipeId: String? = null,
    val servings: Double,
    val nutrition: NutritionDto,
    /** On the day already, and stays: eaten, or the user's own. */
    val stays: Boolean,
)

/** Another way to fill a day of a plan, with the note that takes it. */
@Serializable
data class DayVariationDto(
    val meals: List<DayVariationMealDto>,
    val dayNutrition: NutritionDto,
    val packs: PackTallyDto,
    /** A model's sentence on this way, where one was asked and said one. */
    val reason: String? = null,
    val offer: String,
)

@Serializable
data class DayAsItIsDto(val dayNutrition: NutritionDto, val packs: PackTallyDto)

/** POST `/meal-plans/:id/days/:dayId/variations`: up to three other ways to fill a day; nothing is written. */
@Serializable
data class DayVariationsDto(
    val date: String,
    val calorieTarget: Double,
    val current: DayAsItIsDto,
    val variations: List<DayVariationDto>,
    /** Who put them in order, or why the engine's order stands; null when no model was asked. */
    val aiMeta: AiGenerationMetaDto? = null,
)

/** A recipe named by its id and title. */
@Serializable
data class RecipeRefDto(val id: String, val title: String)

/** What a swap that uses up a pack takes more of it, and what would be left of the pack, to the gram or millilitre. */
@Serializable
data class UseUpTakeDto(val ingredientId: String, val name: String, val unit: String, val amount: Int, val left: Int)

@Serializable
data class PacksBeforeAfterDto(val before: PackTallyDto, val after: PackTallyDto)

/** A recipe in the place of a meal that would use up what the plan opens. */
@Serializable
data class UseUpSuggestionDto(
    val plannedMealId: String,
    val date: String,
    val mealType: String,
    val from: RecipeRefDto,
    val to: RecipeRefDto,
    val servings: Double,
    val before: NutritionDto,
    val after: NutritionDto,
    val takes: List<UseUpTakeDto>,
    val packs: PacksBeforeAfterDto,
    /** A model's sentence on the swap, where one was asked and said one. */
    val reason: String? = null,
    /** The note of a model's pick, for the swap a model put first. */
    val pick: String? = null,
)

/** POST `/meal-plans/:id/use-up`: swaps that would use up what a plan opens; nothing is written. */
@Serializable
data class UseUpSuggestionsDto(val revision: Int, val suggestions: List<UseUpSuggestionDto>, val aiMeta: AiGenerationMetaDto? = null)

/** POST `/meal-plans/:id/days/:dayId/variations/take`. */
@Serializable
data class TakeDayVariationRequest(val offer: String)

/** PATCH `/meal-plans/:id/meals/:mealId/eaten`. */
@Serializable
data class SetMealEatenRequest(val eaten: Boolean)

/** POST `/meal-plans/:id/days/:date/custom-meal`: a meal of the user's own, what one serving holds, and how many servings it is. */
@Serializable
data class AddCustomMealRequest(val name: String, val mealType: String, val nutrition: NutritionDto, val servings: Double)

/** A meal's scale put back as it was. */
@Serializable
data class RestoreScaleDto(val mealId: String, val scale: Double)

/**
 * POST `/meal-plans/:id/rebalance`: `day` balances [date], or puts back the scales of [restore];
 * `week` balances the days from [date], today where it is left out, to the end of that week of the plan.
 */
@Serializable
data class RebalanceRequest(val scope: String, val date: String? = null, val restore: List<RestoreScaleDto>? = null)

// ── Shopping lists (packages/shared/src/shopping-list.ts) ───────────────────

@Serializable
data class ShoppingListItemDto(
    val id: String,
    val ingredientId: String,
    /** The ingredient's name in the language the request asked for. */
    val name: String,
    /** Platform `ProductCategory` value, e.g. `vegetables` | `dairy` | `pantry` … */
    val category: String,
    /** Total needed, canonical unit, duplicates merged. */
    val totalQuantity: Double,
    /** `g` | `ml` | `piece`. */
    val unit: String,
    /** What to call [unit] when showing the row: `slice` for bread, `clove` for garlic. */
    val displayUnit: String,
    /** Pantry coverage snapshot at generation time (the "from pantry" chip). */
    val alreadyHaveQuantity: Double = 0.0,
    /** totalQuantity - alreadyHaveQuantity, floored at 0. */
    val toBuyQuantity: Double,
    /** What the user has obtained, the pantry's share included; null until the row is first edited. */
    val purchasedQuantity: Double? = null,
    /** Earliest best-before among contributing pantry rows, or null. */
    val pantryBestBefore: String? = null,
    /** Estimated calories contributed by this line (informational). */
    val estimatedCalories: Double,
    /** How the row is bought in packs; null for an ingredient not sold in packs, or nothing left to buy. */
    val packs: ShoppingPacksDto? = null,
    val checked: Boolean = false,
)

/** [count] packs of [size] [sizeUnit], which come to [quantity] in the row's unit. */
@Serializable
data class ShoppingPacksDto(val count: Int, val size: Double, val sizeUnit: String, val quantity: Double)

@Serializable
data class ShoppingListGroupDto(
    val category: String,
    val items: List<ShoppingListItemDto>,
    /** What the entries of the group come to in kilocalories, an estimate as each of theirs is. */
    val estimatedCalories: Double,
)

@Serializable
data class ShoppingListDto(
    val id: String,
    val planId: String,
    val fromDate: String,
    val toDate: String,
    val groups: List<ShoppingListGroupDto>,
    val totalEstimatedCalories: Double,
    /**
     * The plan's menu for these dates no longer needs what the list says: a meal was swapped or
     * resized after the list was made. Generating a list for the same dates again replaces it.
     */
    val stale: Boolean,
    val createdAt: String,
)

/** An ingredient named by its id and its name. */
@Serializable
data class IngredientRefDto(val ingredientId: String, val name: String)

/** A meal of a list's days, as a proposal of the list names it. */
@Serializable
data class ProposalMealDto(val plannedMealId: String, val date: String, val mealType: String, val title: String)

/**
 * Something a shopping list could do without, by its [kind]: `merge`, two rows one ingredient could
 * stand for ([from], [to], the [meals] that use [from], [packsSaved]); or `leftover`, a row whose
 * packs leave more than a fifth of one ([ingredientId], [name], [left] of [packSize] in [unit]).
 * The fields of the other kind are absent.
 */
@Serializable
data class ListProposalDto(
    val kind: String,
    val from: IngredientRefDto? = null,
    val to: IngredientRefDto? = null,
    val meals: List<ProposalMealDto> = emptyList(),
    val packsSaved: Int? = null,
    val ingredientId: String? = null,
    val name: String? = null,
    val unit: String? = null,
    val left: Int? = null,
    val packSize: Double? = null,
    /** A model's sentence on it, where one was asked and said one. */
    val reason: String? = null,
)

/** POST `/shopping-lists/:id/proposals`: what a list could do without; nothing is written. */
@Serializable
data class ListProposalsDto(val planId: String, val proposals: List<ListProposalDto>, val aiMeta: AiGenerationMetaDto? = null)

/** POST `/shopping-lists/generate` — dates default to the plan's full range. */
@Serializable
data class GenerateShoppingListRequest(
    val planId: String,
    val fromDate: String? = null,
    val toDate: String? = null,
)

/**
 * PATCH `/shopping-lists/:id/items/:itemId`: what changed of a row, nothing else. A
 * `purchasedQuantity` that reaches the row's need ticks it; [JsonNull] clears it, which the
 * encoder writes where a Kotlin null is left out.
 */
@Serializable
data class UpdateShoppingItemRequest(
    /** Everything the user has for the row, the part at home included: a number, or [JsonNull]. */
    val purchasedQuantity: JsonElement? = null,
    val checked: Boolean? = null,
    /** What is at home of the row, in the place of what the list took from the pantry. */
    val alreadyHaveQuantity: Double? = null,
)
