// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.local.CacheDao
import net.whiteravens.dietapp.data.local.CachedHouseholdList
import net.whiteravens.dietapp.data.local.CachedMealPlan
import net.whiteravens.dietapp.data.local.CachedPlanList
import net.whiteravens.dietapp.data.local.CachedShoppingList
import net.whiteravens.dietapp.data.local.HouseholdListDao
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.data.remote.ShoppingListDto
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.Drained
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.HouseholdOf
import net.whiteravens.dietapp.domain.HouseholdPart
import net.whiteravens.dietapp.domain.ItemShare
import net.whiteravens.dietapp.domain.RowChange
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The account's household lists read, made, deleted and a row changed, without a network as well,
 * against a local server answering what a running backend answered: Adam's and Ewa's plans of
 * three days, joined for the first two.
 */
class HouseholdListRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    private val household = "d199e617-f67e-4689-b73d-a84b91c3602e"
    private val adamsPlan = "e04c6fbd-7bc8-4797-8916-9e9c4fcd7864"
    private val ewasPlan = "9e192f56-8915-4f33-a700-c159e7f88dee"
    private val pepper = "c030d26e-dcae-4d07-be5b-0912f9f49a3c"

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    /** The household lists kept, in memory. */
    private class Kept : HouseholdListDao {
        val rows = mutableMapOf<String, CachedHouseholdList>()
        override suspend fun upsert(list: CachedHouseholdList) {
            rows[list.id] = list
        }
        override suspend fun all() = rows.values.sortedByDescending { it.syncedAt }
        override suspend fun delete(id: String) {
            rows.remove(id)
        }
    }

    /** The plans' own lists kept, in memory; nothing else of the cache is asked for. */
    private class Lists(vararg kept: CachedShoppingList) : CacheDao {
        val rows = kept.associateBy { it.id }.toMutableMap()
        override suspend fun upsertShoppingList(list: CachedShoppingList) {
            rows[list.id] = list
        }
        override suspend fun shoppingList(id: String) = rows[id]
        override suspend fun shoppingListsForPlan(planId: String) = rows.values.filter { it.planId == planId }
        override suspend fun deleteShoppingList(id: String) {
            rows.remove(id)
        }
        override suspend fun upsertPlanList(list: CachedPlanList) = Unit
        override suspend fun planList(profileId: String): CachedPlanList? = null
        override suspend fun deletePlanList(profileId: String) = Unit
        override suspend fun upsertMealPlan(plan: CachedMealPlan) = Unit
        override suspend fun mealPlan(id: String): CachedMealPlan? = null
        override suspend fun deleteMealPlan(id: String) = Unit
    }

    private fun repository(kept: Kept = Kept(), lists: Lists = Lists(), queue: ChangeQueue = changeQueue()): HouseholdListRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return HouseholdListRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), kept, lists, json, queue)
    }

    private fun answer(status: Int, name: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    /** A plan's own list as the phone keeps it, of the days from [from] to [to]. */
    private fun planList(id: String, planId: String, from: String, to: String) = CachedShoppingList(
        id,
        planId,
        """{"id":"$id","planId":"$planId","fromDate":"$from","toDate":"$to","groups":[],"totalEstimatedCalories":0,"stale":false,"createdAt":"2026-11-07T00:00:00.000Z"}""",
        1L,
    )

    @Test
    fun `the household lists are read with their parts and what each profile needs of a row, and kept for a start without a network`() {
        server.enqueue(answer(200, "household-lists"))
        val kept = Kept()
        val households = repository(kept)

        val read = runBlocking { households.refresh() }

        assertEquals("/api/household-lists", server.takeRequest().path)
        val list = read.single()
        assertEquals(household, list.id)
        assertEquals("2026-11-07" to "2026-11-08", list.fromDate to list.toDate)
        assertEquals(
            listOf(
                HouseholdPart("2484a554-e267-4903-aae4-ded94f21cf94", adamsPlan, "7c974769-a7c9-43e7-ae22-f66b57ea4beb", "Adam", "2026-11-07", "2026-11-08", false),
                HouseholdPart("15728f05-c24e-4c3f-a088-dfe653a49a8a", ewasPlan, "7a984e4a-b285-415b-be6a-cc813b2577ce", "Ewa", "2026-11-07", "2026-11-08", false),
            ),
            list.parts,
        )
        val row = list.groups.first().items.first()
        assertEquals("Bell pepper" to 425.0, row.name to row.totalQuantity)
        assertEquals(listOf(ItemShare("7c974769-a7c9-43e7-ae22-f66b57ea4beb", "Adam", 345.0), ItemShare("7a984e4a-b285-415b-be6a-cc813b2577ce", "Ewa", 80.0)), row.shares)
        assertEquals(read, runBlocking { households.cached() }.map { it.copy(syncedAt = null) })

        // None any more: none kept either.
        server.enqueue(answer(200, "household-lists-none"))
        runBlocking { households.refresh() }
        assertTrue(runBlocking { households.cached() }.isEmpty())
    }

    @Test
    fun `one is made of a plan of each profile for its days, the plans' lists of the days give way, and the household lists are read again`() {
        server.enqueue(answer(201, "household-list-made"))
        server.enqueue(answer(200, "household-lists"))
        val lists = Lists(
            planList("of-the-days", adamsPlan, "2026-11-07", "2026-11-09"),
            planList("after-them", adamsPlan, "2026-11-09", "2026-11-09"),
            planList("ewas", ewasPlan, "2026-11-08", "2026-11-08"),
        )
        val kept = Kept()

        val made = runBlocking { repository(kept, lists).make(listOf(adamsPlan, ewasPlan), "2026-11-07", "2026-11-08") }

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/household-lists", sent.path)
        assertEquals(
            json.parseToJsonElement("""{"planIds":["$adamsPlan","$ewasPlan"],"fromDate":"2026-11-07","toDate":"2026-11-08"}"""),
            json.parseToJsonElement(sent.body.readUtf8()),
        )
        assertEquals("/api/household-lists", server.takeRequest().path)
        assertEquals(household, made.id)
        // A list of a day after the household list's stays; those that share a day with it went.
        assertEquals(setOf("after-them"), lists.rows.keys)
        assertEquals(setOf(household), kept.rows.keys)
    }

    @Test
    fun `two plans of one profile are refused with the API's code`() {
        server.enqueue(answer(400, "household-list-profile-twice"))

        val refused = runCatching { runBlocking { repository().make(listOf(adamsPlan, "another of Adam's"), "2026-11-07", "2026-11-08") } }.exceptionOrNull() as ApiException

        assertEquals("HOUSEHOLD_PROFILE_TWICE", refused.code)
    }

    @Test
    fun `a row is ticked on the household list, and the list as the server then has it is kept`() {
        server.enqueue(answer(200, "household-list-ticked"))
        val kept = Kept()

        val ticked = runBlocking { repository(kept).change(household, pepper, RowChange(checked = true)) }

        val sent = server.takeRequest()
        assertEquals("PATCH", sent.method)
        assertEquals("/api/household-lists/$household/items/$pepper", sent.path)
        assertEquals(mapOf("checked" to "true"), json.parseToJsonElement(sent.body.readUtf8()).jsonObject.mapValues { it.value.jsonPrimitive.content })
        assertTrue((ticked as Submitted.Sent).answer.groups.first().items.first().checked)
        assertEquals(setOf(household), kept.rows.keys)
    }

    @Test
    fun `a row ticked without a network is kept, and sent in its turn`() {
        var answering = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (answering) answer(200, "household-list-ticked") else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        val queue = changeQueue()
        val households = repository(queue = queue)

        assertEquals(Submitted.Kept(offline = true), runBlocking { households.change(household, pepper, RowChange(checked = true)) })
        assertEquals(QueuedChange.HouseholdRow(household, pepper, checked = true), runBlocking { queue.waiting.first() }.single().change)

        answering = true
        assertEquals(Drained.EMPTY, runBlocking { queue.drain { households.send(it as QueuedChange.HouseholdRow) } })
        val again = generateSequence { server.takeRequest(0, TimeUnit.SECONDS) }.last()
        assertEquals("/api/household-lists/$household/items/$pepper", again.path)
    }

    @Test
    fun `a deleted household list takes its parts' copies with it, and one already gone is deleted all the same`() {
        server.enqueue(answer(200, "household-lists"))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(answer(404, "household-list-unknown"))
        val kept = Kept()
        val lists = Lists(
            planList("2484a554-e267-4903-aae4-ded94f21cf94", adamsPlan, "2026-11-07", "2026-11-08"),
            planList("15728f05-c24e-4c3f-a088-dfe653a49a8a", ewasPlan, "2026-11-07", "2026-11-08"),
            planList("another", ewasPlan, "2026-11-09", "2026-11-09"),
        )
        val households = repository(kept, lists)
        val list = runBlocking { households.refresh() }.single()
        server.takeRequest()

        runBlocking { households.delete(list) }
        runBlocking { households.delete(list) }

        assertEquals("DELETE", server.takeRequest().method)
        assertEquals("/api/household-lists/$household", server.takeRequest().path)
        assertTrue(kept.rows.isEmpty())
        assertEquals(setOf("another"), lists.rows.keys)
    }

    @Test
    fun `a plan's own list that is a part of a household list says so, with whom it is for`() {
        val part = json.decodeFromString<List<ShoppingListDto>>(fixture("shopping-lists-household-part")).single().toDomain()

        assertEquals(HouseholdOf(household, listOf("Adam", "Ewa")), part.household)
    }
}
