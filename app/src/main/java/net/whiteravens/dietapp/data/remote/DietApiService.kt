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

    /** Chooses the unit the account's weights are shown and typed in; null gives the choice back to where the phone is. */
    @PATCH("users/me")
    suspend fun chooseWeightUnit(@Body body: WeightUnitRequest): SessionUserDto

    /** Sends the mail that confirms the account's address again; answered 202. */
    @POST("users/me/email/resend-verification")
    suspend fun resendVerification()

    @POST("users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    /** Asks for another address: a mail with a link goes to it, and the link makes the change; answered 202. */
    @POST("users/me/email")
    suspend fun changeEmail(@Body body: ChangeEmailRequest)

    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest)

    /** Whether the account signs in with a second step, since when, and how many recovery codes it has left. */
    @GET("users/me/second-factor")
    suspend fun secondFactor(): SecondFactorStateDto

    /** A secret to set an authenticator app up with, for the password; it counts from its first code on. */
    @POST("users/me/second-factor/setup")
    suspend fun proposeSecondFactor(@Body body: SecondFactorSetupRequest): SecondFactorSetupDto

    /** The first code: signing in takes one from now on; answered with the recovery codes, this once. */
    @POST("users/me/second-factor")
    suspend fun beginSecondFactor(@Body body: SecondFactorConfirmRequest): RecoveryCodesDto

    /** New recovery codes in the place of the ones the account had, for the password and a code. */
    @POST("users/me/second-factor/recovery-codes")
    suspend fun newRecoveryCodes(@Body body: SecondFactorProofRequest): RecoveryCodesDto

    /** Switches the second step off, for the password and a code; answered 204. */
    @HTTP(method = "DELETE", path = "users/me/second-factor", hasBody = true)
    suspend fun endSecondFactor(@Body body: SecondFactorProofRequest)

    /** The open sessions of the account, the one used last first. */
    @GET("users/me/sessions")
    suspend fun sessions(): List<AccountSessionDto>

    /** Ends one session of the account; answered 204, or 404 `SESSION_NOT_FOUND`. */
    @DELETE("users/me/sessions/{id}")
    suspend fun endSession(@Path("id") id: String)

    /** Ends every session of the account, the one asking included; answered 204. */
    @DELETE("users/me/sessions")
    suspend fun endAllSessions()

    // ── Favourites ──────────────────────────────────────────────────────────

    /**
     * The recipes a profile marked, each with its recipe: narrowed to those whose title holds
     * [search], those for [mealType], those marked as [sentiment] and those under [tag], in the
     * order [sort] names (`newest`, `title`, `calories`; the mark made last first where it is left out).
     */
    @GET("favorites")
    suspend fun favorites(
        @Query("profileId") profileId: String,
        @Query("search") search: String? = null,
        @Query("mealType") mealType: String? = null,
        @Query("sentiment") sentiment: String? = null,
        @Query("tag") tag: String? = null,
        @Query("sort") sort: String? = null,
    ): List<MarkedRecipeDto>

    /**
     * Marks a recipe for a profile; marking it again replaces what was said of it before. The mark
     * the API answers with is not read: a class nothing reads is one R8 takes out of a release build.
     */
    @POST("favorites")
    suspend fun markRecipe(@Body body: AddFavoriteRequest)

    /** Takes a profile's mark off a recipe; 404 `FAVORITE_NOT_FOUND` where there was none. */
    @DELETE("favorites/{profileId}/{recipeId}")
    suspend fun unmarkRecipe(@Path("profileId") profileId: String, @Path("recipeId") recipeId: String)

    @GET("favorite-sets")
    suspend fun favoriteSets(@Query("profileId") profileId: String): List<FavoriteSetDto>

    /** Keeps a set of a profile: a recipe for each meal it names. */
    @POST("favorite-sets")
    suspend fun createFavoriteSet(@Body body: CreateFavoriteSetRequest): FavoriteSetDto

    /** Says a set's name and its recipes anew. */
    @PATCH("favorite-sets/{id}")
    suspend fun updateFavoriteSet(@Path("id") id: String, @Body body: UpdateFavoriteSetRequest): FavoriteSetDto

    @DELETE("favorite-sets/{id}")
    suspend fun deleteFavoriteSet(@Path("id") id: String)

    /** Puts the recipes of a set on days of a plan, each in the place of the day's meal of its kind; answers the plan. */
    @POST("favorite-sets/{id}/apply")
    suspend fun applyFavoriteSet(@Path("id") id: String, @Body body: ApplyFavoriteSetRequest): MealPlanDto

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

    // ── Weights ─────────────────────────────────────────────────────────────

    /** The weights logged for a profile, the newest first: those taken [since] a moment, where it is given. */
    @GET("weights")
    suspend fun weights(@Query("profileId") profileId: String, @Query("since") since: String? = null): List<WeightEntryDto>

    /** Logs a weight of a profile, taken now; answers it as the API keeps it. */
    @POST("weights")
    suspend fun logWeight(@Body body: LogWeightRequest): WeightEntryDto

    /** Removes a logged weight; answered 204. */
    @DELETE("weights/{id}")
    suspend fun deleteWeight(@Path("id") id: String)

    // ── Ingredients ─────────────────────────────────────────────────────────

    /**
     * The catalogue's ingredients whose name holds [search], or those [ids] names (comma-separated),
     * in the language of the request.
     */
    @GET("ingredients")
    suspend fun ingredients(@Query("search") search: String? = null, @Query("ids") ids: String? = null): List<IngredientDto>

    // ── Meal plans ──────────────────────────────────────────────────────────

    /**
     * A page of a profile's plans, as summaries, narrowed to those [status] names and those holding
     * a day from [from] to [to]; a plan with its days is [mealPlan].
     */
    @GET("meal-plans")
    suspend fun mealPlans(
        @Query("profileId") profileId: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("status") status: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): MealPlanPageDto

    /** What a profile's plan of the moment comes to: the one under way, else the next, else the last. */
    @GET("meal-plans/overview")
    suspend fun planOverview(@Query("profileId") profileId: String): PlanOverviewAnswerDto

    /** The days of a profile's plans from [from] to [to], for a calendar. */
    @GET("meal-plans/calendar")
    suspend fun planCalendar(
        @Query("profileId") profileId: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): PlanCalendarDto

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

    /** The ingredients the catalogue's own rules name in the place of those of a planned meal. */
    @GET("meal-plans/{id}/meals/{mealId}/substitutes")
    suspend fun substitutes(@Path("id") id: String, @Path("mealId") mealId: String): List<SubstituteSuggestionDto>

    /** What a substitution would do to its meal; nothing is written. */
    @POST("meal-plans/swap-ingredient/preview")
    suspend fun previewSubstitution(@Body body: SwapIngredientRequest): SwapPreviewDto

    /** Makes a substitution: the meal gets a recipe of its own with the substitute in it. */
    @POST("meal-plans/swap-ingredient/apply")
    suspend fun substitute(@Body body: SwapIngredientRequest): SwapIngredientResponseDto

    /** An ingredient a model picked from the engine's pool for the line; nothing is written. */
    @POST("meal-plans/swap-ingredient/ai-suggest")
    suspend fun suggestSubstitute(@Body body: AiSuggestIngredientRequest): AiSuggestIngredientResponseDto

    /** A recipe for the meal that a model picked from those the engine puts first; nothing is written. */
    @POST("meal-plans/ai-swap-meal/preview")
    suspend fun aiOfferSwap(@Body body: AiSwapOfferRequest): AiMealSwapPreviewDto

    /** Makes a plan again, or the days of it the request names. */
    @POST("meal-plans/{id}/regenerate")
    suspend fun regeneratePlan(@Path("id") id: String, @Body body: RegenerateRequest): MealPlanDto

    /** Makes one day of a plan again. */
    @POST("meal-plans/{id}/days/{dayId}/regenerate")
    suspend fun regenerateDay(@Path("id") id: String, @Path("dayId") dayId: String, @Body body: RegenerateRequest): MealPlanDto

    /** Other ways to fill a day, weighed by a model where [WeighRequest.weigh]; nothing is written. */
    @POST("meal-plans/{id}/days/{dayId}/variations")
    suspend fun dayVariations(@Path("id") id: String, @Path("dayId") dayId: String, @Body body: WeighRequest): DayVariationsDto

    /** Takes one of the ways a day was offered. */
    @POST("meal-plans/{id}/days/{dayId}/variations/take")
    suspend fun takeDayVariation(@Path("id") id: String, @Path("dayId") dayId: String, @Body body: TakeDayVariationRequest): MealPlanDto

    /** Swaps that would use up what the plan opens, weighed by a model where [WeighRequest.weigh]; nothing is written. */
    @POST("meal-plans/{id}/use-up")
    suspend fun useUp(@Path("id") id: String, @Body body: WeighRequest): UseUpSuggestionsDto

    /** Marks a meal eaten or not; the day is balanced again around it. */
    @PATCH("meal-plans/{id}/meals/{mealId}/eaten")
    suspend fun setMealEaten(@Path("id") id: String, @Path("mealId") mealId: String, @Body body: SetMealEatenRequest): RebalanceResultDto

    /** Adds a meal of the user's own to the day of [date]; a day the plan skipped is skipped no longer. */
    @POST("meal-plans/{id}/days/{date}/custom-meal")
    suspend fun addCustomMeal(@Path("id") id: String, @Path("date") date: String, @Body body: AddCustomMealRequest): RebalanceResultDto

    @POST("meal-plans/{id}/rebalance")
    suspend fun rebalancePlan(@Path("id") id: String, @Body body: RebalanceRequest): RebalanceResultDto

    /** Deletes a plan, and its shopping lists with it. */
    @DELETE("meal-plans/{id}")
    suspend fun deletePlan(@Path("id") id: String)

    // ── Recipes ─────────────────────────────────────────────────────────────

    /**
     * The recipe library, the shared recipes and the account's own, narrowed as asked, a page at a
     * time. [ingredientIds] and [withoutAllergens] are lists parted by commas; [marked] and
     * [favoriteIngredients] are about the profile [profileId].
     */
    @GET("recipes")
    suspend fun recipes(
        @Query("search") search: String? = null,
        @Query("dietType") dietType: String? = null,
        @Query("mealType") mealType: String? = null,
        @Query("difficulty") difficulty: String? = null,
        @Query("maxCalories") maxCalories: Int? = null,
        @Query("maxPrepMinutes") maxPrepMinutes: Int? = null,
        @Query("cuisine") cuisine: String? = null,
        @Query("minProtein") minProtein: Double? = null,
        @Query("maxFat") maxFat: Double? = null,
        @Query("maxCarbs") maxCarbs: Double? = null,
        @Query("ingredientIds") ingredientIds: String? = null,
        @Query("withoutAllergens") withoutAllergens: String? = null,
        @Query("profileId") profileId: String? = null,
        @Query("marked") marked: String? = null,
        @Query("favoriteIngredients") favoriteIngredients: Boolean? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
    ): RecipeSearchPageDto

    /** Asks a model for a recipe of the account's own, from the choices of [body]. */
    @POST("recipes/drafts/from-prompt")
    suspend fun draftRecipe(@Body body: AiDraftRecipeRequest): AiDraftRecipeResponseDto

    /** The account's own recipes, a page at a time: those a model wrote for it and those a substitution left. */
    @GET("recipes/mine")
    suspend fun myRecipes(@Query("page") page: Int? = null, @Query("pageSize") pageSize: Int? = null): RecipeSearchPageDto

    /** Puts a recipe of one's own before the curators, for the library everyone shares. */
    @POST("recipes/{id}/submit")
    suspend fun submitRecipe(@Path("id") id: String)

    /** Deletes a recipe of one's own; the plans that hold it keep it. */
    @DELETE("recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: String)

    @GET("recipes/{id}")
    suspend fun recipe(@Path("id") id: String): RecipeDto

    // ── Shopping lists ──────────────────────────────────────────────────────

    @GET("shopping-lists")
    suspend fun shoppingLists(@Query("planId") planId: String): List<ShoppingListDto>

    @POST("shopping-lists/generate")
    suspend fun generateShoppingList(@Body body: GenerateShoppingListRequest): ShoppingListDto

    @GET("shopping-lists/{id}")
    suspend fun shoppingList(@Path("id") id: String): ShoppingListDto

    /** What a list could do without, weighed by a model where [WeighRequest.weigh]; nothing is written. */
    @POST("shopping-lists/{id}/proposals")
    suspend fun listProposals(@Path("id") id: String, @Body body: WeighRequest): ListProposalsDto

    /** Deletes a shopping list; what was obtained for days still ahead goes into the pantry. */
    @DELETE("shopping-lists/{id}")
    suspend fun deleteShoppingList(@Path("id") id: String)

    /** Returns the full updated list, which we mirror straight into the cache. */
    @PATCH("shopping-lists/{id}/items/{itemId}")
    suspend fun updateShoppingItem(
        @Path("id") listId: String,
        @Path("itemId") itemId: String,
        @Body body: UpdateShoppingItemRequest,
    ): ShoppingListDto
}
