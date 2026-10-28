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
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.Drained
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.ListProposal
import net.whiteravens.dietapp.domain.ProposalMeal
import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.changeQueue
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

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

    private fun repository(cache: CacheDao, queue: ChangeQueue = changeQueue()): ShoppingListRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        val client = AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders())
        return ShoppingListRepository(AppModule.api(client, json), AppModule.modelApi(client, json), cache, json, queue)
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
    fun `the lists of a plan the API says is not there leave no copy, and the lists of another plan stay`() {
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody(fixture("shopping-lists-of-an-unknown-plan")))
        val other = CachedShoppingList("other-plans", "another-plan", "{}", 1L)
        val cache = Cache(listOf(kept("first", "2026-10-13", "2026-10-19"), kept("second", "2026-10-20", "2026-10-21"), other))

        val refused = runCatching { runBlocking { repository(cache).refreshForPlan(planId) } }.exceptionOrNull() as ApiException

        assertEquals("PLAN_NOT_FOUND", refused.code)
        assertEquals(setOf(other.id), cache.lists.keys)
    }

    @Test
    fun `a list the API says is not there leaves no copy, and the other lists of its plan stay`() {
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody(fixture("shopping-list-unknown")))
        val cache = Cache(listOf(kept("gone", "2026-10-13", "2026-10-19"), kept("kept", "2026-10-20", "2026-10-21")))

        val refused = runCatching { runBlocking { repository(cache).refresh("gone") } }.exceptionOrNull() as ApiException

        assertEquals("LIST_NOT_FOUND", refused.code)
        assertEquals(setOf("kept"), cache.lists.keys)
    }

    @Test
    fun `a list read while the server cannot say whether it is there keeps its copy`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Content-Type", "text/html").setBody("<html>Service Unavailable</html>"))
        val cache = Cache(listOf(kept("list", "2026-10-13", "2026-10-19")))

        runCatching { runBlocking { repository(cache).refresh("list") } }

        assertEquals(setOf("list"), cache.lists.keys)
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

    @Test
    fun `a row sends what changed of it alone, its tick with what was typed, what is at home, and what the user has, cleared with a null`() {
        repeat(3) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(fixture("shopping-item-ticked"))) }
        val lists = repository(Cache(emptyList()))

        runBlocking {
            lists.change("list", "row", RowChange(checked = true, atHome = 100.0))
            lists.change("list", "row", RowChange(have = 0.0))
            lists.change("list", "row", RowChange(have = 350.0, checked = true))
        }

        val ticked = server.takeRequest()
        assertEquals("PATCH", ticked.method)
        assertEquals("/api/shopping-lists/list/items/row", ticked.path)
        assertEquals("""{"checked":true,"alreadyHaveQuantity":100.0}""", ticked.body.readUtf8())
        assertEquals("""{"purchasedQuantity":null}""", server.takeRequest().body.readUtf8())
        assertEquals("""{"purchasedQuantity":350.0,"checked":true}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `a row change whose answer was lost is kept, and sent again as it is in its turn`() {
        var answering = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (answering) MockResponse().setHeader("Content-Type", "application/json").setBody(fixture("shopping-item-ticked"))
                else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        val cache = Cache(emptyList())
        val queue = changeQueue()
        val lists = repository(cache, queue)

        val kept = runBlocking { lists.change("list", "row", RowChange(checked = true, atHome = 100.0)) }

        assertEquals(Submitted.Kept(offline = true), kept)
        answering = true
        val drained = runBlocking { queue.drain { lists.send(it as QueuedChange.ShoppingRow) } }
        assertEquals(Drained.EMPTY, drained)
        val sent = generateSequence { server.takeRequest(0, TimeUnit.SECONDS) }.last()
        assertEquals("/api/shopping-lists/list/items/row", sent.path)
        assertEquals("""{"checked":true,"alreadyHaveQuantity":100.0}""", sent.body.readUtf8())
        // The list as the server answered it is kept for the screen to read.
        assertEquals(1, cache.lists.size)
    }

    @Test
    fun `what a list could do without is asked for unweighed unless a model is to weigh it, and a kind this version does not know is left out`() {
        // The shape the API answers with, a model's order and sentences in it.
        val proposals = """{"planId":"plan","proposals":[""" +
            """{"kind":"merge","from":{"ingredientId":"butter","name":"Butter"},"to":{"ingredientId":"oil","name":"Olive oil"},""" +
            """"meals":[{"plannedMealId":"meal-1","date":"2026-10-14","mealType":"lunch","title":"Soup"}],"packsSaved":1,"reason":"One bottle fewer."},""" +
            """{"kind":"leftover","ingredientId":"feta","name":"Feta","unit":"g","left":80,"packSize":200,"reason":null},""" +
            """{"kind":"another","name":"Unknown"}],""" +
            """"aiMeta":{"provider":"openrouter","model":"m","failoverChain":[],"usedDeterministicFallback":false,"fallbackReason":null,"reason":null}}"""
        repeat(2) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(proposals)) }
        val lists = repository(Cache(emptyList()))

        val found = runBlocking { lists.proposals("list") }
        runBlocking { lists.proposals("list", weigh = true) }

        assertEquals(
            listOf(
                ListProposal.Merge("Butter", "Olive oil", listOf(ProposalMeal("2026-10-14", "lunch", "Soup")), 1, "One bottle fewer."),
                ListProposal.Leftover("Feta", "g", 80, 200.0, null),
            ),
            found.proposals,
        )
        assertTrue(found.weighed)
        val first = server.takeRequest()
        assertEquals("/api/shopping-lists/list/proposals", first.path)
        assertEquals("{}", first.body.readUtf8())
        assertEquals("""{"weigh":true}""", server.takeRequest().body.readUtf8())
    }
}
