// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.auth.SessionEnd
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.AccountSession
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** Where the account is signed in, this phone's session named, and sessions ended, against a local server answering as the backend did. */
class SessionRepositoryTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    private val phone = "83bc3b9b-0c12-47f6-a4f2-d4ad0b806338"
    private val firefox = "61adb10b-b80f-41de-8a84-927bd5f9381c"
    private val tokens = mockk<TokenStore>()
    private val sessionEnd = mockk<SessionEnd>(relaxed = true)

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        coEvery { tokens.sessionId() } returns phone
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun repository(): SessionRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return SessionRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), tokens, sessionEnd, json)
    }

    private fun answer(status: Int, name: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(fixture(name))

    @Test
    fun `the open sessions are read as the API lists them, the phone's own named by the id its sign-in kept`() {
        server.enqueue(answer(200, "account-sessions"))

        val open = runBlocking { repository().sessions() }

        assertEquals("/api/users/me/sessions", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(
            listOf(
                AccountSession(
                    "d0b064e0-6241-4e31-b5c4-ab1134d3e0d7", null,
                    Instant.parse("2026-10-17T08:42:56.048Z"), Instant.parse("2026-10-17T08:42:56.048Z"), Instant.parse("2027-01-15T08:42:56.047Z"), false,
                ),
                AccountSession(
                    firefox, "Firefox · Linux",
                    Instant.parse("2026-10-17T08:42:55.828Z"), Instant.parse("2026-10-17T08:42:55.828Z"), Instant.parse("2027-01-15T08:42:55.828Z"), false,
                ),
                AccountSession(
                    phone, "DietApp-Android · Android",
                    Instant.parse("2026-10-17T08:42:55.390Z"), Instant.parse("2026-10-17T08:42:55.390Z"), Instant.parse("2027-01-15T08:42:55.390Z"), true,
                ),
            ),
            open,
        )
    }

    @Test
    fun `a session is ended by its id, and only the phone's own ends the session on the phone`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val sessions = repository()

        runBlocking { sessions.end(firefox) }
        coVerify(exactly = 0) { sessionEnd.end() }
        server.enqueue(MockResponse().setResponseCode(204))
        runBlocking { sessions.end(phone) }

        val first = server.takeRequest()
        assertEquals("DELETE", first.method)
        assertEquals("/api/users/me/sessions/$firefox", first.requestUrl!!.encodedPath)
        assertEquals("/api/users/me/sessions/$phone", server.takeRequest().requestUrl!!.encodedPath)
        coVerify(exactly = 1) { sessionEnd.end() }
    }

    @Test
    fun `a session no longer open is refused with its code, and the phone stays signed in`() {
        server.enqueue(answer(404, "account-session-unknown"))

        val refused = runCatching { runBlocking { repository().end(phone) } }.exceptionOrNull() as ApiException

        assertEquals(404, refused.statusCode)
        assertEquals("SESSION_NOT_FOUND", refused.code)
        coVerify(exactly = 0) { sessionEnd.end() }
    }

    @Test
    fun `signing out everywhere asks for it once and ends the phone's session too`() {
        server.enqueue(MockResponse().setResponseCode(204))

        runBlocking { repository().endAll() }

        val sent = server.takeRequest()
        assertEquals("DELETE", sent.method)
        assertEquals("/api/users/me/sessions", sent.requestUrl!!.encodedPath)
        coVerify(exactly = 1) { sessionEnd.end() }
    }
}
