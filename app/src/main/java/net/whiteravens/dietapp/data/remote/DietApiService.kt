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
 * Auth endpoints live on [AuthApiService], and the routes a model may take part in on
 * [ModelApiService].
 */
interface DietApiService {

    // ── Account (users/me) ──────────────────────────────────────────────────

    @GET("users/me")
    suspend fun me(): SessionUserDto

    /** Chooses what AI the account has. */
    @PATCH("users/me")
    suspend fun updateSettings(@Body body: UpdateUserSettingsRequest): SessionUserDto

    /** Chooses the unit the account's weights are shown and typed in; null gives the choice back to where the phone is. */
    @PATCH("users/me")
    suspend fun chooseWeightUnit(@Body body: WeightUnitRequest): SessionUserDto

    /** Sends the mail that confirms the account's address again; answered 202. */
    @POST("users/me/email/resend-verification")
    suspend fun resendVerification()

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

    // ── AI ──────────────────────────────────────────────────────────────────

    /** What AI the account has now: the mode it chose, and what is left of the instance's allowance. */
    @GET("ai/quota")
    suspend fun aiQuota(): AiQuotaStatusDto

    /** The providers the account set up, without their keys, the one asked first first. */
    @GET("ai/providers")
    suspend fun aiProviders(): List<AiProviderConfigDto>

    /** Saves the account's configuration of a provider: made the first time, replaced afterwards. */
    @PUT("ai/providers")
    suspend fun saveAiProvider(@Body body: AiProviderConfigRequest): AiProviderConfigDto

    /** Removes a provider of the account, with its key; answered 204, or 404 `AI_CONFIG_NOT_FOUND`. */
    @DELETE("ai/providers/{id}")
    suspend fun removeAiProvider(@Path("id") id: String)

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

    /**
     * The weights of a profile by day in [timeZone] (as IANA names it), the oldest day first: from the
     * day [since] falls on, where it is given.
     */
    @GET("weights/days")
    suspend fun weightDays(
        @Query("profileId") profileId: String,
        @Query("since") since: String?,
        @Query("timeZone") timeZone: String,
    ): List<WeightDayDto>

    /** Logs a weight of a profile, taken now; answers it as the API keeps it. */
    @POST("weights")
    suspend fun logWeight(@Body body: LogWeightRequest): WeightEntryDto

    /** Removes a logged weight; answered 204. */
    @DELETE("weights/{id}")
    suspend fun deleteWeight(@Path("id") id: String)

    // ── Notifications ───────────────────────────────────────────────────────

    /** The notifications of the account's profiles that are not read yet. */
    @GET("notifications/unread")
    suspend fun unreadNotifications(): List<NotificationDto>

    /** Marks a notification read; answered 204. */
    @POST("notifications/{id}/read")
    suspend fun readNotification(@Path("id") id: String)

    /** Marks every notification of the account read, and says how many there were. */
    @POST("notifications/read-all")
    suspend fun readAllNotifications(): UpdatedDto

    // ── Pantry ──────────────────────────────────────────────────────────────

    /** What a profile's pantry holds, in the order of the ingredients' English names, then of their units. */
    @GET("inventory")
    suspend fun pantry(@Query("profileId") profileId: String): List<InventoryItemDto>

    /** Puts some of an ingredient into a profile's pantry; answers the row it is kept in. */
    @POST("inventory")
    suspend fun addToPantry(@Body body: UpsertInventoryItemRequest): InventoryItemDto

    /** Changes a row of the pantry; answers it as it is kept now. */
    @PATCH("inventory/{id}")
    suspend fun changePantryRow(@Path("id") id: String, @Body body: PatchInventoryItemRequest): InventoryItemDto

    /** Takes a row out of the pantry; answered 204. */
    @DELETE("inventory/{id}")
    suspend fun removePantryRow(@Path("id") id: String)

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

    /** Makes a plan again, or the days of it the request names. */
    @POST("meal-plans/{id}/regenerate")
    suspend fun regeneratePlan(@Path("id") id: String, @Body body: RegenerateRequest): MealPlanDto

    /** Makes one day of a plan again. */
    @POST("meal-plans/{id}/days/{dayId}/regenerate")
    suspend fun regenerateDay(@Path("id") id: String, @Path("dayId") dayId: String, @Body body: RegenerateRequest): MealPlanDto

    /** What a change of the settings of a day would do. Nothing is written. */
    @POST("meal-plans/{id}/days/{dayId}/settings/preview")
    suspend fun previewDaySettings(@Path("id") id: String, @Path("dayId") dayId: String, @Body body: DaySettingsDto): DaySettingsPreviewDto

    /** Changes the settings of a day, on the plan at the preview's revision. */
    @PUT("meal-plans/{id}/days/{dayId}/settings")
    suspend fun applyDaySettings(@Path("id") id: String, @Path("dayId") dayId: String, @Body body: DaySettingsDto): MealPlanDto

    /** Takes a meal off its day; the day is balanced again without it. */
    @DELETE("meal-plans/{id}/meals/{mealId}")
    suspend fun removeMeal(@Path("id") id: String, @Path("mealId") mealId: String): RebalanceResultDto

    /** Takes one of the ways a day was offered. */
    @POST("meal-plans/{id}/days/{dayId}/variations/take")
    suspend fun takeDayVariation(@Path("id") id: String, @Path("dayId") dayId: String, @Body body: TakeDayVariationRequest): MealPlanDto

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
