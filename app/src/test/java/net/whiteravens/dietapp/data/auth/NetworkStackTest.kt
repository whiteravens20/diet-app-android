// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.AuthTokens
import net.whiteravens.dietapp.data.remote.BaseUrlInterceptor
import net.whiteravens.dietapp.data.remote.ClientHeaders
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.LoginRequest
import net.whiteravens.dietapp.data.remote.RefreshRequest
import net.whiteravens.dietapp.data.remote.RegisterRequest
import net.whiteravens.dietapp.data.remote.ServerAddress
import net.whiteravens.dietapp.data.remote.WeighRequest
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.testing.fixture
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
import java.io.IOException
import java.io.InterruptedIOException
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CountDownLatch
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

    /** The session as the token store holds it, null when signed out, and the id it was saved with. */
    private class Held(var session: Session?, var sessionId: String? = null)

    /** The clients as the application wires them; [ended] counts the sessions the stack ended. */
    private class Stack(val api: DietApiService, val auth: AuthApiService, val ended: AtomicInteger)

    private fun stack(held: Held): Stack {
        val tokens = mockk<TokenStore>()
        coEvery { tokens.current() } answers { held.session }
        coEvery { tokens.save(any(), any()) } answers {
            val pair = firstArg<AuthTokens>()
            held.session = Session(pair.accessToken, pair.refreshToken)
            held.sessionId = secondArg()
        }
        coEvery { tokens.clear() } answers { held.session = null }
        val ended = AtomicInteger()
        val sessionEnd = mockk<SessionEnd>()
        coEvery { sessionEnd.end() } answers {
            ended.incrementAndGet()
            held.session = null
        }
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        val context = mockk<Context>()
        every { context.getString(R.string.api_language) } returns "pl"

        val plain = AppModule.baseOkHttp(BaseUrlInterceptor(config), ClientHeaders(context)).newBuilder()
            .dispatcher(CallDispatcher(threads))
            .build()
        val authenticator = TokenAuthenticator(tokens, sessionEnd, AppModule.refreshApi(plain, json), json)
        val authed = AppModule.authedOkHttp(plain, AuthInterceptor(tokens), authenticator)
        return Stack(AppModule.api(authed, json), AppModule.authApi(plain, json), ended)
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

    /**
     * [backend], holding back the answers to the run-out token until [requests] of them have
     * arrived: the requests meet the run-out token together, however the threads are scheduled.
     */
    private fun together(requests: Int, backend: Dispatcher): Dispatcher {
        val arrived = CountDownLatch(requests)
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") == "Bearer run-out") {
                    arrived.countDown()
                    arrived.await(10, TimeUnit.SECONDS)
                }
                return backend.dispatch(request)
            }
        }
    }

    @Test
    fun `a request whose access token ran out is answered after one refresh, with the new pair kept and the session it belongs to`() {
        val held = aRunOutSession()
        val refreshes = AtomicInteger()
        server.dispatcher = backend({ answer("auth-refresh") }, refreshes)

        runBlocking { apiCall(json) { stack(held).api.me() } }

        assertEquals(1, refreshes.get())
        assertEquals("<removed>", held.session?.accessToken)
        assertEquals("222dea42-e0d7-4792-af43-38277cb5d75f", held.sessionId)
    }

    @Test
    fun `a refresh answered 401 ends the session, the cached copies with it`() {
        val held = aRunOutSession()
        server.dispatcher = backend({ refused(401, "INVALID_REFRESH") })
        val stack = stack(held)

        runBlocking { runCatching { apiCall(json) { stack.api.me() } } }

        assertNull(held.session)
        assertEquals(1, stack.ended.get())
    }

    @Test
    fun `a 401 that is not about the access token is the request's own answer, with no refresh`() {
        val held = aRunOutSession()
        val refreshes = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/auth/refresh" -> {
                    refreshes.incrementAndGet()
                    answer("auth-refresh")
                }
                else -> refused(401, "INVALID_PASSWORD")
            }
        }

        val failure = runBlocking { runCatching { apiCall(json) { stack(held).api.me() } }.exceptionOrNull() }

        assertEquals("INVALID_PASSWORD", (failure as ApiException).code)
        assertEquals(0, refreshes.get())
        assertEquals(Session("run-out", "refresh-1"), held.session)
    }

    @Test
    fun `a connection that drops during the refresh keeps the session`() {
        val held = aRunOutSession()
        server.dispatcher = backend({ MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) })

        runBlocking { runCatching { apiCall(json) { stack(held).api.me() } } }

        assertNotNull(held.session)
    }

    @Test
    fun `a refresh answered with anything but 401 keeps the session`() {
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
    fun `a refresh answered 200 with something other than a token pair leaves the application running`() {
        val escaped = Collections.synchronizedList(mutableListOf<Throwable>())
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> escaped += e }
        try {
            server.dispatcher = backend({
                MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Sign in to the network</html>")
            })
            val held = aRunOutSession()
            runBlocking { runCatching { apiCall(json) { stack(held).api.me() } } }
            assertNotNull("the session after a refresh answered with a page", held.session)
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
        server.dispatcher = together(4, backend({ answer("auth-refresh") }, refreshes))
        val api = stack(aRunOutSession()).api

        val answered = runBlocking {
            withTimeoutOrNull(10_000) { (1..4).map { GlobalScope.async { runCatching { api.me() }.isSuccess } }.awaitAll() }
        }

        assertEquals(List(4) { true }, answered)
        assertEquals(1, refreshes.get())
    }

    @Test
    fun `five and eight requests at once with a run-out token cause one refresh and are all answered`() {
        // OkHttp sends a host as many requests at a time as this; the rest wait for a slot.
        val inFlight = CallDispatcher().maxRequestsPerHost
        for (requests in listOf(5, 8)) {
            val refreshes = AtomicInteger()
            server.dispatcher = together(minOf(requests, inFlight), backend({ answer("auth-refresh") }, refreshes))
            val api = stack(aRunOutSession()).api

            // Not children of the test: a call that never ends must not keep the test from ending.
            val calls = (1..requests).map { GlobalScope.async { runCatching { api.me() }.isSuccess } }
            val answered = runBlocking { withTimeoutOrNull(10_000) { calls.awaitAll() } }

            assertEquals("$requests at once", List(requests) { true }, answered)
            assertEquals("$requests at once", 1, refreshes.get())
        }
    }

    @Test
    fun `every request says which app it is, its language and the user's day, signing in and refreshing included`() {
        val asked = Collections.synchronizedList(mutableListOf<RecordedRequest>())
        val refreshing = backend({ answer("auth-refresh") })
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request
                return when (request.path) {
                    "/api/auth/login" -> answer("auth-login")
                    else -> refreshing.dispatch(request)
                }
            }
        }
        val stack = stack(aRunOutSession())

        runBlocking {
            apiCall(json) { stack.auth.login(LoginRequest("ala@example.test", "A-long-password-1")) }
            apiCall(json) { stack.api.me() }
        }

        assertEquals(setOf("/api/auth/login", "/api/users/me", "/api/auth/refresh"), asked.map { it.path }.toSet())
        for (request in asked) {
            assertEquals(request.path, ClientHeaders.userAgent, request.getHeader("User-Agent"))
            assertTrue(request.path, request.getHeader("User-Agent")!!.startsWith("DietApp-Android/"))
            assertEquals(request.path, "pl", request.getHeader("Accept-Language"))
            assertEquals(request.path, LocalDate.now().toString(), request.getHeader("X-Client-Date"))
        }
    }

    @Test
    fun `a call that takes longer than half a minute is given up`() {
        val config = mockk<ServerConfigStore>()
        val context = mockk<Context>()

        val client = AppModule.baseOkHttp(BaseUrlInterceptor(config), ClientHeaders(context))

        assertEquals(30_000, client.callTimeoutMillis)
    }

    @Test
    fun `an answer that stops coming for ten seconds is given up`() {
        val config = mockk<ServerConfigStore>()
        val context = mockk<Context>()

        val client = AppModule.baseOkHttp(BaseUrlInterceptor(config), ClientHeaders(context))

        assertEquals(10_000, client.readTimeoutMillis)
    }

    @Test
    fun `a call a model takes part in is given two minutes, and its answer as long to begin`() {
        val config = mockk<ServerConfigStore>()
        val context = mockk<Context>()

        val client = AppModule.modelClient(AppModule.baseOkHttp(BaseUrlInterceptor(config), ClientHeaders(context)))

        assertEquals(120_000, client.callTimeoutMillis)
        assertEquals(120_000, client.readTimeoutMillis)
    }

    @Test
    fun `a route a model takes part in waits for an answer that begins after any other call would have been given up`() {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns server.url("/api/").toString()
        val context = mockk<Context>()
        every { context.getString(R.string.api_language) } returns "pl"
        // The other calls' limits cut to a second, so that an answer two seconds in the coming outlasts them.
        val short = AppModule.baseOkHttp(BaseUrlInterceptor(config), ClientHeaders(context)).newBuilder()
            .callTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
        repeat(2) { server.enqueue(answer("meal-plan-of-another-account", 404).setHeadersDelay(2, TimeUnit.SECONDS)) }

        val other = runCatching { runBlocking { apiCall(json) { AppModule.api(short, json).mealPlan("plan") } } }.exceptionOrNull()
        val asked = runCatching { runBlocking { apiCall(json) { AppModule.modelApi(short, json).useUp("plan", WeighRequest(false)) } } }.exceptionOrNull()

        assertTrue(other.toString(), other is InterruptedIOException)
        assertEquals("PLAN_NOT_FOUND", (asked as ApiException).code)
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

    @Test
    fun `a revocation goes to the server it names, not to the one in use`() {
        val issuer = MockWebServer()
        issuer.start()
        try {
            val revoked = SynchronousQueue<RecordedRequest>()
            issuer.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    revoked.offer(request, 5, TimeUnit.SECONDS)
                    return MockResponse().setResponseCode(204)
                }
            }
            val inUse = Collections.synchronizedList(mutableListOf<RecordedRequest>())
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    inUse += request
                    return MockResponse().setResponseCode(204)
                }
            }
            val stack = stack(Held(session = null))

            val call = GlobalScope.async {
                stack.auth.logout(ServerAddress(issuer.url("/api/").toString()), RefreshRequest("refresh-1"))
            }
            val request = revoked.poll(10, TimeUnit.SECONDS)
            runBlocking { call.await() }

            assertEquals("/api/auth/logout", request?.path)
            assertEquals("""{"refreshToken":"refresh-1"}""", request?.body?.readUtf8())
            assertEquals(emptyList<RecordedRequest>(), inUse.toList())
        } finally {
            issuer.shutdown()
        }
    }

    @Test
    fun `with no server chosen a request goes nowhere and fails as a missing connection does`() {
        val config = mockk<ServerConfigStore>()
        coEvery { config.current() } returns ""
        val context = mockk<Context>()
        every { context.getString(R.string.api_language) } returns "pl"
        val api = AppModule.authApi(AppModule.baseOkHttp(BaseUrlInterceptor(config), ClientHeaders(context)), json)

        val failure = runCatching { runBlocking { api.config() } }.exceptionOrNull()

        assertTrue("$failure", failure is IOException)
        assertEquals(0, server.requestCount)
    }
}
