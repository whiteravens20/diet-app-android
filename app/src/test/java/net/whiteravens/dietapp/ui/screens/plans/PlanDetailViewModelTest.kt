// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.ApplyMealSwapRequest
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.MealSwapPreviewDto
import net.whiteravens.dietapp.data.remote.RebalanceResultDto
import net.whiteravens.dietapp.data.remote.SwapOfferRequest
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.fixture
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private fun model(api: DietApiService) =
        PlanDetailViewModel(SavedStateHandle(mapOf("planId" to plan.id)), MealPlanRepository(api, NoCache, json))

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
}
