// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.auth.SessionEnd
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.LoginRequest
import net.whiteravens.dietapp.data.remote.RefreshRequest
import net.whiteravens.dietapp.data.remote.RegisterRequest
import net.whiteravens.dietapp.data.remote.apiCall
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session lifecycle. Sign-in persists the token pair; from then on the
 * authenticated client attaches and refreshes tokens transparently. Sign-out
 * ends the session on the device at once ([SessionEnd]: the offline cache, then
 * the tokens) and revokes it on the server; a revocation that cannot reach the
 * server is kept and tried again at the next start ([retryRevocation]).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApiService,
    private val tokens: TokenStore,
    private val sessionEnd: SessionEnd,
    private val json: Json,
) {
    /** Reactive sign-in state — drives the auth vs. main navigation graph. */
    val isSignedIn: Flow<Boolean> = tokens.session.map { it != null }

    suspend fun signIn(email: String, password: String) {
        val response = apiCall(json) { authApi.login(LoginRequest(email, password)) }
        tokens.save(response.tokens)
    }

    suspend fun register(email: String, password: String, displayName: String) {
        val response = apiCall(json) { authApi.register(RegisterRequest(email, password, displayName)) }
        tokens.save(response.tokens)
    }

    suspend fun signOut() {
        // Kept before anything else, so that a revocation cut short is still tried again.
        tokens.current()?.let { session -> tokens.setPendingRevocation(session.refreshToken) }
        sessionEnd.end()
        retryRevocation()
    }

    /** Revokes on the server a session signed out of on this device, if one is still waiting for it. */
    suspend fun retryRevocation() {
        val refreshToken = tokens.pendingRevocation() ?: return
        try {
            authApi.logout(RefreshRequest(refreshToken))
            tokens.setPendingRevocation(null)
        } catch (e: HttpException) {
            // The server refused it: the token is no good any more, which is what was wanted.
            tokens.setPendingRevocation(null)
        } catch (e: IOException) {
            // Still no server: tried again at the next start.
        }
    }
}
