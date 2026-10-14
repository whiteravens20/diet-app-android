// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.whiteravens.dietapp.data.remote.RefreshApiService
import net.whiteravens.dietapp.data.remote.RefreshRequest
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * Answers a request refused for the access token it carried with a new token, the way the
 * backend asks a client that keeps its own tokens to:
 *
 * - only a 401 that says the token is no good (`UNAUTHORIZED`, `INVALID_ACCESS_TOKEN`) is
 *   answered with a refresh; another 401, a wrong password, is the request's own answer;
 * - the refresh is a synchronous call on a client of its own ([RefreshApiService]), one at a
 *   time: the requests that wait here hold the slots of their client, and a refresh that needed
 *   one would wait for ever. A request that waited while another refreshed takes the token that
 *   one got;
 * - only a 401 to the refresh ends the session ([SessionEnd]). Any other answer, an answer that
 *   cannot be read, or none, fails the request and keeps the session for the next one.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokens: TokenStore,
    private val sessionEnd: SessionEnd,
    private val refreshApi: RefreshApiService,
    private val json: Json,
) : Authenticator {

    private val lock = ReentrantLock()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (priorAttempts(response) >= MAX_ATTEMPTS) return null
        if (refusal(response) !in TOKEN_REFUSALS) return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val freshToken = lock.withLock { tokenInstead(failedToken) } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }

    /** The token to send instead of [failedToken], or null when there is none to be had now. */
    private fun tokenInstead(failedToken: String?): String? {
        val session = runBlocking { tokens.current() } ?: return null
        // Another request rotated the pair while this one waited.
        if (session.accessToken != failedToken) return session.accessToken
        val answer = try {
            refreshApi.refresh(RefreshRequest(session.refreshToken)).execute()
        } catch (e: Exception) {
            return null
        }
        return when {
            answer.isSuccessful -> answer.body()?.tokens?.let { pair ->
                runBlocking { tokens.save(pair) }
                pair.accessToken
            }
            answer.code() == 401 -> {
                runBlocking { sessionEnd.end() }
                null
            }
            answer.code() == 409 && refusal(answer.errorBody()?.string()) == "SESSION_JUST_REFRESHED" ->
                tokensOfTheOtherRefresh(session)
            else -> null
        }
    }

    /**
     * The pair another holder of the session's refresh token got just now, if it is stored here
     * within a moment; the backend takes a refresh token once in ten seconds.
     */
    private fun tokensOfTheOtherRefresh(stale: Session): String? {
        repeat(WAITS) {
            Thread.sleep(WAIT_MILLIS)
            val current = runBlocking { tokens.current() } ?: return null
            if (current != stale) return current.accessToken
        }
        return null
    }

    private fun refusal(response: Response): String? =
        refusal(runCatching { response.peekBody(MAX_REFUSAL_BYTES).string() }.getOrNull())

    /** The code of a refusal in the backend's envelope (`error`), or null for any other body. */
    private fun refusal(body: String?): String? =
        body?.let { runCatching { json.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull() }

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
        val TOKEN_REFUSALS = setOf("UNAUTHORIZED", "INVALID_ACCESS_TOKEN")
        const val MAX_REFUSAL_BYTES = 16_384L
        const val WAITS = 8
        const val WAIT_MILLIS = 250L
    }
}
