// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The routes on which the API waits for an AI provider before it answers: up to a minute for a
 * model, and it says nothing until then. These calls are given two minutes, where every other call
 * is given half of one. Held to the API's contract as [DietApiService] is.
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

    // ── AI ──────────────────────────────────────────────────────────────────

    /**
     * Asks a provider whether it answers to a key or at an address, and which models it has; nothing
     * is kept. The API gives the provider ten seconds, which a call's ten seconds without a byte
     * would not outlast.
     */
    @POST("ai/test")
    suspend fun testAiConnection(@Body body: AiTestConnectionRequest): AiTestConnectionResponseDto
}
