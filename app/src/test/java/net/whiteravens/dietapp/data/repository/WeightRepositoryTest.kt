// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.Drained
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.WeightEntry
import net.whiteravens.dietapp.domain.WeightPoint
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A profile's weights read from a moment on, also by day, one logged and one removed, without a
 * network as well, against a local server answering as the backend did.
 */
class WeightRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    private val profileId = "5c52d79a-1aa4-4614-8005-ff8210b3bc3b"
    private val logged = WeightEntry("97ae03e7-deac-44c8-be76-e472cf7491fd", 79.85, Instant.parse("2026-10-16T21:51:18.696Z"))
    private val earlier = WeightEntry("8536c0c6-0508-40a3-ace8-d871f47a5f0c", 80.6, Instant.parse("2026-10-13T07:30:00Z"))

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(queue: ChangeQueue = changeQueue()): WeightRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return WeightRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json, queue)
    }

    private fun answer(status: Int, name: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    @Test
    fun `the weights are asked for from a moment on and read the newest first, each at the moment it was taken`() {
        server.enqueue(answer(200, "weights"))

        val read = runBlocking { repository().entries(profileId, Instant.parse("2026-07-18T21:51:18.696Z")) }

        val asked = server.takeRequest().requestUrl!!
        assertEquals("/api/weights", asked.encodedPath)
        assertEquals(
            mapOf("profileId" to profileId, "since" to "2026-07-18T21:51:18.696Z"),
            asked.queryParameterNames.associateWith { asked.queryParameter(it) },
        )
        assertEquals(listOf(logged, earlier), read)
    }

    @Test
    fun `the weights by day are asked for where the phone is, from the day a moment falls on, and read the oldest day first, each with its mean`() {
        server.enqueue(answer(200, "weights-days"))

        val read = runBlocking { repository().days(profileId, Instant.parse("2026-10-18T00:00:00Z"), ZoneId.of("Europe/Warsaw")) }

        val asked = server.takeRequest().requestUrl!!
        assertEquals("/api/weights/days", asked.encodedPath)
        assertEquals(
            mapOf("profileId" to profileId, "since" to "2026-10-18T00:00:00Z", "timeZone" to "Europe/Warsaw"),
            asked.queryParameterNames.associateWith { asked.queryParameter(it) },
        )
        // A weight every other day: the mean of the first day counts the days of its week before the day asked from.
        assertEquals(
            listOf(
                WeightPoint(LocalDate.parse("2026-10-18"), 66.5, 66.65),
                WeightPoint(LocalDate.parse("2026-10-20"), 66.4, 66.55),
                WeightPoint(LocalDate.parse("2026-10-22"), 66.3, 66.45),
                WeightPoint(LocalDate.parse("2026-10-24"), 66.2, 66.35),
                WeightPoint(LocalDate.parse("2026-10-26"), 66.1, 66.25),
                WeightPoint(LocalDate.parse("2026-10-28"), 66.0, 66.15),
            ),
            read,
        )
    }

    @Test
    fun `a weight is logged with its profile, its kilograms and the moment it was taken, under a key of its own, and read back as the API keeps it`() {
        server.enqueue(answer(201, "weight-logged"))
        val before = Instant.now()

        val kept = runBlocking { repository().log(profileId, 79.85) }

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/weights", sent.requestUrl!!.encodedPath)
        val body = json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals(setOf("profileId", "kg", "recordedAt"), body.keys)
        assertEquals(profileId, body.getValue("profileId").jsonPrimitive.content)
        assertEquals(79.85, body.getValue("kg").jsonPrimitive.double, 0.0)
        val taken = Instant.parse(body.getValue("recordedAt").jsonPrimitive.content)
        assertTrue(!taken.isBefore(before) && !taken.isAfter(Instant.now()))
        UUID.fromString(sent.getHeader("Idempotency-Key"))
        assertEquals(Submitted.Sent(logged), kept)
    }

    @Test
    fun `a weight whose answer was lost is kept, and sent again in its turn with the same key and moment`() {
        var answering = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (answering) answer(201, "weight-logged") else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        val queue = changeQueue()
        val weights = repository(queue)

        val kept = runBlocking { weights.log(profileId, 79.85) }

        assertEquals(Submitted.Kept(offline = true), kept)
        answering = true
        assertEquals(Drained.EMPTY, runBlocking { queue.drain { weights.send(it as QueuedChange.WeightLogged) } })
        val first = server.takeRequest()
        val again = generateSequence { server.takeRequest(0, TimeUnit.SECONDS) }.last()
        assertEquals(first.getHeader("Idempotency-Key"), again.getHeader("Idempotency-Key"))
        assertEquals(first.body.readUtf8(), again.body.readUtf8())
    }

    @Test
    fun `a weight is removed by its id, and one that is not there is refused with its code`() {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(answer(404, "weight-entry-unknown"))
        val weights = repository()

        assertEquals(Submitted.Sent(Unit), runBlocking { weights.delete(profileId, logged.id) })
        val refused = runCatching { runBlocking { weights.delete(profileId, logged.id) } }.exceptionOrNull() as ApiException

        val sent = server.takeRequest()
        assertEquals("DELETE", sent.method)
        assertEquals("/api/weights/${logged.id}", sent.requestUrl!!.encodedPath)
        assertEquals(404, refused.statusCode)
        assertEquals("WEIGHT_ENTRY_NOT_FOUND", refused.code)
    }

    @Test
    fun `a removal whose answer was lost is done when the weight is gone by the time it is sent again`() {
        var answering = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (answering) answer(404, "weight-entry-unknown") else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        val queue = changeQueue()
        val weights = repository(queue)

        assertEquals(Submitted.Kept(offline = true), runBlocking { weights.delete(profileId, logged.id) })
        answering = true
        assertEquals(Drained.EMPTY, runBlocking { queue.drain { weights.send(it as QueuedChange.WeightDeleted) } })

        assertTrue(runBlocking { queue.waiting.first() }.isEmpty())
        assertTrue(runBlocking { queue.refused.first() }.isEmpty())
    }
}
