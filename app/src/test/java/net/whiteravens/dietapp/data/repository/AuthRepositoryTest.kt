// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.auth.PendingRevocation
import net.whiteravens.dietapp.data.auth.Session
import net.whiteravens.dietapp.data.auth.SessionEnd
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.config.AppearanceStore
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.notify.PhoneNotificationsSwitch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.AuthTokens
import net.whiteravens.dietapp.data.remote.LoginRequest
import net.whiteravens.dietapp.data.remote.RefreshRequest
import net.whiteravens.dietapp.data.remote.SecondFactorSignInRequest
import net.whiteravens.dietapp.data.remote.ServerAddress
import net.whiteravens.dietapp.di.AppModule
import net.whiteravens.dietapp.domain.SignInResult
import net.whiteravens.dietapp.testing.fixture
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Signing out. The session ends on the device at once, the cached copies of the account's data
 * before the tokens, and is revoked on the server; a revocation that cannot reach the server is
 * kept and sent at the next start, so that the refresh token does not stay good for its month.
 */
class AuthRepositoryTest {
    private val json = AppModule.json()

    /** The token store's pending revocation, kept in memory. */
    private class Tokens(var session: Session?, var pending: PendingRevocation? = null)

    /** The server the app talks to, [SERVER] unless a test changes it. */
    private var current = SERVER

    private fun servers(): ServerConfigStore {
        val servers = mockk<ServerConfigStore>()
        coEvery { servers.current() } answers { current }
        return servers
    }

    private fun tokens(held: Tokens): TokenStore {
        val tokens = mockk<TokenStore>()
        every { tokens.session } returns emptyFlow()
        coEvery { tokens.current() } answers { held.session }
        coEvery { tokens.clear() } answers { held.session = null }
        coEvery { tokens.pendingRevocation() } answers { held.pending }
        coEvery { tokens.setPendingRevocation(any()) } answers { held.pending = firstArg() }
        return tokens
    }

    private fun refused(status: Int) = HttpException(
        Response.error<Any>(status, """{"statusCode":$status,"error":"INVALID_REFRESH","message":"No."}""".toResponseBody("application/json".toMediaType())),
    )

    @Test
    fun `signing out keeps the revocation, ends the session on the device, then revokes it on the server`() {
        val held = Tokens(Session("access", "refresh-1"))
        val tokens = tokens(held)
        val sessionEnd = mockk<SessionEnd>()
        coEvery { sessionEnd.end() } answers { held.session = null }
        val api = mockk<AuthApiService>()
        coEvery { api.logout(any(), any()) } returns Unit

        runBlocking { AuthRepository(api, tokens, sessionEnd, servers(), json).signOut() }

        coVerifyOrder {
            tokens.setPendingRevocation(PendingRevocation("refresh-1", SERVER))
            sessionEnd.end()
            api.logout(ServerAddress(SERVER), RefreshRequest("refresh-1"))
            tokens.setPendingRevocation(null)
        }
        assertNull(held.session)
        assertNull(held.pending)
    }

    @Test
    fun `a sign-out without a server keeps the revocation, and the next start sends it`() {
        val held = Tokens(Session("access", "refresh-1"))
        val sessionEnd = mockk<SessionEnd>()
        coEvery { sessionEnd.end() } answers { held.session = null }
        val api = mockk<AuthApiService>()
        coEvery { api.logout(any(), any()) } throws IOException("no network")
        val auth = AuthRepository(api, tokens(held), sessionEnd, servers(), json)

        runBlocking { auth.signOut() }

        assertNull(held.session)
        assertEquals(PendingRevocation("refresh-1", SERVER), held.pending)

        coEvery { api.logout(any(), any()) } returns Unit
        runBlocking { auth.retryRevocation() }

        assertNull(held.pending)
        coVerify(exactly = 2) { api.logout(ServerAddress(SERVER), RefreshRequest("refresh-1")) }
    }

    @Test
    fun `a revocation kept from before a change of server goes to the server of its session alone`() {
        val held = Tokens(session = null, pending = PendingRevocation("refresh-1", SERVER))
        val api = mockk<AuthApiService>()
        coEvery { api.logout(any(), any()) } returns Unit
        current = "https://another.example.test/api/"

        runBlocking { AuthRepository(api, tokens(held), mockk(), servers(), json).retryRevocation() }

        coVerify(exactly = 1) { api.logout(any(), any()) }
        coVerify { api.logout(ServerAddress(SERVER), RefreshRequest("refresh-1")) }
        assertNull(held.pending)
    }

    @Test
    fun `a revocation the server refuses is not kept, the token being no good already`() {
        val held = Tokens(session = null, pending = PendingRevocation("refresh-1", SERVER))
        val api = mockk<AuthApiService>()
        coEvery { api.logout(any(), any()) } throws refused(401)

        runBlocking { AuthRepository(api, tokens(held), mockk(), servers(), json).retryRevocation() }

        assertNull(held.pending)
    }

    @Test
    fun `the end of a session takes the phone's notifications and the account's colours away and clears the cached copies, before the tokens`() {
        val cache = mockk<CacheDatabase>(relaxed = true)
        val tokens = mockk<TokenStore>(relaxed = true)
        val phoneNotifications = mockk<PhoneNotificationsSwitch>(relaxed = true)
        val appearance = mockk<AppearanceStore>(relaxed = true)

        runBlocking { SessionEnd(tokens, cache, phoneNotifications, appearance).end() }

        coVerifyOrder {
            phoneNotifications.forget()
            appearance.forgetAccount()
            cache.clearAllTables()
            tokens.clear()
        }
    }

    @Test
    fun `an account with two-step sign-in is asked for a second step, which a code answers with a session`() {
        val held = Tokens(session = null)
        val tokens = tokens(held)
        val saved = mutableListOf<AuthTokens>()
        val sessions = mutableListOf<String?>()
        coEvery { tokens.save(capture(saved), captureNullable(sessions)) } returns Unit
        val api = mockk<AuthApiService>()
        coEvery { api.login(any()) } returns json.decodeFromString(fixture("auth-login-two-step-account"))
        coEvery { api.loginSecondFactor(any()) } returns json.decodeFromString(fixture("auth-login-after"))
        val auth = AuthRepository(api, tokens, mockk(), servers(), json)

        val result = runBlocking { auth.signIn("ala@example.test", "A-long-password-1", checkToken = "check-1") }
        val challenge = (result as SignInResult.SecondStep).challenge
        assertEquals(emptyList<AuthTokens>(), saved)
        runBlocking { auth.answerSecondStep(challenge, " 123 456 ") }

        coVerify { api.login(LoginRequest("ala@example.test", "A-long-password-1", "check-1")) }
        coVerify { api.loginSecondFactor(SecondFactorSignInRequest(challenge, "123456")) }
        assertEquals(1, saved.size)
        assertEquals(listOf("80eb1686-7071-4650-a2e1-49a7edefce01"), sessions)
    }

    @Test
    fun `a sign-in and a sign-up keep the id of the session they opened, which the list of sessions names this phone's by`() {
        val tokens = tokens(Tokens(session = null))
        val sessions = mutableListOf<String?>()
        coEvery { tokens.save(any(), captureNullable(sessions)) } returns Unit
        val api = mockk<AuthApiService>()
        coEvery { api.login(any()) } returns json.decodeFromString(fixture("auth-login"))
        coEvery { api.register(any()) } returns json.decodeFromString(fixture("auth-register"))
        val auth = AuthRepository(api, tokens, mockk(), servers(), json)

        runBlocking {
            auth.signIn("ala@example.test", "A-long-password-1")
            auth.register("ala@example.test", "A-long-password-1", "Ala")
        }

        assertEquals(listOf("222dea42-e0d7-4792-af43-38277cb5d75f", "2d733c1c-2e91-4bf2-9131-6bc29175074e"), sessions)
    }

    @Test
    fun `a refusal says how long to wait where the server said`() {
        val refusal = HttpException(
            Response.error<Any>(
                """{"statusCode":429,"error":"TOO_MANY_ATTEMPTS","message":"Wait."}""".toResponseBody("application/json".toMediaType()),
                okhttp3.Response.Builder()
                    .request(okhttp3.Request.Builder().url("http://localhost/api/auth/login").build())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .header("Retry-After", "42")
                    .build(),
            ),
        )

        val failure = ApiException.from(refusal, json)

        assertEquals("TOO_MANY_ATTEMPTS", failure.code)
        assertEquals(42, failure.retryAfterSeconds)
    }

    private companion object {
        const val SERVER = "https://diet.example.test/api/"
    }
}
