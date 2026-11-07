// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.Drained
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.WaterEntry
import net.whiteravens.dietapp.domain.WaterPoint
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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A profile's day of water read, an amount added and an entry removed, without a network as well,
 * against a local server answering as the backend does.
 */
class WaterRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    private val profileId = "5c52d79a-1aa4-4614-8005-ff8210b3bc3b"
    private val day = LocalDate.parse("2026-11-07")

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(queue: ChangeQueue = changeQueue()): WaterRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return WaterRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json, queue)
    }

    private fun answer(status: Int, name: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    @Test
    fun `a day is asked for by its profile and date, and read with its last entry and the seven days that end with it`() {
        server.enqueue(answer(200, "water-day"))

        val read = runBlocking { repository().day(profileId, day) }

        val asked = server.takeRequest().requestUrl!!
        assertEquals("/api/water", asked.encodedPath)
        assertEquals(mapOf("profileId" to profileId, "day" to "2026-11-07"), asked.queryParameterNames.associateWith { asked.queryParameter(it) })
        assertEquals(600, read.ml)
        assertEquals(2000, read.target)
        assertEquals(WaterEntry("b6ae1a19-555e-40bd-878b-fb9198a3395b", 250, Instant.parse("2026-11-07T21:17:09.557Z")), read.last)
        assertEquals(7, read.days.size)
        assertEquals(WaterPoint(day, 600), read.days.last())
        // On target: the first, the fourth and the sixth day.
        assertEquals(listOf("2026-11-01", "2026-11-04", "2026-11-06"), read.reached.map { it.day.toString() })
    }

    @Test
    fun `water is added to its profile's day under a key of its own, and the day read back as the API then has it`() {
        server.enqueue(answer(201, "water-added"))

        val added = runBlocking { repository().add(profileId, day, 330) }

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/water", sent.requestUrl!!.encodedPath)
        val body = json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals(setOf("profileId", "day", "ml"), body.keys)
        assertEquals(profileId, body.getValue("profileId").jsonPrimitive.content)
        assertEquals("2026-11-07", body.getValue("day").jsonPrimitive.content)
        assertEquals(330, body.getValue("ml").jsonPrimitive.int)
        UUID.fromString(sent.getHeader("Idempotency-Key"))
        assertEquals(930, (added as Submitted.Sent).answer.ml)
    }

    @Test
    fun `water added without a network is kept, and sent again in its turn with the same key and day`() {
        var answering = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (answering) answer(201, "water-added") else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        val queue = changeQueue()
        val water = repository(queue)

        assertEquals(Submitted.Kept(offline = true), runBlocking { water.add(profileId, day, 330) })
        val kept = runBlocking { queue.waiting.first() }.single().change as QueuedChange.WaterAdded
        assertEquals(QueuedChange.WaterAdded(profileId, "2026-11-07", 330, kept.key), kept)

        answering = true
        assertEquals(Drained.EMPTY, runBlocking { queue.drain { water.send(it as QueuedChange.WaterAdded) } })
        val first = server.takeRequest()
        val again = generateSequence { server.takeRequest(0, TimeUnit.SECONDS) }.last()
        assertEquals(kept.key, again.getHeader("Idempotency-Key"))
        assertEquals(first.getHeader("Idempotency-Key"), again.getHeader("Idempotency-Key"))
        assertEquals(first.body.readUtf8(), again.body.readUtf8())
    }

    @Test
    fun `an entry is removed by its id, and its removal is done when the entry is gone by the time it is sent again`() {
        server.enqueue(answer(200, "water-removed"))
        val entry = WaterEntry("b6ae1a19-555e-40bd-878b-fb9198a3395b", 250, Instant.parse("2026-11-07T21:17:09.557Z"))

        val removed = runBlocking { repository().delete(profileId, day, entry) }

        val sent = server.takeRequest()
        assertEquals("DELETE", sent.method)
        assertEquals("/api/water/${entry.id}", sent.requestUrl!!.encodedPath)
        // 600 ml and the 330 added before, less the 250 taken away.
        assertEquals(680, (removed as Submitted.Sent).answer.ml)

        var answering = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (answering) answer(404, "water-entry-unknown") else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        val queue = changeQueue()
        val water = repository(queue)
        assertEquals(Submitted.Kept(offline = true), runBlocking { water.delete(profileId, day, entry) })
        answering = true
        assertEquals(Drained.EMPTY, runBlocking { queue.drain { water.send(it as QueuedChange.WaterDeleted) } })
        assertTrue(runBlocking { queue.waiting.first() }.isEmpty())
        assertTrue(runBlocking { queue.refused.first() }.isEmpty())
    }
}
