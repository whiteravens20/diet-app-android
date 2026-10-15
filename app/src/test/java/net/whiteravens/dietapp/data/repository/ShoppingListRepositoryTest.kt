// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
import org.junit.Before
import org.junit.Test

/** The shopping lists of a plan, and the copies the phone keeps of them, against a local server answering what the backend answered. */
class ShoppingListRepositoryTest {
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

    /** The copies of lists the phone keeps, by id: what is written and what is taken out. */
    private class Cache(rows: List<CachedShoppingList>) : CacheDao {
        val lists = rows.associateBy { it.id }.toMutableMap()
        override suspend fun upsertPlanList(list: CachedPlanList) = Unit
        override suspend fun planList(profileId: String): CachedPlanList? = null
        override suspend fun deletePlanList(profileId: String) = Unit
        override suspend fun upsertMealPlan(plan: CachedMealPlan) = Unit
        override suspend fun mealPlan(id: String): CachedMealPlan? = null
        override suspend fun deleteMealPlan(id: String) = Unit
        override suspend fun upsertShoppingList(list: CachedShoppingList) {
            lists[list.id] = list
        }
        override suspend fun shoppingList(id: String) = lists[id]
        override suspend fun shoppingListsForPlan(planId: String) = lists.values.filter { it.planId == planId }
        override suspend fun deleteShoppingList(id: String) {
            lists.remove(id)
        }
    }

    private fun repository(cache: CacheDao): ShoppingListRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return ShoppingListRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), cache, json)
    }

    /** The captured list of a plan, its days and id those given: another list of the same plan. */
    private val captured = json.parseToJsonElement(fixture("shopping-list")).jsonObject
    private val planId = captured.getValue("planId").toString().trim('"')
    private fun kept(id: String, from: String, to: String) = CachedShoppingList(
        id,
        planId,
        JsonObject(captured + mapOf("id" to JsonPrimitive(id), "fromDate" to JsonPrimitive(from), "toDate" to JsonPrimitive(to))).toString(),
        1L,
    )

    @Test
    fun `a list is made for the days asked, and the copies kept of the lists of the plan that share a day with it go`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(fixture("shopping-list-generate")))
        val cache = Cache(listOf(kept("sharing", "2026-10-19", "2026-10-20"), kept("after", "2026-10-20", "2026-10-21")))

        val made = runBlocking { repository(cache).generate(planId, "2026-10-13", "2026-10-19") }

        val asked = server.takeRequest()
        assertEquals("/api/shopping-lists/generate", asked.path)
        assertEquals("""{"planId":"$planId","fromDate":"2026-10-13","toDate":"2026-10-19"}""", asked.body.readUtf8())
        assertEquals(setOf("after", made.id), cache.lists.keys)
    }

    @Test
    fun `the lists of a plan read anew leave no copy of a list the API no longer has`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(fixture("shopping-lists")))
        val cache = Cache(listOf(kept("gone", "2026-10-20", "2026-10-21")))

        val lists = runBlocking { repository(cache).refreshForPlan(planId) }

        assertEquals(lists.map { it.id }.toSet(), cache.lists.keys)
    }

    @Test
    fun `a deleted list leaves no copy on the phone`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val cache = Cache(listOf(kept("list", "2026-10-13", "2026-10-19"), kept("other", "2026-10-20", "2026-10-21")))

        runBlocking { repository(cache).delete("list") }

        val deleted = server.takeRequest()
        assertEquals("DELETE", deleted.method)
        assertEquals("/api/shopping-lists/list", deleted.path)
        assertEquals(setOf("other"), cache.lists.keys)
    }
}
