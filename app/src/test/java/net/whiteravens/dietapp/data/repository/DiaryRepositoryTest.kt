// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.local.CachedDiaryDay
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.Drained
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.DiaryAmount
import net.whiteravens.dietapp.domain.Macros
import net.whiteravens.dietapp.domain.NewDiaryEntry
import net.whiteravens.dietapp.domain.Nutrition
import net.whiteravens.dietapp.testing.MemoryChanges
import net.whiteravens.dietapp.testing.MemoryKeptReads
import net.whiteravens.dietapp.testing.changeQueue
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/**
 * A day of a diary read and kept on the phone, an entry written, changed and taken out, without a
 * network as well, and a day copied, against a local server answering as the backend did.
 */
class DiaryRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer
    private val kept = MemoryKeptReads()

    private val profileId = "4914e35a-ba92-4295-bf2a-eb8054eca729"
    private val day = LocalDate.parse("2026-10-31")

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(queue: ChangeQueue = changeQueue()): DiaryRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return DiaryRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), kept, json, queue)
    }

    private fun answer(status: Int, name: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    private fun noNetwork() = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)

    private fun RecordedRequest.bodyJson(): JsonObject = json.parseToJsonElement(body.readUtf8()).jsonObject

    @Test
    fun `a day is asked for by its profile and date and read with its target, its plan and its entries`() {
        server.enqueue(answer(200, "diary-day"))

        val read = runBlocking { repository().day(profileId, day) }

        val asked = server.takeRequest().requestUrl!!
        assertEquals("/api/diary/day", asked.encodedPath)
        assertEquals(mapOf("profileId" to profileId, "date" to "2026-10-31"), asked.queryParameterNames.associateWith { asked.queryParameter(it) })
        assertEquals(Nutrition(1650.0, 103.0, 55.0, 186.0), read.target)
        assertTrue(read.targetFromPlan)
        assertEquals(Nutrition(1993.0, 107.0, 76.0, 221.0), read.eaten)
        assertTrue(read.estimated)
        val plan = checkNotNull(read.plan)
        assertEquals("over", plan.offTarget)
        assertEquals(805.0, plan.calorieDelta, 0.0)
        assertEquals(listOf("breakfast", "lunch", "snack", "dinner"), plan.meals.map { it.slot })
        assertEquals(listOf(true, false, false, false), plan.meals.map { it.eaten })
        assertEquals(448.0, plan.meals.first().calories)
    }

    @Test
    fun `an entry reads as what it is of, how much of it there was and what it comes to`() {
        server.enqueue(answer(200, "diary-day"))

        val entries = runBlocking { repository().day(profileId, day) }.entries.associateBy { it.name }

        val recipe = entries.getValue("Apple oat chia bites with peanut butter")
        assertEquals("recipe", recipe.kind)
        assertEquals(1.5, recipe.servings)
        assertNull(recipe.amount)
        assertEquals(339.0, recipe.calories)
        val egg = entries.getValue("Large egg")
        assertEquals("ingredient", egg.kind)
        assertNull(egg.servings)
        assertEquals(DiaryAmount(2.0, "piece", 2.0, "piece"), egg.amount)
        val soup = entries.getValue("Grandma's soup")
        assertTrue(soup.estimated)
        assertEquals(Macros(38.0, 20.0, 68.0), soup.macros)
    }

    @Test
    fun `a day read is kept, and read back the same without a network, saying when it was read`() {
        server.enqueue(answer(200, "diary-day"))
        val diary = repository()
        val before = System.currentTimeMillis()

        val read = runBlocking { diary.day(profileId, day) }

        val copy = checkNotNull(runBlocking { diary.keptDay(profileId, day) })
        assertEquals(read, copy.copy(syncedAt = null))
        assertTrue(checkNotNull(copy.syncedAt) >= before)
        assertNull(runBlocking { diary.keptDay(profileId, day.minusDays(1)) })
    }

    @Test
    fun `what can no longer be read of a day kept is dropped, and read as nothing`() {
        kept.days[profileId to "2026-10-31"] = CachedDiaryDay(profileId, "2026-10-31", "{\"not\":\"a day\"}", 1L)

        assertNull(runBlocking { repository().keptDay(profileId, day) })
        assertTrue(kept.days.isEmpty())
    }

    @Test
    fun `a day read long ago is forgotten when another is read, one read lately stays`() {
        val now = System.currentTimeMillis()
        kept.days[profileId to "2026-01-01"] = CachedDiaryDay(profileId, "2026-01-01", "{}", now - Duration.ofDays(15).toMillis())
        kept.days[profileId to "2026-10-20"] = CachedDiaryDay(profileId, "2026-10-20", "{}", now - Duration.ofDays(13).toMillis())
        server.enqueue(answer(200, "diary-day"))

        runBlocking { repository().day(profileId, day) }

        assertEquals(setOf("2026-10-20", "2026-10-31"), kept.days.keys.map { it.second }.toSet())
    }

    @Test
    fun `a profile the API says is not there takes what is kept of its diary with it, and only its`() {
        kept.days[profileId to "2026-10-30"] = CachedDiaryDay(profileId, "2026-10-30", "{}", System.currentTimeMillis())
        kept.days["other" to "2026-10-30"] = CachedDiaryDay("other", "2026-10-30", "{}", System.currentTimeMillis())
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("""{"statusCode":404,"error":"PROFILE_NOT_FOUND","message":"Profile not found."}"""))

        val refused = assertThrows(ApiException::class.java) { runBlocking { repository().day(profileId, day) } }

        assertEquals("PROFILE_NOT_FOUND", refused.code)
        assertEquals(setOf("other"), kept.days.keys.map { it.first }.toSet())
    }

    @Test
    fun `a recipe is written in servings, under a key of its own`() {
        server.enqueue(answer(201, "diary-entry-recipe"))

        val submitted = runBlocking { repository().add(profileId, day, NewDiaryEntry.OfRecipe("breakfast", "r", "Bites", 226.0, 1.5)) }

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/diary/entries", sent.requestUrl!!.encodedPath)
        UUID.fromString(sent.getHeader("Idempotency-Key"))
        val body = sent.bodyJson()
        assertEquals(setOf("profileId", "date", "mealType", "kind", "recipeId", "servings"), body.keys)
        assertEquals("recipe", body.getValue("kind").jsonPrimitive.content)
        assertEquals(1.5, body.getValue("servings").jsonPrimitive.double, 0.0)
        assertEquals(339.0, (submitted as Submitted.Sent).answer.calories)
    }

    @Test
    fun `an ingredient is written in an amount of a unit`() {
        server.enqueue(answer(201, "diary-entry-pieces"))

        runBlocking { repository().add(profileId, day, NewDiaryEntry.OfIngredient("second_breakfast", "i", "Large egg", "piece", 2.0, "piece")) }

        val body = server.takeRequest().bodyJson()
        assertEquals(setOf("profileId", "date", "mealType", "kind", "ingredientId", "amount"), body.keys)
        assertEquals("ingredient", body.getValue("kind").jsonPrimitive.content)
        val amount = body.getValue("amount").jsonObject
        assertEquals(2.0, amount.getValue("quantity").jsonPrimitive.double, 0.0)
        assertEquals("piece", amount.getValue("unit").jsonPrimitive.content)
    }

    @Test
    fun `a meal of one's own is written by its figures, and a macronutrient left out is not sent at all, for the API to estimate`() {
        server.enqueue(answer(201, "diary-entry-estimated"))

        val submitted = runBlocking { repository().add(profileId, day, NewDiaryEntry.OwnMeal("dinner", "  Grandma's soup ", 2.0, 300.0, null, null, null)) }

        val body = server.takeRequest().bodyJson()
        assertEquals(setOf("profileId", "date", "mealType", "kind", "name", "servings", "nutrition"), body.keys)
        assertEquals("meal", body.getValue("kind").jsonPrimitive.content)
        assertEquals("Grandma's soup", body.getValue("name").jsonPrimitive.content)
        assertEquals(setOf("calories"), body.getValue("nutrition").jsonObject.keys)
        assertTrue((submitted as Submitted.Sent).answer.estimated)
    }

    @Test
    fun `without a network an entry waits, and is sent with the same key once there is one, the server writing it once`() {
        val changes = MemoryChanges()
        val queue = changeQueue(changes)
        val diary = repository(queue)
        server.enqueue(noNetwork())

        val submitted = runBlocking { diary.add(profileId, day, NewDiaryEntry.OwnMeal("dinner", "Soup", 1.0, 300.0, null, null, null)) }

        assertEquals(Submitted.Kept(offline = true), submitted)
        val waiting = runBlocking { queue.waiting.first() }.single().change as QueuedChange.DiaryAdded
        assertEquals("Soup", waiting.name)
        assertEquals("2026-10-31", waiting.date)
        server.takeRequest()

        server.enqueue(answer(201, "diary-entry-estimated"))
        assertEquals(Drained.EMPTY, runBlocking { queue.drain { diary.send(it as QueuedChange.DiaryAdded) } })

        val sent = server.takeRequest()
        assertEquals(waiting.key, sent.getHeader("Idempotency-Key"))
        assertEquals(setOf("calories"), sent.bodyJson().getValue("nutrition").jsonObject.keys)
        assertTrue(changes.all.isEmpty())
    }

    @Test
    fun `an entry the server refuses for its day stays with the refusal, and is not sent again`() {
        val changes = MemoryChanges()
        val queue = changeQueue(changes)
        val diary = repository(queue)
        server.enqueue(noNetwork())
        runBlocking { diary.add(profileId, day, NewDiaryEntry.OwnMeal("dinner", "Soup", 1.0, 300.0, null, null, null)) }
        server.takeRequest()

        server.enqueue(answer(409, "diary-day-too-old"))
        runBlocking { queue.drain { diary.send(it as QueuedChange.DiaryAdded) } }

        assertEquals("DIARY_DAY_TOO_OLD", changes.all.single().refusedCode)
        assertFalse(runBlocking { queue.anyWaiting() })
    }

    @Test
    fun `a change of an entry sends what changes, by its id, and is read back as the API keeps it`() {
        server.enqueue(answer(200, "diary-entry-changed"))

        val submitted = runBlocking { repository().change(profileId, "e", "lunch", null, 300.0) }

        val sent = server.takeRequest()
        assertEquals("PATCH", sent.method)
        assertEquals("/api/diary/entries/e", sent.requestUrl!!.encodedPath)
        assertEquals(setOf("mealType", "quantity"), sent.bodyJson().keys)
        val answer = (submitted as Submitted.Sent).answer
        assertEquals("lunch", answer.slot)
        assertEquals(300.0, answer.amount?.quantity)
        assertEquals(58.0, answer.calories)
    }

    @Test
    fun `an entry taken out that the server no longer has was taken out, and a change of one is refused and said`() {
        val changes = MemoryChanges()
        val queue = changeQueue(changes)
        val diary = repository(queue)
        server.enqueue(noNetwork())
        server.enqueue(answer(404, "diary-entry-unknown"))
        runBlocking { diary.remove(profileId, "gone") }
        server.takeRequest()

        runBlocking { queue.drain { diary.send(it as QueuedChange.DiaryRemoved) } }

        assertEquals("/api/diary/entries/gone", server.takeRequest().requestUrl!!.encodedPath)
        assertTrue(changes.all.isEmpty())

        // A client of its own: a connection kept open does not meet the disconnection.
        val again = repository(queue)
        server.enqueue(noNetwork())
        server.enqueue(answer(404, "diary-entry-unknown"))
        runBlocking { again.change(profileId, "gone", null, 2.0, null) }
        server.takeRequest()
        runBlocking { queue.drain { again.send(it as QueuedChange.DiaryChanged) } }

        assertEquals("DIARY_ENTRY_NOT_FOUND", changes.all.single().refusedCode)
    }

    @Test
    fun `a day is copied under a key of its own, and the day it leaves is the one kept`() {
        server.enqueue(answer(201, "diary-copy"))
        val to = LocalDate.parse("2026-10-30")
        val diary = repository()

        val copied = runBlocking { diary.copy(profileId, day, to) }

        val sent = server.takeRequest()
        assertEquals("/api/diary/copy", sent.requestUrl!!.encodedPath)
        UUID.fromString(sent.getHeader("Idempotency-Key"))
        assertEquals(setOf("profileId", "from", "to"), sent.bodyJson().keys)
        assertEquals(6, copied.copied)
        assertEquals(6, copied.day.entries.size)
        assertNotNull(runBlocking { diary.keptDay(profileId, to) })
    }

    @Test
    fun `an ingredient found reads with the units the API converts, what a piece of it is called and what it holds`() {
        server.enqueue(answer(200, "ingredients-search-egg"))

        val found = runBlocking { repository().ingredients("egg") }

        assertEquals("/api/ingredients", server.takeRequest().requestUrl!!.encodedPath)
        val egg = found.first()
        assertTrue(egg.units.isNotEmpty())
        assertNotNull(egg.caloriesPer100)
        assertEquals("g", egg.canonicalUnit)
    }
}
