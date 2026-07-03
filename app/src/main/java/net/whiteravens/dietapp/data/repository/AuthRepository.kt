package net.whiteravens.dietapp.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.LoginRequest
import net.whiteravens.dietapp.data.remote.RefreshRequest
import net.whiteravens.dietapp.data.remote.RegisterRequest
import net.whiteravens.dietapp.data.remote.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session lifecycle. Sign-in persists the token pair; from then on the
 * authenticated client attaches and refreshes tokens transparently. Sign-out
 * revokes the refresh token (best-effort), then clears tokens and the offline
 * cache — cached plans belong to the account, not the device.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApiService,
    private val tokens: TokenStore,
    private val db: CacheDatabase,
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
        tokens.current()?.let { session ->
            // Best-effort revocation; a dead network must not block local sign-out.
            runCatching { authApi.logout(RefreshRequest(session.refreshToken)) }
        }
        tokens.clear()
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }
}
