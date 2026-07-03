package net.whiteravens.dietapp.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding for the authenticated Diet App API surface. Endpoints and
 * payload shapes mirror the web platform's contract — see
 * docs/contracts/api-contract.md. Auth endpoints live on [AuthApiService].
 */
interface DietApiService {

    // ── Account (users/me) ──────────────────────────────────────────────────

    @GET("users/me")
    suspend fun me(): SessionUserDto

    @PATCH("users/me")
    suspend fun updateSettings(@Body body: UpdateUserSettingsRequest): SessionUserDto

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

    // ── Meal plans ──────────────────────────────────────────────────────────

    @GET("meal-plans")
    suspend fun mealPlans(@Query("profileId") profileId: String): List<MealPlanDto>

    @GET("meal-plans/{id}")
    suspend fun mealPlan(@Path("id") id: String): MealPlanDto

    @POST("meal-plans/generate")
    suspend fun generatePlan(@Body body: GeneratePlanRequest): MealPlanDto

    @POST("meal-plans/swap-meal")
    suspend fun swapMeal(@Body body: SwapMealRequest): RebalanceResultDto

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
