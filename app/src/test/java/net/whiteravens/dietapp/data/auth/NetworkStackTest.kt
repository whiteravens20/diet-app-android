// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.AuthTokens
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.LoginRequest
import net.whiteravens.dietapp.data.remote.RegisterRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.fixture
import net.whiteravens.dietapp.testing.knownDefect
import okhttp3.Dispatcher as CallDispatcher
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The client's network stack (the interceptors, the authenticator, Retrofit and the JSON
 * converter, wired the way the application wires them) against a local server that answers what
 * it is told to. The backend's rules for a client that keeps its own tokens decide what is right:
 * a new access token is asked for when one runs out, and only a 401 to that request ends the
 * session. The token store and the server setting need a device and are stood in for.
 */
@OptIn(DelicateCoroutinesApi::class)
class NetworkStackTest {
    private val json = AppModule.json()
    private lateinit var server: MockWebServer

    // The calls' threads are daemons and stopped after each test, so that a call that never ends
    // cannot keep the build from ending.
    private val threads = ThreadPoolExecutor(0, Int.MAX_VALUE, 60, TimeUnit.SECONDS, SynchronousQueue()) { task ->
        Thread(task, "calls of the test").apply { isDaemon = true }
    }

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        // Interrupted, a call held inside the authenticator fails and its thread ends; waiting for
        // that keeps what it throws from reaching the next test.
        threads.shutdownNow()
        threads.awaitTermination(10, TimeUnit.SECONDS)
        runCatching { server.shutdown() }
    }

    /** The session as the token store holds it; null is signed out. */
    private class Held(var session: Session?)

    private class Stack(val api: DietApiService, val auth: AuthApiService)

    private fun stack(held: Held): Stack {
        val tokens = mockk<TokenStore>()
        coEvery { tokens.current() } answers { held.session }
        coEvery { tokens.save(any()) } answers {
            val pair = firstArg<AuthTokens>()
            held.session = Session(pair.accessToken, pair.refreshToken)
        }
        coEvery { tokens.clear() } answers { held.session = null }
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()

        val plain = AppModule.baseOkHttp(BaseUrlInterceptor(config)).newBuilder()
            .dispatcher(CallDispatcher(threads))
            .build()
        lateinit var auth: AuthApiService
        val authed = AppModule.authedOkHttp(plain, AuthInterceptor(tokens), TokenAuthenticator(tokens) { auth })
        auth = AppModule.authApi(plain, json)
        return Stack(AppModule.api(authed, json), auth)
    }

    private fun answer(name: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(fixture(name))

    private fun refused(status: Int, code: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"statusCode":$status,"error":"$code","message":"Refused."}""")

    /** Answers the account with a valid token, 401 to the run-out one, and [refresh] to a refresh. */
    private fun backend(refresh: () -> MockResponse, refreshes: AtomicInteger = AtomicInteger()) =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/auth/refresh" -> {
                    refreshes.incrementAndGet()
                    refresh()
                }
                request.getHeader("Authorization") == "Bearer run-out" -> refused(401, "INVALID_ACCESS_TOKEN")
                else -> answer("users-me")
            }
        }

    private fun aRunOutSession() = Held(Session("run-out", "refresh-1"))

    @Test
    fun `a request whose access token ran out is answered after one refresh, with the new pair kept`() {
        val held = aRunOutSession()
        val refreshes = AtomicInteger()
        server.dispatcher = backend({ answer("auth-refresh") }, refreshes)

        runBlocking { apiCall(json) { stack(held).api.me() } }

        assertEquals(1, refreshes.get())
        assertEquals("<removed>", held.session?.accessToken)
    }

    @Test
    fun `a refresh answered 401 ends the session`() {
        val held = aRunOutSession()
        server.dispatcher = backend({ refused(401, "INVALID_REFRESH") })

        runBlocking { runCatching { apiCall(json) { stack(held).api.me() } } }

        assertNull(held.session)
    }

    @Test
    fun `a connection that drops during the refresh keeps the session`() {
        val held = aRunOutSession()
        server.dispatcher = backend({ MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) })

        runBlocking { runCatching { apiCall(json) { stack(held).api.me() } } }

        assertNotNull(held.session)
    }

    @Test
    fun `a refresh answered with anything but 401 keeps the session`() = knownDefect(
        "every HTTP error of a refresh clears the tokens",
    ) {
        val answers = listOf(
            "409 SESSION_JUST_REFRESHED" to { answer("auth-refresh-used-token-at-once", 409) },
            "429 with Retry-After" to { refused(429, "RATE_LIMITED").setHeader("Retry-After", "30") },
            "500" to { refused(500, "INTERNAL") },
            "502 from a proxy" to { MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>") },
            "503" to { refused(503, "UNAVAILABLE") },
        )
        for ((name, refresh) in answers) {
            val held = aRunOutSession()
            server.dispatcher = backend(refresh)
            runBlocking { runCatching { apiCall(json) { stack(held).api.me() } } }
            assertNotNull("the session after a refresh answered $name", held.session)
        }
    }

    @Test
    fun `a refresh answered 200 with something other than a token pair leaves the application running`() = knownDefect(
        "the answer is decoded inside OkHttp, where the exception it throws reaches the thread's handler",
    ) {
        val escaped = Collections.synchronizedList(mutableListOf<Throwable>())
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> escaped += e }
        try {
            server.dispatcher = backend({
                MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Sign in to the network</html>")
            })
            runBlocking { runCatching { apiCall(json) { stack(aRunOutSession()).api.me() } } }
            // An exception thrown on one of OkHttp's threads arrives after the call has returned.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (escaped.isEmpty() && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("escaped to a thread's handler: $escaped", escaped.isEmpty())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }

    @Test
    fun `four requests at once with a run-out token cause one refresh and are all answered`() {
        val refreshes = AtomicInteger()
        server.dispatcher = backend({ answer("auth-refresh") }, refreshes)
        val api = stack(aRunOutSession()).api

        val answered = runBlocking {
            withTimeoutOrNull(10_000) { (1..4).map { GlobalScope.async { runCatching { api.me() }.isSuccess } }.awaitAll() }
        }

        assertEquals(List(4) { true }, answered)
        assertEquals(1, refreshes.get())
    }

    @Test
    fun `five requests at once with a run-out token are all answered`() = knownDefect(
        "the authenticator holds the dispatcher's five slots for one host while the refresh waits for one of them",
    ) {
        server.dispatcher = backend({ answer("auth-refresh") })
        val api = stack(aRunOutSession()).api

        // Not children of the test: a call that never ends must not keep the test from ending.
        val calls = (1..5).map { GlobalScope.async { runCatching { api.me() }.isSuccess } }
        val answered = runBlocking { withTimeoutOrNull(10_000) { calls.awaitAll() } }

        assertEquals(List(5) { true }, answered)
    }

    @Test
    fun `signing in and registering send no access token, and other calls send the one held`() {
        val asked = Collections.synchronizedList(mutableListOf<RecordedRequest>())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request
                return when (request.path) {
                    "/api/auth/register" -> answer("auth-register", 201)
                    "/api/auth/login" -> answer("auth-login")
                    else -> answer("users-me")
                }
            }
        }
        val stack = stack(Held(Session("held-access", "held-refresh")))

        runBlocking {
            apiCall(json) { stack.auth.register(RegisterRequest("ala@example.test", "A-long-password-1", "Ala")) }
            apiCall(json) { stack.auth.login(LoginRequest("ala@example.test", "A-long-password-1")) }
            apiCall(json) { stack.api.me() }
        }

        val byPath = asked.associateBy { it.path }
        assertNull(byPath.getValue("/api/auth/register").getHeader("Authorization"))
        assertNull(byPath.getValue("/api/auth/login").getHeader("Authorization"))
        assertEquals("Bearer held-access", byPath.getValue("/api/users/me").getHeader("Authorization"))
    }
}
