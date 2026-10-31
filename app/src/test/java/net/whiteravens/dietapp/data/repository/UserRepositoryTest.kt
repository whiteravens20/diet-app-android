// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.whiteravens.dietapp.data.config.AppearanceStore
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.MemoryKeptReads
import net.whiteravens.dietapp.testing.clientHeaders
import net.whiteravens.dietapp.testing.fixture
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

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

    private val appearance = mockk<AppearanceStore>(relaxed = true)

    private val kept = MemoryKeptReads()

    private fun repository(): UserRepository = UserRepository(AppModule.api(client(), json), json, appearance, kept)

    private fun client(): OkHttpClient {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        return AppModule.baseOkHttp(BaseUrlInterceptor(config), clientHeaders())
    }

    private fun answer(name: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(fixture(name))

    @Test
    fun `the profiles read are kept for a start without a network, those of the last read alone and in their order`() {
        server.enqueue(answer("profiles"))
        val users = repository()

        val read = runBlocking { users.profiles() }

        val keptNow = runBlocking { users.keptProfiles() }
        assertEquals(read, keptNow?.profiles)
        assertTrue(read.isNotEmpty())

        server.enqueue(answer("profiles-none"))
        runBlocking { users.profiles() }

        assertNull(runBlocking { users.keptProfiles() })
    }

    @Test
    fun `without a network the profiles are those kept, and before any was kept the failure stands`() {
        server.enqueue(answer("profiles"))
        val read = runBlocking { repository().profiles() }

        // Each with a client of its own: a connection kept open does not meet the disconnection.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(read, runBlocking { repository().profilesOrKept() })

        val never = UserRepository(AppModule.api(client(), json), json, appearance, MemoryKeptReads())
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertThrows(IOException::class.java) { runBlocking { never.profilesOrKept() } }
    }

    @Test
    fun `a refusal of the server is no want of a network, the profiles kept are not shown for it`() {
        val users = repository()
        server.enqueue(answer("profiles"))
        runBlocking { users.profiles() }
        assertNotNull(runBlocking { users.keptProfiles() })

        server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json").setBody(fixture("without-a-token")))
        assertThrows(ApiException::class.java) { runBlocking { users.profilesOrKept() } }
    }

    @Test
    fun `the account's colour scheme is kept as every answer about the account says it`() {
        server.enqueue(answer("users-me"))
        server.enqueue(answer("users-me-in-pounds"))
        val users = repository()

        val read = runBlocking { users.me() }
        runBlocking { users.chooseWeightUnit("lb") }

        assertEquals("default", read.palette)
        coVerify(exactly = 2) { appearance.heard("default") }
    }

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
    fun `the AI mode is sent alone and the account read back, and its own providers' mode is refused while it has none`() {
        server.enqueue(answer("users-me-ai-admin"))
        server.enqueue(answer("ai-mode-byok-without-a-provider").setResponseCode(400))
        val users = repository()

        val shared = runBlocking { users.chooseAiMode("admin") }
        val refused = runCatching { runBlocking { users.chooseAiMode("byok") } }.exceptionOrNull() as ApiException

        val chosen = server.takeRequest()
        assertEquals("PATCH", chosen.method)
        assertEquals("/api/users/me", chosen.requestUrl!!.encodedPath)
        assertEquals(json.parseToJsonElement("""{"aiMode":"admin"}"""), json.parseToJsonElement(chosen.body.readUtf8()))
        assertEquals("admin", shared.aiMode)
        assertEquals("BYOK_NO_PROVIDER", refused.code)
    }

    @Test
    fun `the AI mode every answer about the account says is told to whoever follows it`() {
        server.enqueue(answer("users-me"))
        server.enqueue(answer("users-me-ai-admin"))
        val users = repository()

        val told = runBlocking {
            val following = async(start = CoroutineStart.UNDISPATCHED) { users.aiModeChanges.take(2).toList() }
            users.me()
            users.chooseAiMode("admin")
            // An answer that tells nobody leaves the follower waiting: it is given up after a while.
            withTimeoutOrNull(5_000) { following.await() }.also { following.cancel() }
        }

        assertEquals(listOf("none", "admin"), told)
    }
}
