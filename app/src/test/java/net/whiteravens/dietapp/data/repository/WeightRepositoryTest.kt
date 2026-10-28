// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.WeightEntry
import net.whiteravens.dietapp.domain.WeightPoint
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A profile's weights read from a moment on, also by day, one logged and one removed, against a local server answering as the backend did. */
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

    private fun repository(): WeightRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return WeightRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json)
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
    fun `a weight is logged with its profile and its kilograms alone, the moment left to the API, and read back as the API keeps it`() {
        server.enqueue(answer(201, "weight-logged"))

        val kept = runBlocking { repository().log(profileId, 79.85) }

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/weights", sent.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"profileId":"$profileId","kg":79.85}"""), json.parseToJsonElement(sent.body.readUtf8()))
        assertEquals(logged, kept)
    }

    @Test
    fun `a weight is removed by its id, and one that is not there is refused with its code`() {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(answer(404, "weight-entry-unknown"))
        val weights = repository()

        runBlocking { weights.delete(logged.id) }
        val refused = runCatching { runBlocking { weights.delete(logged.id) } }.exceptionOrNull() as ApiException

        val sent = server.takeRequest()
        assertEquals("DELETE", sent.method)
        assertEquals("/api/weights/${logged.id}", sent.requestUrl!!.encodedPath)
        assertEquals(404, refused.statusCode)
        assertEquals("WEIGHT_ENTRY_NOT_FOUND", refused.code)
    }
}
