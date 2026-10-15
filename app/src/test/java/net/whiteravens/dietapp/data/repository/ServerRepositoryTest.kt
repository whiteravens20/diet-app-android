// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.ServerProbe
import net.whiteravens.dietapp.domain.ServerVersion
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

/**
 * The server's version as the app asks for it, with `GET /config` through the client the app
 * uses for it, against a local server. The answers are the one a running backend gave
 * (`config.json`, API 0.19.0, the version in contract/API_VERSION), and that answer with another
 * version in it.
 */
class ServerRepositoryTest {
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

    private fun repository(): ServerRepository {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return ServerRepository(AppModule.authApi(AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders()), json), json)
    }

    private fun asked(): ServerVersion {
        val repository = repository()
        runBlocking { repository.refresh() }
        return repository.version.value
    }

    private fun answer(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    /** The captured answer, with [version] where the backend named its own. */
    private fun configOfVersion(version: String): String {
        val captured = json.parseToJsonElement(fixture("config")).jsonObject
        val instance = captured.getValue("instance").jsonObject
        return JsonObject(captured + ("instance" to JsonObject(instance + ("version" to JsonPrimitive(version))))).toString()
    }

    @Test
    fun `the version the build was checked against is asked for at the config route and needs no word`() {
        server.enqueue(answer(fixture("config")))

        assertEquals(ServerVersion.Checked, asked())
        assertEquals("/api/config", server.takeRequest().path)
    }

    @Test
    fun `a server of another version is named, beside the version the build was checked against`() {
        server.enqueue(answer(configOfVersion("0.20.0")))

        assertEquals(ServerVersion.Other(server = "0.20.0", checkedAgainst = BuildConfig.API_VERSION), asked())
    }

    @Test
    fun `an answer without a version is one of a version the app does not support`() {
        server.enqueue(answer("""{"instance":{}}"""))

        assertEquals(ServerVersion.Unsupported, asked())
    }

    @Test
    fun `an answer that is not JSON is one of a version the app does not support`() {
        server.enqueue(answer("<!doctype html><title>Not the API</title>").setHeader("Content-Type", "text/html"))

        assertEquals(ServerVersion.Unsupported, asked())
    }

    @Test
    fun `a server without the config route runs a version the app does not support`() {
        server.enqueue(answer("""{"statusCode":404,"error":"NOT_FOUND","message":"Not found."}""", status = 404))

        assertEquals(ServerVersion.Unsupported, asked())
    }

    @Test
    fun `a server that fails says nothing about its version`() {
        server.enqueue(answer("""{"statusCode":500,"error":"INTERNAL","message":"Failed."}""", status = 500))

        assertEquals(ServerVersion.Unknown, asked())
    }

    @Test
    fun `no answer says nothing about the version`() {
        val repository = repository()
        server.shutdown()

        runBlocking { repository.refresh() }

        assertEquals(ServerVersion.Unknown, repository.version.value)
    }

    private fun probed(address: String = server.url("/api/").toString()): ServerProbe = runBlocking { repository().probe(address) }

    @Test
    fun `an address where the API is up answers, asked at its health route`() {
        server.enqueue(answer(HEALTH_UP))

        assertEquals(ServerProbe.Answers, probed())
        assertEquals("/api/health", server.takeRequest().path)
    }

    @Test
    fun `an API whose database is down is named for it`() {
        server.enqueue(answer(HEALTH_DOWN, status = 503))

        assertEquals(ServerProbe.DatabaseDown, probed())
    }

    @Test
    fun `something that answers but is not the API is named for it`() {
        server.enqueue(answer("<!doctype html><title>Not found</title>", status = 404).setHeader("Content-Type", "text/html"))
        server.enqueue(answer("<!doctype html><title>A website</title>").setHeader("Content-Type", "text/html"))
        server.enqueue(answer("""{"status":"UP"}"""))
        server.enqueue(answer("<!doctype html><title>Bad gateway</title>", status = 503).setHeader("Content-Type", "text/html"))

        assertEquals(ServerProbe.NotTheApi, probed())
        assertEquals(ServerProbe.NotTheApi, probed())
        assertEquals(ServerProbe.NotTheApi, probed())
        assertEquals(ServerProbe.NotTheApi, probed())
    }

    @Test
    fun `an address where nothing answers, or a name no server has, is named for what it is`() {
        val gone = MockWebServer().apply { start() }
        val address = gone.url("/api/").toString()
        gone.shutdown()

        assertEquals(ServerProbe.NoAnswer, probed(address))
        assertEquals(ServerProbe.UnknownHost, probed("https://no-such-server.invalid/api/"))
    }

    @Test
    fun `a server whose certificate the phone does not trust is named for it`() {
        val api = mockk<AuthApiService>()
        coEvery { api.health(any()) } throws SSLHandshakeException("Trust anchor for certification path not found.")

        assertEquals(ServerProbe.Untrusted, runBlocking { ServerRepository(api, json).probe("https://diet.example.test/api/") })
    }

    private companion object {
        const val HEALTH_UP = """{"status":"ok","db":"up","time":"2026-10-15T00:00:00.000Z"}"""
        const val HEALTH_DOWN = """{"status":"degraded","db":"down","time":"2026-10-15T00:00:00.000Z"}"""
    }
}
