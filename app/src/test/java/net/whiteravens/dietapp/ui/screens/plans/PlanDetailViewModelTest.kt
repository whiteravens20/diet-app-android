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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.AiGenerationMetaDto
import net.whiteravens.dietapp.data.remote.AiMealSwapPreviewDto
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.ApplyFavoriteSetRequest
import net.whiteravens.dietapp.data.remote.ApplyMealSwapRequest
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.data.remote.MealSwapPreviewDto
import net.whiteravens.dietapp.data.remote.NutritionDto
import net.whiteravens.dietapp.data.remote.PackTallyDto
import net.whiteravens.dietapp.data.remote.PacksBeforeAfterDto
import net.whiteravens.dietapp.data.remote.RebalanceChangeDto
import net.whiteravens.dietapp.data.remote.RebalanceRequest
import net.whiteravens.dietapp.data.remote.RebalanceResultDto
import net.whiteravens.dietapp.data.remote.RebalanceSummaryDto
import net.whiteravens.dietapp.data.remote.RecipeRefDto
import net.whiteravens.dietapp.data.remote.RegenerateRequest
import net.whiteravens.dietapp.data.remote.RestoreScaleDto
import net.whiteravens.dietapp.data.remote.SetMealEatenRequest
import net.whiteravens.dietapp.data.remote.SwapOfferRequest
import net.whiteravens.dietapp.data.remote.UseUpSuggestionDto
import net.whiteravens.dietapp.data.remote.UseUpSuggestionsDto
import net.whiteravens.dietapp.data.remote.UseUpTakeDto
import net.whiteravens.dietapp.data.remote.WeighRequest
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.Reroll
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
import java.io.IOException

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
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

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
        MarkedRecipe("oats", "Oats", listOf("breakfast"), "favorite", emptyList()),
        MarkedRecipe("liver", "Liver", listOf("lunch"), "avoid", emptyList()),
    )
    private val favorites = mockk<FavoriteRepository>().also { coEvery { it.marked(plan.profileId) } returns liked }
    private val users = mockk<UserRepository>().also { coEvery { it.me() } returns account("admin") }

    private fun account(aiMode: String) = SessionUser("u", "ala@example.test", "Ala", true, "en", aiMode = aiMode)

    private fun model(api: DietApiService, at: Map<String, String> = mapOf("planId" to plan.id)) =
        PlanDetailViewModel(SavedStateHandle(at), MealPlanRepository(api, NoCache, json), favorites, users)

    private fun refused(status: Int, code: String, details: String? = null) = HttpException(
        Response.error<Any>(
            status,
            """{"statusCode":$status,"error":"$code","message":"Refused."${details?.let { ",\"details\":$it" }.orEmpty()}}""".toResponseBody("application/json".toMediaType()),
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
    fun `swaps put first what the pantry covers until the user says not to`() {
        val api = mockk<DietApiService>()
        val offers = mutableListOf<SwapOfferRequest>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.offerSwap(capture(offers)) } returns preview
        val model = model(api)

        model.offerSwap(meal.id)
        model.dismissOffer()
        model.setSwapWithPantry(false)
        model.offerSwap(meal.id)

        assertEquals(listOf(true, false), offers.map { it.respectInventory })
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

    @Test
    fun `a plan made again shows the plan the API made`() {
        val api = mockk<DietApiService>()
        val asked = mutableListOf<RegenerateRequest>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.regenerateDay(plan.id, plan.days[2].id, capture(asked)) } returns swapped.plan
        val model = model(api)

        model.reroll(Reroll(dayId = plan.days[2].id, replaceEaten = true))

        assertEquals(RegenerateRequest(replaceEaten = true), asked.single())
        assertEquals(swapped.plan.revision, model.state.value.plan?.revision)
        assertFalse(model.state.value.changing)
    }

    @Test
    fun `a re-roll refused for a lock that no longer holds, or for days that have all passed, is asked again with that allowed`() {
        val api = mockk<DietApiService>()
        val asked = mutableListOf<RegenerateRequest>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.regeneratePlan(plan.id, capture(asked)) } throws refused(400, "LOCKED_RECIPE_INELIGIBLE") andThenThrows refused(409, "NOTHING_TO_REGENERATE") andThen plan
        val model = model(api)

        model.reroll(Reroll(from = "2026-10-13", to = "2026-10-14"))

        assertEquals(PlanDetailViewModel.DROP_LOCKS, model.state.value.refused?.allow)
        assertEquals("LOCKED_RECIPE_INELIGIBLE", (model.state.value.error as ApiException).code)

        model.allowRefused()

        assertEquals(PlanDetailViewModel.INCLUDE_PAST, model.state.value.refused?.allow)

        model.allowRefused()

        assertEquals(RegenerateRequest(from = "2026-10-13", to = "2026-10-14", includePast = true, dropIneligibleLocks = true), asked.last())
        assertNull(model.state.value.refused)
        assertNull(model.state.value.error)
    }

    @Test
    fun `a re-roll that could only go outside the plan's limits says what stands in the way, and is asked again with them let give way in their order`() {
        val api = mockk<DietApiService>()
        val asked = mutableListOf<RegenerateRequest>()
        val needs = """{"needs":["weekly_cap","cook_time"],"meals":[{"date":"2026-10-13","slot":"lunch","gaveWay":["weekly_cap"]}]}"""
        coEvery { api.mealPlan(plan.id) } returns plan.copy(options = plan.options.copy(allowRelaxations = listOf("ingredient_cap")))
        coEvery { api.regeneratePlan(plan.id, capture(asked)) } throws refused(422, "PLAN_NEEDS_RELAXATION", needs) andThen plan
        val model = model(api)

        model.reroll(Reroll())

        val inTheWay = model.state.value.inTheWay!!
        assertEquals(listOf("weekly_cap", "cook_time"), inTheWay.needed.needs)
        // Left out by the window: the limits the plan may already let give way.
        assertEquals(listOf("ingredient_cap"), inTheWay.allowed)
        assertNull(model.state.value.error)

        model.letGiveWay()

        assertEquals(listOf("cook_time", "weekly_cap", "ingredient_cap"), asked.last().allowRelaxations)
        assertNull(model.state.value.inTheWay)
    }

    /** A swap the API found that would use up a pack the plan opens, for the plan at [revision]; put first by a model where [weighed]. */
    private fun usingUp(revision: Int, weighed: Boolean = false) = UseUpSuggestionsDto(
        revision = revision,
        suggestions = listOf(
            UseUpSuggestionDto(
                plannedMealId = meal.id,
                date = plan.days.first().date,
                mealType = meal.mealType,
                from = RecipeRefDto(meal.recipe!!.id, meal.recipe!!.title),
                to = RecipeRefDto("recipe-feta", "Feta salad"),
                servings = 1.0,
                before = NutritionDto(500.0, 30.0, 20.0, 50.0),
                after = NutritionDto(480.0, 28.0, 22.0, 45.0),
                takes = listOf(UseUpTakeDto("feta", "Feta", "g", 60, 40)),
                packs = PacksBeforeAfterDto(PackTallyDto(400.0, 260.0, 1), PackTallyDto(400.0, 320.0, 0)),
                reason = "Uses the feta.".takeIf { weighed },
                pick = "note".takeIf { weighed },
            ),
        ),
        aiMeta = AiGenerationMetaDto(provider = "openrouter", usedDeterministicFallback = false).takeIf { weighed },
    )

    @Test
    fun `swaps that would use up the opened packs are weighed by a model on request, and the one taken is written with the revision they were found for and the model's note`() {
        val api = mockk<DietApiService>()
        val asked = mutableListOf<WeighRequest>()
        val taken = slot<ApplyMealSwapRequest>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.useUp(plan.id, capture(asked)) } returns usingUp(plan.revision) andThen usingUp(plan.revision, weighed = true)
        coEvery { api.swapMeal(capture(taken)) } returns swapped.copy(rebalance = scaled)
        val model = model(api)

        model.findUseUp()
        model.findUseUp(weigh = true)
        val found = model.state.value.useUp!!
        model.takeUseUp(found.swaps.single())

        assertEquals(listOf(WeighRequest(false), WeighRequest(true)), asked)
        assertTrue(found.weighed)
        assertEquals("Uses the feta.", found.swaps.single().reason)
        assertEquals(ApplyMealSwapRequest(plan.id, meal.id, "recipe-feta", plan.revision, "random", pick = "note"), taken.captured)
        assertNull(model.state.value.useUp)
        assertEquals(swapped.plan.revision, model.state.value.plan?.revision)
        assertEquals(listOf(ScaleChange(meal.id, 1.0, 0.8)), model.state.value.rebalance?.changes)
    }

    @Test
    fun `an account without a model is not asked to weigh, and swaps found for the plan before it changed are not offered`() {
        val api = mockk<DietApiService>()
        val asked = mutableListOf<WeighRequest>()
        coEvery { users.me() } returns account("none")
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.useUp(plan.id, capture(asked)) } returns usingUp(plan.revision)
        coEvery { api.setMealEaten(any(), any(), any()) } returns swapped
        val model = model(api)

        model.findUseUp(weigh = true)

        assertEquals(listOf(WeighRequest(false)), asked)
        assertEquals(1, model.state.value.useUp?.swaps?.size)

        model.setEaten(meal.id, true)

        assertNull(model.state.value.useUp)
    }

    @Test
    fun `a use-up swap refused because the plan changed meanwhile puts the swaps away, loads the plan again and says why`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { api.useUp(plan.id, any()) } returns usingUp(plan.revision)
        coEvery { api.swapMeal(any()) } throws refused(409, "PLAN_CHANGED")
        val model = model(api)

        model.findUseUp()
        model.takeUseUp(model.state.value.useUp!!.swaps.single())

        assertNull(model.state.value.useUp)
        assertEquals("PLAN_CHANGED", (model.state.value.error as ApiException).code)
        coVerify(exactly = 2) { api.mealPlan(plan.id) }
    }

    private val weekday = FavoriteSet("weekday", "Weekday", mapOf("breakfast" to "oats"))

    @Test
    fun `a favourite set is put on the days picked in its window, which reads the profile's sets and closes on the plan it leaves`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { favorites.sets(plan.profileId) } returns listOf(weekday)
        val asked = slot<ApplyFavoriteSetRequest>()
        val applied = plan.copy(revision = plan.revision + 1)
        coEvery { api.applyFavoriteSet("weekday", capture(asked)) } returns applied
        val model = model(api)
        val first = plan.days.first().date

        model.openSetWindow()
        assertEquals(listOf(weekday), model.state.value.setWindow?.sets)
        model.applySet("weekday", listOf(first))

        assertEquals(ApplyFavoriteSetRequest(plan.id, listOf(first)), asked.captured)
        assertNull(model.state.value.setWindow)
        assertEquals(applied.revision, model.state.value.plan?.revision)
    }

    @Test
    fun `a set the API refuses keeps its window open with the reason, and the sets that cannot be read are said there`() {
        val api = mockk<DietApiService>()
        coEvery { api.mealPlan(plan.id) } returns plan
        coEvery { favorites.sets(plan.profileId) } returns listOf(weekday)
        coEvery { api.applyFavoriteSet(any(), any()) } throws refused(409, "MEAL_EATEN")
        val model = model(api)

        model.openSetWindow()
        model.applySet("weekday", listOf(plan.days.first().date))

        val window = model.state.value.setWindow
        assertEquals("MEAL_EATEN", (window?.error as? ApiException)?.code)
        assertFalse(window!!.applying)
        model.closeSetWindow()
        assertNull(model.state.value.setWindow)

        coEvery { favorites.sets(plan.profileId) } throws IOException("no answer")
        model.openSetWindow()
        assertEquals(emptyList<FavoriteSet>(), model.state.value.setWindow?.sets)
        assertTrue(model.state.value.setWindow?.readError is IOException)
    }
}
