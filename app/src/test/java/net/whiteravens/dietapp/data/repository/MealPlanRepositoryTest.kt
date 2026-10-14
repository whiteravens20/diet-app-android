// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    /** Keeps what is written to it. */
    private class Cache : CacheDao {
        val planLists = mutableListOf<CachedPlanList>()
        override suspend fun upsertPlanList(list: CachedPlanList) {
            planLists += list
        }
        override suspend fun planList(profileId: String) = planLists.lastOrNull { it.profileId == profileId }
        override suspend fun deletePlanList(profileId: String) = Unit
        override suspend fun upsertMealPlan(plan: CachedMealPlan) = Unit
        override suspend fun mealPlan(id: String): CachedMealPlan? = null
        override suspend fun deleteMealPlan(id: String) = Unit
        override suspend fun upsertShoppingList(list: CachedShoppingList) = Unit
        override suspend fun shoppingList(id: String): CachedShoppingList? = null
        override suspend fun shoppingListsForPlan(planId: String) = emptyList<CachedShoppingList>()
        override suspend fun deleteShoppingList(id: String) = Unit
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

        val offer = runBlocking { plans.offerSwap("plan-1", "meal-1", seen = listOf("recipe-0")) }
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
}
