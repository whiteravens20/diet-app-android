// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
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
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.Drained
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.DayAsk
import net.whiteravens.dietapp.domain.DaySettings
import net.whiteravens.dietapp.domain.MealFate
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.domain.PlanAsk
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.RelaxedMeal
import net.whiteravens.dietapp.domain.Reroll
import net.whiteravens.dietapp.domain.RerollCouldGo
import net.whiteravens.dietapp.domain.RerollKept
import net.whiteravens.dietapp.domain.RerollPreview
import net.whiteravens.dietapp.domain.ScaleChange
import net.whiteravens.dietapp.domain.Substitution
import net.whiteravens.dietapp.domain.UseUpSwap
import net.whiteravens.dietapp.domain.UseUpTake
import net.whiteravens.dietapp.domain.WayMeal
import net.whiteravens.dietapp.domain.SwapAsk
import net.whiteravens.dietapp.testing.changeQueue
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

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
        val plans = mutableListOf<CachedMealPlan>()
        val dropped = mutableListOf<String>()
        override suspend fun upsertPlanList(list: CachedPlanList) {
            planLists += list
        }
        override suspend fun planList(profileId: String) = planLists.lastOrNull { it.profileId == profileId }
        override suspend fun deletePlanList(profileId: String) {
            dropped += "list of $profileId"
        }
        override suspend fun upsertMealPlan(plan: CachedMealPlan) {
            plans += plan
        }
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

    private fun repository(cache: CacheDao, queue: ChangeQueue = changeQueue()): MealPlanRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        val client = AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders())
        return MealPlanRepository(AppModule.api(client, json), AppModule.modelApi(client, json), cache, json, queue)
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
    fun `a plan the API says is not there takes its copy and those of its shopping lists with it`() {
        server.enqueue(answer("meal-plan-of-another-account").setResponseCode(404))
        val cache = Cache(
            listOf(CachedShoppingList("list-1", "plan-1", "{}", 1L), CachedShoppingList("list-2", "plan-2", "{}", 1L)),
        )

        val refused = runCatching { runBlocking { repository(cache).refreshPlan("plan-1") } }.exceptionOrNull() as ApiException

        assertEquals("PLAN_NOT_FOUND", refused.code)
        assertEquals(listOf("shopping list list-1", "plan plan-1"), cache.dropped)
    }

    @Test
    fun `the plans of a profile the API says is not there take the copy of its list with it`() {
        server.enqueue(answer("meal-plans-of-an-unknown-profile").setResponseCode(404))
        val cache = Cache()

        val refused = runCatching { runBlocking { repository(cache).plans("profile-1") } }.exceptionOrNull() as ApiException

        assertEquals("PROFILE_NOT_FOUND", refused.code)
        assertEquals(listOf("list of profile-1"), cache.dropped)
    }

    @Test
    fun `a plan read while the server cannot say whether it is there keeps its copies`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Content-Type", "text/html").setBody("<html>Service Unavailable</html>"))
        val cache = Cache(listOf(CachedShoppingList("list-1", "plan-1", "{}", 1L)))

        runCatching { runBlocking { repository(cache).refreshPlan("plan-1") } }

        assertEquals(emptyList<String>(), cache.dropped)
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
    fun `an eaten mark whose answer was lost is kept, and sent again as it is in its turn`() {
        var answering = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (answering) answer("meal-plan-swap-apply") else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        val cache = Cache()
        val queue = changeQueue()
        val plans = repository(cache, queue)

        val kept = runBlocking { plans.setEaten("plan-1", "meal-1", eaten = true) }

        assertEquals(Submitted.Kept(offline = true), kept)
        answering = true
        val drained = runBlocking { queue.drain { plans.send(it as QueuedChange.Eaten) } }
        assertEquals(Drained.EMPTY, drained)
        val sent = generateSequence { server.takeRequest(0, TimeUnit.SECONDS) }.last()
        assertEquals("/api/meal-plans/plan-1/meals/meal-1/eaten", sent.path)
        assertEquals("""{"eaten":true}""", sent.body.readUtf8())
        // The plan as the server answered it is kept for the screen to read.
        assertEquals(1, cache.plans.size)
    }

    @Test
    fun `a meal of one's own is sent with what one serving holds and how many servings it is, named without the spaces around it, under a key of its own`() {
        server.enqueue(answer("meal-plan-swap-apply"))

        runBlocking { repository(Cache()).addCustomMeal("plan-1", "2026-10-13", CustomMeal(" Soup ", "lunch", Nutrition(300.0, 12.0, 8.0, 40.0), 1.5)) }

        val own = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/days/2026-10-13/custom-meal", own.path)
        val meal = json.parseToJsonElement(own.body.readUtf8()).jsonObject
        assertEquals("Soup", meal.getValue("name").jsonPrimitive.content)
        assertEquals("lunch", meal.getValue("mealType").jsonPrimitive.content)
        assertEquals(300.0, meal.getValue("nutrition").jsonObject.getValue("calories").jsonPrimitive.double, 0.0)
        assertEquals(1.5, meal.getValue("servings").jsonPrimitive.double, 0.0)
        assertFalse("a meal beside the day's names none it is eaten in the place of", "replaces" in meal)
        UUID.fromString(own.getHeader("Idempotency-Key"))
    }

    @Test
    fun `a meal of one's own whose answer was lost is kept, sent again under the same key, and a removal of a meal gone is done`() {
        var answering = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                !answering -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                request.method == "DELETE" -> MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json")
                    .setBody("""{"statusCode":404,"error":"MEAL_NOT_FOUND","message":"Meal not found."}""")
                else -> answer("meal-plan-swap-apply")
            }
        }
        val queue = changeQueue()
        val plans = repository(Cache(), queue)

        val kept = runBlocking { plans.addCustomMeal("plan-1", "2026-10-13", CustomMeal("Soup", "lunch", Nutrition(300.0, 12.0, 8.0, 40.0), 1.0)) }
        runBlocking { plans.removeMeal("plan-1", "meal-2") }

        assertEquals(Submitted.Kept(offline = true), kept)
        answering = true
        val drained = runBlocking {
            queue.drain { change ->
                when (change) {
                    is QueuedChange.OwnMealAdded -> plans.send(change)
                    is QueuedChange.MealRemoved -> plans.send(change)
                    else -> error("not a change of a plan")
                }
            }
        }
        assertEquals(Drained.EMPTY, drained)
        val first = server.takeRequest()
        val again = generateSequence { server.takeRequest(0, TimeUnit.SECONDS) }.toList()
        val sentAgain = again.first { it.method == "POST" }
        assertEquals(first.getHeader("Idempotency-Key"), sentAgain.getHeader("Idempotency-Key"))
        assertEquals("DELETE", again.last().method)
        assertTrue(runBlocking { queue.refused.first() }.isEmpty())
    }

    @Test
    fun `a meal of one's own in the place of another names it, and a meal taken off its day is deleted by its path`() {
        server.enqueue(answer("meal-plan-swap-apply"))
        server.enqueue(answer("meal-plan-swap-apply"))
        val plans = repository(Cache())

        runBlocking {
            plans.addCustomMeal("plan-1", "2026-10-13", CustomMeal("Soup", "lunch", Nutrition(300.0, 12.0, 8.0, 40.0), 1.0), replaces = "meal-1")
            plans.removeMeal("plan-1", "meal-2")
        }

        assertEquals("meal-1", json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject.getValue("replaces").jsonPrimitive.content)
        val removed = server.takeRequest()
        assertEquals("DELETE", removed.method)
        assertEquals("/api/meal-plans/plan-1/meals/meal-2", removed.path)
    }

    @Test
    fun `the rest of the plan is balanced from the day it is for the phone, or from the day given`() {
        server.enqueue(answer("meal-plan-swap-apply"))
        server.enqueue(answer("meal-plan-swap-apply"))
        val plans = repository(Cache())

        runBlocking {
            plans.rebalanceRest("plan-1")
            plans.rebalanceRest("plan-1", from = "2026-10-20")
        }

        val today = server.takeRequest()
        assertEquals("""{"scope":"plan"}""", today.body.readUtf8())
        assertEquals(LocalDate.now().toString(), today.getHeader("X-Client-Date"))
        assertEquals("""{"scope":"plan","date":"2026-10-20"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `what a day's settings do is asked without a revision, and the change is sent with it and kept on the phone`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(DAY_SETTINGS_PREVIEW))
        server.enqueue(answer("meal-plan"))
        val cache = Cache()
        val plans = repository(cache)
        val settings = DaySettings(dayType = "training", mealCount = 4)

        val preview = runBlocking { plans.previewDaySettings("plan-1", "day-1", settings) }
        val saved = runBlocking { plans.applyDaySettings("plan-1", "day-1", settings, preview.revision, listOf("cook_time")) }

        val asked = server.takeRequest()
        assertEquals("POST", asked.method)
        assertEquals("/api/meal-plans/plan-1/days/day-1/settings/preview", asked.path)
        assertFalse("revision" in json.parseToJsonElement(asked.body.readUtf8()).jsonObject)
        val changed = server.takeRequest()
        assertEquals("PUT", changed.method)
        assertEquals("/api/meal-plans/plan-1/days/day-1/settings", changed.path)
        val body = json.parseToJsonElement(changed.body.readUtf8()).jsonObject
        assertEquals(7, body.getValue("revision").jsonPrimitive.int)
        assertEquals(listOf("cook_time"), body.getValue("allowRelaxations").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf(saved.id), cache.plans.map { it.id })
        assertEquals(listOf(MealFate("meal-1", "lunch", "redrawn", "over_cook_time")), preview.meals)
        assertEquals(listOf("snack"), preview.added)
        assertEquals(2300.0, preview.targetThen, 0.0)
        assertNull("no day's total is said before its new recipes are drawn", preview.dayCalories)
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

    @Test
    fun `what a re-roll would do is asked with the re-roll's own request, a day at its own address, and read as the API answers it`() {
        server.enqueue(answer("reroll-preview"))
        server.enqueue(answer("reroll-preview-replace-eaten"))
        server.enqueue(answer("reroll-preview-skipped-day"))
        val plans = repository(Cache())

        val whole = runBlocking { plans.previewReroll("plan-1", Reroll()) }
        val eatenToo = runBlocking { plans.previewReroll("plan-1", Reroll(replaceEaten = true)) }
        val skipped = runBlocking { plans.previewReroll("plan-1", Reroll(dayId = "day-2")) }

        val first = server.takeRequest()
        assertEquals("POST", first.method)
        assertEquals("/api/meal-plans/plan-1/regenerate/preview", first.path)
        assertEquals("{}", first.body.readUtf8())
        val second = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/regenerate/preview", second.path)
        assertEquals("""{"replaceEaten":true}""", second.body.readUtf8())
        val day = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/days/day-2/regenerate/preview", day.path)
        assertEquals("{}", day.body.readUtf8())
        // A plan of three days from today, the first day's breakfast eaten and the second skipped with its breakfast eaten.
        val eatenCouldGo = RerollCouldGo(past = false, eaten = true, custom = false)
        assertEquals(RerollPreview(2, 5, RerollKept(outside = 0, past = 0, skipped = 1, eaten = 1, custom = 0, locked = 0), eatenCouldGo, "not_begun"), whole)
        assertEquals(RerollPreview(2, 6, RerollKept(outside = 0, past = 0, skipped = 1, eaten = 0, custom = 0, locked = 0), eatenCouldGo, "not_begun"), eatenToo)
        assertEquals(
            RerollPreview(0, 0, RerollKept(outside = 2, past = 0, skipped = 1, eaten = 0, custom = 0, locked = 0), RerollCouldGo(past = false, eaten = false, custom = false), "not_begun"),
            skipped,
        )
    }

    @Test
    fun `other ways to fill a day are asked for unweighed unless a model is to weigh them, and one is taken with its note`() {
        val nutrition = """{"calories":2000,"protein":120,"fat":70,"carbs":210}"""
        val packs = """{"bought":500,"used":450,"wasted":1}"""
        // The shape the API answers with, a model's order in it.
        val ways = """{"date":"2026-10-13","calorieTarget":2050.5,"current":{"dayNutrition":$nutrition,"packs":$packs},""" +
            """"variations":[{"meals":[{"mealType":"lunch","title":"Soup","recipeId":null,"servings":1,"nutrition":$nutrition,"stays":true}],""" +
            """"dayNutrition":$nutrition,"packs":$packs,"reason":"Lighter.","offer":"note-1"}],""" +
            """"aiMeta":{"provider":"openrouter","model":"m","failoverChain":[],"usedDeterministicFallback":false,"fallbackReason":null,"reason":null}}"""
        repeat(2) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(ways)) }
        server.enqueue(answer("meal-plan"))
        val plans = repository(Cache())

        val offered = runBlocking { plans.dayWays("plan-1", "day-1") }
        runBlocking {
            plans.dayWays("plan-1", "day-1", weigh = true)
            plans.takeDayWay("plan-1", "day-1", "note-1")
        }

        assertTrue(offered.weighed)
        assertEquals(listOf(WayMeal("lunch", "Soup", 2000.0, stays = true)), offered.ways.single().meals)
        assertEquals("Lighter.", offered.ways.single().reason)
        val first = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/days/day-1/variations", first.path)
        assertEquals("{}", first.body.readUtf8())
        assertEquals("""{"weigh":true}""", server.takeRequest().body.readUtf8())
        val taken = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/days/day-1/variations/take", taken.path)
        assertEquals("""{"offer":"note-1"}""", taken.body.readUtf8())
    }

    @Test
    fun `swaps that would use up what a plan opens are asked for unweighed unless a model is to weigh them, and one is taken as a swap with the revision they were found for and the model's note`() {
        val nutrition = """{"calories":%d,"protein":30,"fat":20,"carbs":50}"""
        val packs = """{"before":{"bought":400,"used":260,"wasted":1},"after":{"bought":400,"used":320,"wasted":0}}"""
        // The shape the API answers with: as the engine found them, as a model weighed them, and as the engine's own where the model stood aside.
        fun found(reason: String?, pick: String?, aiMeta: String) =
            """{"revision":7,"suggestions":[{"plannedMealId":"meal-1","date":"2026-10-13","mealType":"lunch",""" +
                """"from":{"id":"recipe-1","title":"Soup"},"to":{"id":"recipe-2","title":"Feta salad"},"servings":1.5,""" +
                """"before":${nutrition.format(500)},"after":${nutrition.format(480)},""" +
                """"takes":[{"ingredientId":"feta","name":"Feta","unit":"g","amount":60,"left":40}],"packs":$packs,""" +
                """"reason":${reason?.let { "\"$it\"" }},"pick":${pick?.let { "\"$it\"" }}}],"aiMeta":$aiMeta}"""
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(found(null, null, "null")))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                found(null, null, """{"provider":null,"model":null,"failoverChain":[],"usedDeterministicFallback":true,"fallbackReason":"quota_exhausted","reason":null}"""),
            ),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                found("Uses the feta.", "note-1", """{"provider":"openrouter","model":"m","failoverChain":[],"usedDeterministicFallback":false,"fallbackReason":null,"reason":null}"""),
            ),
        )
        server.enqueue(answer("meal-plan-swap-apply"))
        val plans = repository(Cache())

        val unweighed = runBlocking { plans.useUp("plan-1") }
        val byEngine = runBlocking { plans.useUp("plan-1", weigh = true) }
        val weighed = runBlocking { plans.useUp("plan-1", weigh = true) }
        runBlocking { plans.takeUseUp("plan-1", weighed.revision, weighed.swaps.single()) }

        assertEquals(
            UseUpSwap(
                mealId = "meal-1",
                date = "2026-10-13",
                slot = "lunch",
                fromTitle = "Soup",
                toId = "recipe-2",
                toTitle = "Feta salad",
                takes = listOf(UseUpTake("Feta", "g", 60, 40)),
                before = Nutrition(500.0, 30.0, 20.0, 50.0),
                after = Nutrition(480.0, 30.0, 20.0, 50.0),
                reason = null,
                pick = null,
            ),
            unweighed.swaps.single(),
        )
        assertFalse(unweighed.weighed)
        assertNull(unweighed.fallbackReason)
        assertFalse(byEngine.weighed)
        assertEquals("quota_exhausted", byEngine.fallbackReason)
        assertTrue(weighed.weighed)
        assertEquals("Uses the feta.", weighed.swaps.single().reason)
        val first = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/use-up", first.path)
        assertEquals("{}", first.body.readUtf8())
        assertEquals("""{"weigh":true}""", server.takeRequest().body.readUtf8())
        server.takeRequest()
        val taken = server.takeRequest()
        assertEquals("/api/meal-plans/swap-meal", taken.path)
        assertEquals(
            """{"planId":"plan-1","plannedMealId":"meal-1","recipeId":"recipe-2","revision":7,"strategy":"random","pick":"note-1"}""",
            taken.body.readUtf8(),
        )
    }

    @Test
    fun `a favourite set is put on the days asked of a plan, and the plan it leaves is kept for offline use`() {
        server.enqueue(answer("meal-plan"))
        val cache = Cache()

        val plan = runBlocking { repository(cache).applySet("set-1", "plan-1", listOf("2026-10-13", "2026-10-14")) }

        val applied = server.takeRequest()
        assertEquals("POST", applied.method)
        assertEquals("/api/favorite-sets/set-1/apply", applied.path)
        assertEquals(
            json.parseToJsonElement("""{"planId":"plan-1","dayDates":["2026-10-13","2026-10-14"]}"""),
            json.parseToJsonElement(applied.body.readUtf8()),
        )
        assertEquals(listOf(plan.id), cache.plans.map { it.id })
    }

    private companion object {
        /** A change of a day's settings that redraws its lunch and gives it a snack, as POST `.../settings/preview` answers it. */
        const val DAY_SETTINGS_PREVIEW = """{"revision":7,"date":"2026-10-26","calorieTarget":{"now":2000,"then":2300},""" +
            """"targetMacros":{"now":{"protein":125,"fat":67,"carbs":225},"then":{"protein":144,"fat":77,"carbs":259}},"belowSafetyFloor":false,""" +
            """"meals":[{"mealId":"meal-1","mealType":"lunch","fate":"redrawn","because":"over_cook_time"}],"added":["snack"],""" +
            """"dayCalories":null,"fits":null,"listOutOfDate":false,"unchanged":false}"""
    }

    @Test
    fun `a day is resized by its date, and the plan it leaves is kept for offline use`() {
        server.enqueue(answer("rebalance-day-resized"))
        val cache = Cache()

        val resized = runBlocking { repository(cache).resizeDay("plan-1", "2026-10-31") }

        val sent = server.takeRequest()
        assertEquals("/api/meal-plans/plan-1/rebalance", sent.requestUrl!!.encodedPath)
        val body = json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals("day", body.getValue("scope").jsonPrimitive.content)
        assertEquals("2026-10-31", body.getValue("date").jsonPrimitive.content)
        assertEquals(emptyList<ScaleChange>(), resized.rebalance?.changes)
        assertEquals(resized.plan.id, cache.plans.single().id)
    }

    @Test
    fun `other ways to fill a day say what the diary holds for it, counted as eaten`() {
        server.enqueue(answer("day-ways-with-diary"))

        val ways = runBlocking { repository(Cache()).dayWays("plan-1", "day-1") }

        assertEquals(Nutrition(1554.0, 84.0, 59.0, 167.0), ways.diary)
    }
}
