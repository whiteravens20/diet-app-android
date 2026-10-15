// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.data.remote.MealPlanDto
import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.DayAsk
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanAsk
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.RelaxedMeal
import net.whiteravens.dietapp.domain.Reroll
import net.whiteravens.dietapp.domain.ScaleChange
import net.whiteravens.dietapp.domain.Substitution
import net.whiteravens.dietapp.domain.SwapAsk
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** The plans of a profile, a page at a time, from a local server answering what the backend answered. */
class MealPlanRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    /** Keeps what is written to it, and what is taken out of it. */
    private class Cache(private val listsOfPlan: List<CachedShoppingList> = emptyList()) : CacheDao {
        val planLists = mutableListOf<CachedPlanList>()
        val dropped = mutableListOf<String>()
        override suspend fun upsertPlanList(list: CachedPlanList) {
            planLists += list
        }
        override suspend fun planList(profileId: String) = planLists.lastOrNull { it.profileId == profileId }
        override suspend fun deletePlanList(profileId: String) {
            dropped += "list of $profileId"
        }
        override suspend fun upsertMealPlan(plan: CachedMealPlan) = Unit
        override suspend fun mealPlan(id: String): CachedMealPlan? = null
        override suspend fun deleteMealPlan(id: String) {
            dropped += "plan $id"
        }
        override suspend fun upsertShoppingList(list: CachedShoppingList) = Unit
        override suspend fun shoppingList(id: String): CachedShoppingList? = null
        override suspend fun shoppingListsForPlan(planId: String) = listsOfPlan.filter { it.planId == planId }
        override suspend fun deleteShoppingList(id: String) {
            dropped += "shopping list $id"
        }
    }

    private fun repository(cache: CacheDao): MealPlanRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return MealPlanRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), cache, json)
    }

    private fun answer(name: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(fixture(name))

    @Test
    fun `each page asks for its number, and only the first is kept for offline use`() {
        server.enqueue(answer("meal-plans"))
        server.enqueue(answer("meal-plans"))
        val cache = Cache()
        val plans = repository(cache)

        val first = runBlocking { plans.plans("profile-1") }
        runBlocking { plans.plans("profile-1", page = 2) }

        assertTrue(first.plans.isNotEmpty())
        assertEquals("/api/meal-plans?profileId=profile-1&page=1&pageSize=20", server.takeRequest().path)
        assertEquals("/api/meal-plans?profileId=profile-1&page=2&pageSize=20", server.takeRequest().path)
        assertEquals(listOf("profile-1"), cache.planLists.map { it.profileId })
        assertEquals(first.plans, runBlocking { plans.cachedPlans("profile-1") }!!.plans)
    }

    @Test
    fun `a swap is offered without the recipes offered before, and taken with the recipe and the plan's revision`() {
        server.enqueue(answer("meal-plan-swap-preview"))
        server.enqueue(answer("meal-plan-swap-apply"))
        val plans = repository(Cache())

        val offer = runBlocking { plans.offerSwap("plan-1", "meal-1", SwapAsk("random"), seen = listOf("recipe-0")) }
        runBlocking { plans.takeSwap(offer) }

        val offered = server.takeRequest()
        assertEquals("/api/meal-plans/swap-meal/preview", offered.path)
        val asked = json.parseToJsonElement(offered.body.readUtf8()).jsonObject
        assertEquals("plan-1", asked.getValue("planId").jsonPrimitive.content)
        assertEquals("meal-1", asked.getValue("plannedMealId").jsonPrimitive.content)
        assertEquals("random", asked.getValue("strategy").jsonPrimitive.content)
        assertEquals(listOf("recipe-0"), asked.getValue("skip").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("recipe-0", offer.recipeId), offer.seen)

        val taken = server.takeRequest()
        assertEquals("/api/meal-plans/swap-meal", taken.path)
        val sent = json.parseToJsonElement(taken.body.readUtf8()).jsonObject
        assertEquals(offer.recipeId, sent.getValue("recipeId").jsonPrimitive.content)
        assertEquals(offer.revision, sent.getValue("revision").jsonPrimitive.int)
        assertEquals("random", sent.getValue("strategy").jsonPrimitive.content)
    }

    @Test
    fun `a plan is asked for with what differs from the profile alone, its days as the form chose them`() {
        server.enqueue(answer("meal-plan").setResponseCode(201))
        val monday = LocalDate.of(2026, 10, 12)
        val ask = PlanAsk(
            profileId = "p",
            startDate = monday,
            durationDays = 3,
            mealCount = 4,
            mealPrepFriendly = true,
            days = listOf(DayAsk(monday, dayType = "rest", locks = mapOf("breakfast" to "oats")), DayAsk(monday.plusDays(1), skip = true)),
        )

        runBlocking { repository(Cache()).generate(ask) }

        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("2026-10-12", body.getValue("startDate").jsonPrimitive.content)
        assertEquals("4", body.getValue("mealCount").jsonPrimitive.content)
        assertEquals("true", body.getValue("mealPrepFriendly").jsonPrimitive.content)
        // Left to the profile: nothing is sent for them.
        assertFalse("dietType" in body || "calorieTargetOverride" in body || "allowRelaxations" in body)
        val days = body.getValue("dayOverrides").jsonArray.map { it.jsonObject }
        assertEquals(setOf("date", "dayType", "lockedSlots"), days[0].keys)
        assertEquals("oats", days[0].getValue("lockedSlots").jsonArray[0].jsonObject.getValue("recipeId").jsonPrimitive.content)
        assertEquals(setOf("date", "skip"), days[1].keys)
    }

    @Test
    fun `a refusal for limits names them and the meals that would go outside, and no other refusal does`() {
        val plans = repository(Cache())
        val details = json.parseToJsonElement(
            """{"needs":["cook_time","weekly_cap"],"meals":[{"date":"2026-10-12","slot":"lunch","gaveWay":["weekly_cap"]}]}""",
        ).jsonObject

        val needed = plans.relaxationNeeded(ApiException(422, "PLAN_NEEDS_RELAXATION", "Limits.", details = details))

        assertEquals(listOf("cook_time", "weekly_cap"), needed?.needs)
        assertEquals(RelaxedMeal(LocalDate.of(2026, 10, 12), "lunch", listOf("weekly_cap")), needed?.meals?.single())
        assertNull(plans.relaxationNeeded(ApiException(400, "NO_ELIGIBLE_RECIPE", "None.", details = details)))
        assertNull(plans.relaxationNeeded(ApiException(422, "PLAN_NEEDS_RELAXATION", "Limits.", details = JsonObject(emptyMap()))))
    }

    @Test
    fun `a rebalance taken back puts each meal it scaled back as it was`() {
        server.enqueue(answer("meal-plan-swap-apply"))
        val scaled = Rebalance("day", false, listOf(ScaleChange("meal-1", 1.0, 0.8), ScaleChange("meal-2", 1.0, 1.2)), 0.0, 2300.0, 2000.0)

        runBlocking { repository(Cache()).undo("plan-1", scaled) }

        val undone = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/rebalance", undone.path)
        val back = json.parseToJsonElement(undone.body.readUtf8()).jsonObject
        assertEquals("day", back.getValue("scope").jsonPrimitive.content)
        assertEquals(
            listOf("meal-1" to 1.0, "meal-2" to 1.0),
            back.getValue("restore").jsonArray.map { it.jsonObject.getValue("mealId").jsonPrimitive.content to it.jsonObject.getValue("scale").jsonPrimitive.double },
        )
    }

    @Test
    fun `a meal is marked eaten with what it is to be, not flipped`() {
        server.enqueue(answer("meal-plan-swap-apply"))

        runBlocking { repository(Cache()).setEaten("plan-1", "meal-1", eaten = true) }

        val eaten = server.takeRequest()
        assertEquals("PATCH", eaten.method)
        assertEquals("/api/meal-plans/plan-1/meals/meal-1/eaten", eaten.path)
        assertEquals("""{"eaten":true}""", eaten.body.readUtf8())
    }

    @Test
    fun `a meal of one's own is sent with what one serving holds and how many servings it is, named without the spaces around it`() {
        server.enqueue(answer("meal-plan-swap-apply"))

        runBlocking { repository(Cache()).addCustomMeal("plan-1", "2026-10-13", CustomMeal(" Soup ", "lunch", Nutrition(300.0, 12.0, 8.0, 40.0), 1.5)) }

        val own = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/days/2026-10-13/custom-meal", own.path)
        val meal = json.parseToJsonElement(own.body.readUtf8()).jsonObject
        assertEquals("Soup", meal.getValue("name").jsonPrimitive.content)
        assertEquals("lunch", meal.getValue("mealType").jsonPrimitive.content)
        assertEquals(300.0, meal.getValue("nutrition").jsonObject.getValue("calories").jsonPrimitive.double, 0.0)
        assertEquals(1.5, meal.getValue("servings").jsonPrimitive.double, 0.0)
    }

    @Test
    fun `the rest of the week is balanced from the day it is for the phone`() {
        server.enqueue(answer("meal-plan-swap-apply"))

        runBlocking { repository(Cache()).rebalanceWeek("plan-1") }

        val asked = server.takeRequest()
        assertEquals("""{"scope":"week"}""", asked.body.readUtf8())
        assertEquals(LocalDate.now().toString(), asked.getHeader("X-Client-Date"))
    }

    @Test
    fun `a deleted plan leaves no copy on the phone of itself, of its shopping lists or of its profile's list`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val plan = json.decodeFromString<MealPlanDto>(fixture("meal-plan")).toDomain()
        val cache = Cache(listOf(CachedShoppingList("list-1", plan.id, "{}", 1L), CachedShoppingList("list-2", "another plan", "{}", 1L)))

        runBlocking { repository(cache).delete(plan) }

        val deleted = server.takeRequest()
        assertEquals("DELETE", deleted.method)
        assertEquals("/api/meal-plans/${plan.id}", deleted.path)
        assertEquals(setOf("shopping list list-1", "plan ${plan.id}", "list of ${plan.profileId}"), cache.dropped.toSet())
    }

    @Test
    fun `a model is asked for a pick, and its pick is taken as the engine's own, with its note`() {
        // A captured offer, with what an answer of a model adds to it.
        val offered = json.parseToJsonElement(fixture("meal-plan-swap-preview")).jsonObject
        val picked = JsonObject(
            offered + mapOf(
                "aiMeta" to json.parseToJsonElement("""{"provider":"openrouter","model":"m","failoverChain":[],"usedDeterministicFallback":false,"fallbackReason":null,"reason":"Lighter."}"""),
                "pick" to JsonPrimitive("signed-note"),
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(picked.toString()))
        server.enqueue(answer("meal-plan-swap-apply"))
        val plans = repository(Cache())

        val offer = runBlocking { plans.offerSwap("plan-1", "meal-1", SwapAsk("ai")) }
        runBlocking { plans.takeSwap(offer) }

        assertEquals("/api/meal-plans/ai-swap-meal/preview", server.takeRequest().path)
        assertEquals("Lighter.", offer.reason)
        val taken = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("random", taken.getValue("strategy").jsonPrimitive.content)
        assertEquals("signed-note", taken.getValue("pick").jsonPrimitive.content)
    }

    @Test
    fun `an offer leaves the pantry out of the choice only where it is asked to`() {
        repeat(2) { server.enqueue(answer("meal-plan-swap-preview")) }
        val plans = repository(Cache())

        runBlocking {
            plans.offerSwap("plan-1", "meal-1", SwapAsk("random"))
            plans.offerSwap("plan-1", "meal-1", SwapAsk("random"), respectInventory = false)
        }

        // Left out, the API's own default holds: the pantry first.
        assertFalse("respectInventory" in json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject)
        assertEquals("false", json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("respectInventory").jsonPrimitive.content)
    }

    @Test
    fun `a substitution is previewed with nothing taken, and made with a moved serving taken, a rewording asked for and a model's note`() {
        val line = """{"ingredientId":"%s","name":"%s","quantity":20,"unit":"g","display":{"quantity":20,"unit":"g"}}"""
        val nutrition = """{"calories":500,"protein":20,"fat":25,"carbs":40}"""
        // The shape the API answers a preview with.
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"before":$nutrition,"after":$nutrition,"calorieDelta":0,"macroDelta":{"protein":0,"fat":0,"carbs":0},""" +
                    """"from":${line.format("butter", "Butter")},"to":${line.format("oil", "Olive oil")},""" +
                    """"sizing":"energy","merged":false,"violations":[],"moved":["fat"],"valid":true}""",
            ),
        )
        // A captured change of a plan, with what a rewording a model was asked for adds to it.
        val made = JsonObject(
            json.parseToJsonElement(fixture("meal-plan-swap-apply")).jsonObject + mapOf(
                "aiMeta" to json.parseToJsonElement("""{"provider":null,"model":null,"failoverChain":[],"usedDeterministicFallback":true,"fallbackReason":"quota_exhausted","reason":null}"""),
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(made.toString()))
        val plans = repository(Cache())

        val preview = runBlocking { plans.previewSubstitution(Substitution("plan-1", "meal-1", "butter", "oil")) }
        val done = runBlocking { plans.substitute(Substitution("plan-1", "meal-1", "butter", "oil", rewriteWithAi = true, acceptChange = true, pick = "note")) }

        assertEquals(listOf("fat"), preview.moved)
        assertEquals("Olive oil", preview.to.name)
        val previewed = server.takeRequest()
        assertEquals("/api/meal-plans/swap-ingredient/preview", previewed.path)
        val asked = json.parseToJsonElement(previewed.body.readUtf8()).jsonObject
        assertFalse("acceptChange" in asked || "rewriteWithAi" in asked || "pick" in asked)
        val applied = server.takeRequest()
        assertEquals("/api/meal-plans/swap-ingredient/apply", applied.path)
        val sent = json.parseToJsonElement(applied.body.readUtf8()).jsonObject
        assertEquals("true", sent.getValue("acceptChange").jsonPrimitive.content)
        assertEquals("true", sent.getValue("rewriteWithAi").jsonPrimitive.content)
        assertEquals("note", sent.getValue("pick").jsonPrimitive.content)
        assertEquals("quota_exhausted", done.fallbackReason)
    }

    @Test
    fun `a day is made again at its own address, a part of the plan by its days, and nothing is sent that is not asked for`() {
        repeat(3) { server.enqueue(answer("meal-plan")) }
        val plans = repository(Cache())

        runBlocking {
            plans.reroll("plan-1", Reroll(dayId = "day-1", replaceCustom = true))
            plans.reroll("plan-1", Reroll(from = "2026-10-13", to = "2026-10-15", includePast = true, allowRelaxations = emptyList()))
            plans.reroll("plan-1", Reroll())
        }

        val day = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/days/day-1/regenerate", day.path)
        assertEquals("""{"replaceCustom":true}""", day.body.readUtf8())
        val part = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/regenerate", part.path)
        // An empty list of limits is every limit held: it is sent.
        assertEquals("""{"from":"2026-10-13","to":"2026-10-15","includePast":true,"allowRelaxations":[]}""", part.body.readUtf8())
        assertEquals("{}", server.takeRequest().body.readUtf8())
    }
}

