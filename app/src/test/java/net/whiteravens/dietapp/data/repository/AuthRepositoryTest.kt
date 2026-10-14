// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.auth.Session
import net.whiteravens.dietapp.data.auth.SessionEnd
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.RefreshRequest
import net.whiteravens.dietapp.di.AppModule
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
    private class Tokens(var session: Session?, var pending: String? = null)

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
        coEvery { api.logout(any()) } returns Unit

        runBlocking { AuthRepository(api, tokens, sessionEnd, json).signOut() }

        coVerifyOrder {
            tokens.setPendingRevocation("refresh-1")
            sessionEnd.end()
            api.logout(RefreshRequest("refresh-1"))
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
        coEvery { api.logout(any()) } throws IOException("no network")
        val auth = AuthRepository(api, tokens(held), sessionEnd, json)

        runBlocking { auth.signOut() }

        assertNull(held.session)
        assertEquals("refresh-1", held.pending)

        coEvery { api.logout(any()) } returns Unit
        runBlocking { auth.retryRevocation() }

        assertNull(held.pending)
        coVerify(exactly = 2) { api.logout(RefreshRequest("refresh-1")) }
    }

    @Test
    fun `a revocation the server refuses is not kept, the token being no good already`() {
        val held = Tokens(session = null, pending = "refresh-1")
        val api = mockk<AuthApiService>()
        coEvery { api.logout(any()) } throws refused(401)

        runBlocking { AuthRepository(api, tokens(held), mockk(), json).retryRevocation() }

        assertNull(held.pending)
    }

    @Test
    fun `the end of a session clears the cached copies before the tokens`() {
        val cache = mockk<CacheDatabase>(relaxed = true)
        val tokens = mockk<TokenStore>(relaxed = true)

        runBlocking { SessionEnd(tokens, cache).end() }

        coVerifyOrder {
            cache.clearAllTables()
            tokens.clear()
        }
    }
}
