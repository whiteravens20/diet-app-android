// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding for the authenticated Diet App API surface. Every call is
 * held to the copy of the API's contract in contract/ (docs/contracts/api-contract.md).
 * Auth endpoints live on [AuthApiService].
 */
interface DietApiService {

    // ── Account (users/me) ──────────────────────────────────────────────────

    @GET("users/me")
    suspend fun me(): SessionUserDto

    @PATCH("users/me")
    suspend fun updateSettings(@Body body: UpdateUserSettingsRequest): SessionUserDto

    /** Sends the mail that confirms the account's address again; answered 202. */
    @POST("users/me/email/resend-verification")
    suspend fun resendVerification()

    @POST("users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest)

    // ── Profiles ────────────────────────────────────────────────────────────

    @GET("profiles")
    suspend fun profiles(): List<ProfileDto>

    @GET("profiles/{id}")
    suspend fun profile(@Path("id") id: String): ProfileDto

    /** Deterministic calorie target — engine-owned, rendered as-is. */
    @GET("profiles/{id}/calories")
    suspend fun calories(@Path("id") profileId: String): CalorieCalculationDto

    @POST("profiles")
    suspend fun createProfile(@Body body: NewProfileDto): ProfileDto

    @PUT("profiles/{id}")
    suspend fun updateProfile(@Path("id") id: String, @Body body: ProfileUpdateDto): ProfileDto

    @DELETE("profiles/{id}")
    suspend fun deleteProfile(@Path("id") id: String)

    @PATCH("profiles/{id}/preferences")
    suspend fun updatePreferences(@Path("id") id: String, @Body body: ProfilePreferencesPatchDto): ProfileDto

    /** What typed figures come to, and each pace of loss for the same body. Nothing is kept. */
    @POST("profiles/calories")
    suspend fun previewCalories(@Body body: CaloriePreviewRequestDto): CaloriePreviewDto

    // ── Ingredients ─────────────────────────────────────────────────────────

    /**
     * The catalogue's ingredients whose name holds [search], or those [ids] names (comma-separated),
     * in the language of the request.
     */
    @GET("ingredients")
    suspend fun ingredients(@Query("search") search: String? = null, @Query("ids") ids: String? = null): List<IngredientDto>

    // ── Meal plans ──────────────────────────────────────────────────────────

    /** A page of a profile's plans, as summaries; a plan with its days is [mealPlan]. */
    @GET("meal-plans")
    suspend fun mealPlans(
        @Query("profileId") profileId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): MealPlanPageDto

    @GET("meal-plans/{id}")
    suspend fun mealPlan(@Path("id") id: String): MealPlanDto

    @POST("meal-plans/generate")
    suspend fun generatePlan(@Body body: GeneratePlanRequest): MealPlanDto

    /** A recipe offered in the place of a meal, with what the swap would do; nothing is written. */
    @POST("meal-plans/swap-meal/preview")
    suspend fun offerSwap(@Body body: SwapOfferRequest): MealSwapPreviewDto

    /** Takes an offer: the meal gets the recipe, if the plan is still at the offer's revision. */
    @POST("meal-plans/swap-meal")
    suspend fun swapMeal(@Body body: ApplyMealSwapRequest): RebalanceResultDto

    // ── Recipes ─────────────────────────────────────────────────────────────

    @GET("recipes")
    suspend fun recipes(
        @Query("search") search: String? = null,
        @Query("dietType") dietType: String? = null,
        @Query("mealType") mealType: String? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
    ): RecipeSearchPageDto

    @GET("recipes/{id}")
    suspend fun recipe(@Path("id") id: String): RecipeDto

    // ── Shopping lists ──────────────────────────────────────────────────────

    @GET("shopping-lists")
    suspend fun shoppingLists(@Query("planId") planId: String): List<ShoppingListDto>

    @POST("shopping-lists/generate")
    suspend fun generateShoppingList(@Body body: GenerateShoppingListRequest): ShoppingListDto

    @GET("shopping-lists/{id}")
    suspend fun shoppingList(@Path("id") id: String): ShoppingListDto

    /** Returns the full updated list, which we mirror straight into the cache. */
    @PATCH("shopping-lists/{id}/items/{itemId}")
    suspend fun updateShoppingItem(
        @Path("id") listId: String,
        @Path("itemId") itemId: String,
        @Body body: UpdateShoppingItemRequest,
    ): ShoppingListDto
}
