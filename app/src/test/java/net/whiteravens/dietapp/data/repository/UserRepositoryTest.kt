// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The account's settings changed one at a time, against a local server answering as the backend did. */
class UserRepositoryTest {
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

    private fun repository(): UserRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return UserRepository(AppModule.api(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json)
    }

    private fun answer(name: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(fixture(name))

    @Test
    fun `the unit chosen is sent alone and read back, and the choice given back sends a null the API reads as such`() {
        server.enqueue(answer("users-me-in-pounds"))
        server.enqueue(answer("users-me"))
        val users = repository()

        val inPounds = runBlocking { users.chooseWeightUnit("lb") }
        val givenBack = runBlocking { users.chooseWeightUnit(null) }

        val chosen = server.takeRequest()
        assertEquals("PATCH", chosen.method)
        assertEquals("/api/users/me", chosen.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"weightUnit":"lb"}"""), json.parseToJsonElement(chosen.body.readUtf8()))
        assertEquals(json.parseToJsonElement("""{"weightUnit":null}"""), json.parseToJsonElement(server.takeRequest().body.readUtf8()))
        assertEquals("lb", inPounds.weightUnit)
        assertNull(givenBack.weightUnit)
    }

    @Test
    fun `a new name is sent alone, and the account read back as the API keeps it`() {
        server.enqueue(answer("users-me-renamed-in-polish"))

        val renamed = runBlocking { repository().rename("Konto z telefonu") }

        val named = server.takeRequest()
        assertEquals("PATCH", named.method)
        assertEquals("/api/users/me", named.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"displayName":"Konto z telefonu"}"""), json.parseToJsonElement(named.body.readUtf8()))
        assertEquals("Konto z telefonu", renamed.displayName)
    }

    @Test
    fun `the language of the mails and the web is sent alone, and the account read back as the API keeps it`() {
        server.enqueue(answer("users-me-renamed-in-polish"))

        val inPolish = runBlocking { repository().chooseLanguage("pl") }

        val chosen = server.takeRequest()
        assertEquals("PATCH", chosen.method)
        assertEquals(json.parseToJsonElement("""{"locale":"pl"}"""), json.parseToJsonElement(chosen.body.readUtf8()))
        assertEquals("pl", inPolish.locale)
    }
}
