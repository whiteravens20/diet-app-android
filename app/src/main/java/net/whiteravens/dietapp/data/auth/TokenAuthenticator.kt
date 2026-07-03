package net.whiteravens.dietapp.data.auth

import dagger.Lazy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.whiteravens.dietapp.data.remote.AuthApiService
import net.whiteravens.dietapp.data.remote.RefreshRequest
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transparent access-token refresh on 401 — the same contract as the web
 * client. Single-flight: concurrent 401s queue on the mutex and reuse the
 * token the first caller refreshed. A refresh rejected by the server ends the
 * session (tokens cleared → [TokenStore.session] observers see sign-out);
 * a network failure during refresh just fails this request and keeps the pair.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokens: TokenStore,
    // Lazy breaks the DI cycle: this authenticator is part of the authenticated
    // client, while AuthApiService rides the unauthenticated one.
    private val authApi: Lazy<AuthApiService>,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (priorAttempts(response) >= MAX_ATTEMPTS) return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        val freshToken = runBlocking {
            refreshMutex.withLock {
                val session = tokens.current() ?: return@runBlocking null
                // Another request already rotated the pair while we waited.
                if (session.accessToken != failedToken) return@runBlocking session.accessToken
                try {
                    val refreshed = authApi.get().refresh(RefreshRequest(session.refreshToken))
                    tokens.save(refreshed.tokens)
                    refreshed.tokens.accessToken
                } catch (e: HttpException) {
                    tokens.clear() // refresh token revoked/expired — sign out
                    null
                } catch (e: IOException) {
                    null // offline mid-refresh — keep the session, fail this call
                }
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }

    private fun priorAttempts(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
