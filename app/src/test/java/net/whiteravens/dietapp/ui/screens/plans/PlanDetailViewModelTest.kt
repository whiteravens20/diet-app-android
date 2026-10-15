// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.AiGenerationMetaDto
import net.whiteravens.dietapp.data.remote.AiMealSwapPreviewDto
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.ApplyMealSwapRequest
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.MealSwapPreviewDto
import net.whiteravens.dietapp.data.remote.NutritionDto
import net.whiteravens.dietapp.data.remote.RebalanceChangeDto
import net.whiteravens.dietapp.data.remote.RebalanceRequest
import net.whiteravens.dietapp.data.remote.RebalanceResultDto
import net.whiteravens.dietapp.data.remote.RebalanceSummaryDto
import net.whiteravens.dietapp.data.remote.RestoreScaleDto
import net.whiteravens.dietapp.data.remote.SetMealEatenRequest
import net.whiteravens.dietapp.data.remote.SwapOfferRequest
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.ScaleChange
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.domain.SwapAsk
import net.whiteravens.dietapp.testing.fixture
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * A meal swapped on the plan's screen, the way the web application swaps one: a recipe is offered,
 * another can be asked for, and the swap is written only when the offer is taken, with the plan's
 * revision the offer was made from. The answers are what a running backend answered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanDetailViewModelTest {
    private val json = AppModule.json()
    private val plan = json.decodeFromString<MealPlanDto>(fixture("meal-plan"))
    private val preview = json.decodeFromString<MealSwapPreviewDto>(fixture("meal-plan-swap-preview"))
    private val swapped = json.decodeFromString<RebalanceResultDto>(fixture("meal-plan-swap-apply"))
    private val meal = plan.days.first().meals.first { it.recipe != null }

    @Before
    fun mainThread() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    /** A cache that keeps nothing. */
    private object NoCache : CacheDao {
        override suspend fun upsertPlanList(list: CachedPlanList) = Unit
        override suspend fun planList(profileId: String): CachedPlanList? = null
        override suspend fun deletePlanList(profileId: String) = Unit
        override suspend fun upsertMealPlan(plan: CachedMealPlan) = Unit
        override suspend fun mealPlan(id: String): CachedMealPlan? = null
        override suspend fun deleteMealPlan(id: String) = Unit
        override suspend fun upsertShoppingList(list: CachedShoppingList) = Unit
        override suspend fun shoppingList(id: String): CachedShoppingList? = null
        override suspend fun shoppingListsForPlan(planId: String) = emptyList<CachedShoppingList>()
        override suspend fun deleteShoppingList(id: String) = Unit
    }

    private val liked = listOf(
        MarkedRecipe("oats", "Oats", listOf("breakfast"), "marked", emptyList()),
        MarkedRecipe("liver", "Liver", listOf("lunch"), "avoid", emptyList()),
    )
    private val favorites = mockk<FavoriteRepository>().also { coEvery { it.marked(plan.profileId) } returns liked }
    private val users = mockk<UserRepository>().also { coEvery { it.me() } returns account("admin") }

    private fun account(aiMode: String) = SessionUser("u", "ala@example.test", "Ala", true, "en", "system", aiMode = aiMode)

    private fun model(api: DietApiService, at: Map<String, String> = mapOf("planId" to plan.id)) =
        PlanDetailViewModel(SavedStateHandle(at), MealPlanRepository(api, NoCache, json), favorites, users)

    private fun refused(status: Int, code: String) = HttpException(
        Response.error<Any>(
            status,
            """{"statusCode":$status,"error":"$code","message":"Refused."}""".toResponseBody("application/json".toMediaType()),
        ),
    )

    @Test
    fun `a swap is offered, another is asked for without the first, and the one taken is written with its revision`() {
        val api = mockk<DietApiService>()
        val offers = mutableListOf<SwapOfferRequest>()
        val taken = slot<ApplyMealSwapRequest>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.offerSwap(capture(offers)) } returns preview
        coEvery { api.swapMeal(capture(taken)) } returns swapped
        val model = model(api)

        model.offerSwap(meal.id)
        model.anotherOffer()
        val offer = model.state.value.offer
        assertNotNull(offer)
        model.takeOffer()

        assertEquals(listOf(emptyList(), listOf(preview.recipe.id)), offers.map { it.skip })
        assertEquals(meal.id, taken.captured.plannedMealId)
        assertEquals(offer!!.recipeId, taken.captured.recipeId)
        assertEquals(preview.revision, taken.captured.revision)
        assertNull(model.state.value.offer)
        assertEquals(swapped.plan.id, model.state.value.plan?.id)
    }

    @Test
    fun `a swap refused because the plan changed meanwhile loads the plan again and says why`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.offerSwap(any()) } returns preview
        coEvery { api.swapMeal(any()) } throws refused(409, "PLAN_CHANGED")
        val model = model(api)

        model.offerSwap(meal.id)
        model.takeOffer()

        assertNull(model.state.value.offer)
        assertEquals("PLAN_CHANGED", (model.state.value.error as ApiException).code)
        coVerify(exactly = 2) { api.mealPlan(plan.id) }
    }

    @Test
    fun `a plan opened at a day of the calendar shows that day, and one opened from the list its first`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        val day = plan.days[3]

        val atDay = model(api, mapOf("planId" to plan.id, "day" to day.date))
        val fromList = model(api)

        assertEquals(3, atDay.state.value.selectedDay)
        assertEquals(0, fromList.state.value.selectedDay)
    }

    /** A day balanced around a meal: one meal scaled down, and the day still over its target. */
    private val scaled = RebalanceSummaryDto(
        scope = "day",
        feasibility = "best-effort",
        changes = listOf(RebalanceChangeDto(meal.id, before = 1.0, after = 0.8)),
        unabsorbedKcal = 0.0,
        macrosBefore = NutritionDto(2300.0, 100.0, 80.0, 250.0),
        macrosAfter = NutritionDto(2100.0, 95.0, 75.0, 230.0),
    )

    @Test
    fun `a meal marked eaten shows the plan it left, and says how its day was balanced`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.setMealEaten(plan.id, meal.id, SetMealEatenRequest(true)) } returns swapped.copy(rebalance = scaled)
        val model = model(api)

        model.setEaten(meal.id, true)

        assertEquals(swapped.plan.revision, model.state.value.plan?.revision)
        val said = model.state.value.rebalance!!
        assertEquals(listOf(ScaleChange(meal.id, 1.0, 0.8)), said.changes)
        assertTrue(said.bestEffort)
        assertFalse(model.state.value.changing)
    }

    @Test
    fun `a swap taken says how its day was balanced, until that is put away`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.offerSwap(any()) } returns preview
        coEvery { api.swapMeal(any()) } returns swapped.copy(rebalance = scaled)
        val model = model(api)

        model.offerSwap(meal.id)
        model.takeOffer()

        assertEquals(listOf(ScaleChange(meal.id, 1.0, 0.8)), model.state.value.rebalance?.changes)

        model.dismissRebalance()

        assertNull(model.state.value.rebalance)
    }

    @Test
    fun `the rest of the week is balanced from the card, and the way back puts each meal it scaled back as it was, saying nothing of itself`() {
        val api = mockk<DietApiService>()
        val asked = mutableListOf<RebalanceRequest>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.offerSwap(any()) } returns preview
        coEvery { api.swapMeal(any()) } returns swapped.copy(rebalance = scaled)
        coEvery { api.rebalancePlan(plan.id, capture(asked)) } returns swapped.copy(rebalance = scaled.copy(scope = "week")) andThen swapped
        val model = model(api)

        model.offerSwap(meal.id)
        model.takeOffer()
        model.rebalanceWeek()

        assertEquals(RebalanceRequest(scope = "week"), asked[0])
        assertEquals("week", model.state.value.rebalance?.scope)

        model.undoRebalance()

        assertEquals(RebalanceRequest(scope = "day", restore = listOf(RestoreScaleDto(meal.id, 1.0))), asked[1])
        assertNull(model.state.value.rebalance)
    }

    @Test
    fun `a deleted plan closes its screen`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.deletePlan(plan.id) } just runs
        val model = model(api)

        model.delete()

        assertTrue(model.state.value.deleted)
    }

    @Test
    fun `an eaten mark refused because the plan changed meanwhile loads the plan again and says why`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.setMealEaten(any(), any(), any()) } throws refused(409, "PLAN_CHANGED")
        val model = model(api)

        model.setEaten(meal.id, true)

        assertEquals("PLAN_CHANGED", (model.state.value.error as ApiException).code)
        assertNull(model.state.value.rebalance)
        coVerify(exactly = 2) { api.mealPlan(plan.id) }
    }

    @Test
    fun `the favourites a meal can be swapped to leave out those marked to avoid, and an account without a model says so`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { users.me() } returns account("none")

        val model = model(api)

        assertEquals(listOf("oats"), model.state.value.liked.map { it.recipeId })
        assertEquals(false, model.state.value.aiOn)
    }

    @Test
    fun `a favourite picked by hand is asked for with its recipe, the diet no bar where all favourites were shown`() {
        val api = mockk<DietApiService>()
        val offers = mutableListOf<SwapOfferRequest>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.offerSwap(capture(offers)) } returns preview
        val model = model(api)

        model.offerSwap(meal.id, SwapAsk("favorite", favoriteRecipeId = "oats", allowOffDiet = true))

        val asked = offers.single()
        assertEquals("favorite", asked.strategy)
        assertEquals("oats", asked.favoriteRecipeId)
        assertTrue(asked.allowOffDiet)
        assertEquals("favorite", model.state.value.offer?.ask?.strategy)
    }

    @Test
    fun `an offer a model was asked for says why the engine picked, or that the model did and why`() {
        val api = mockk<DietApiService>()
        val byEngine = AiMealSwapPreviewDto(
            recipe = preview.recipe,
            servings = preview.servings,
            before = preview.before,
            after = preview.after,
            dayBefore = preview.dayBefore,
            dayAfter = preview.dayAfter,
            calorieTarget = preview.calorieTarget,
            revision = preview.revision,
            aiMeta = AiGenerationMetaDto(usedDeterministicFallback = true, fallbackReason = "quota_exhausted"),
            pick = null,
        )
        val byModel = byEngine.copy(aiMeta = AiGenerationMetaDto(provider = "openrouter", usedDeterministicFallback = false, reason = "Lighter."), pick = "note")
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.aiOfferSwap(any()) } returns byEngine andThen byModel
        val model = model(api)

        model.offerSwap(meal.id, SwapAsk("ai"))

        assertEquals("quota_exhausted", model.state.value.offer?.fallbackReason)
        assertNull(model.state.value.offer?.pick)

        model.anotherOffer()

        assertNull(model.state.value.offer?.fallbackReason)
        assertEquals("note", model.state.value.offer?.pick)
        assertEquals("Lighter.", model.state.value.offer?.reason)
        assertEquals("ai", model.state.value.offer?.ask?.strategy)
    }
}

