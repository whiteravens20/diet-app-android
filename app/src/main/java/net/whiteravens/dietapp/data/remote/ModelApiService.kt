// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The routes a model may take part in. The API may wait a minute for the model before it answers,
 * and says nothing until then, so these calls are given two minutes, where every other call is
 * given half of one. Held to the API's contract as [DietApiService] is.
 */
interface ModelApiService {

    // ── Meal plans ──────────────────────────────────────────────────────────

    /** Makes a substitution: the meal gets a recipe of its own with the substitute in it, its text reworded by a model where asked. */
    @POST("meal-plans/swap-ingredient/apply")
    suspend fun substitute(@Body body: SwapIngredientRequest): SwapIngredientResponseDto

    /** An ingredient a model picked from the engine's pool for the line; nothing is written. */
    @POST("meal-plans/swap-ingredient/ai-suggest")
    suspend fun suggestSubstitute(@Body body: AiSuggestIngredientRequest): AiSuggestIngredientResponseDto

    /** A recipe for the meal that a model picked from those the engine puts first; nothing is written. */
    @POST("meal-plans/ai-swap-meal/preview")
    suspend fun aiOfferSwap(@Body body: AiSwapOfferRequest): AiMealSwapPreviewDto

    /** Other ways to fill a day, weighed by a model where [WeighRequest.weigh]; nothing is written. */
    @POST("meal-plans/{id}/days/{dayId}/variations")
    suspend fun dayVariations(@Path("id") id: String, @Path("dayId") dayId: String, @Body body: WeighRequest): DayVariationsDto

    /** Swaps that would use up what the plan opens, weighed by a model where [WeighRequest.weigh]; nothing is written. */
    @POST("meal-plans/{id}/use-up")
    suspend fun useUp(@Path("id") id: String, @Body body: WeighRequest): UseUpSuggestionsDto

    // ── Recipes ─────────────────────────────────────────────────────────────

    /** Asks a model for a recipe of the account's own, from the choices of [body]. */
    @POST("recipes/drafts/from-prompt")
    suspend fun draftRecipe(@Body body: AiDraftRecipeRequest): AiDraftRecipeResponseDto

    // ── Shopping lists ──────────────────────────────────────────────────────

    /** What a list could do without, weighed by a model where [WeighRequest.weigh]; nothing is written. */
    @POST("shopping-lists/{id}/proposals")
    suspend fun listProposals(@Path("id") id: String, @Body body: WeighRequest): ListProposalsDto
}
