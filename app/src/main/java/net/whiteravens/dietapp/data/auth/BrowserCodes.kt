// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.remote.RefreshApiService
import net.whiteravens.dietapp.data.remote.RefreshRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * Codes with which the web application's page signs the phone's browser in to this account
 * (`POST /auth/browser-code`). Asking for one is a refresh: it uses up the session's refresh token,
 * so it waits its turn with the refreshes ([RefreshTurns]) and keeps the pair it is given as a
 * refresh keeps it. As for a refresh, only a 401 ends the session; any other answer, or none,
 * leaves the session as it was and the page to be opened without a code.
 */
@Singleton
class BrowserCodes @Inject constructor(
    private val tokens: TokenStore,
    private val sessionEnd: SessionEnd,
    private val refreshApi: RefreshApiService,
    private val turns: RefreshTurns,
) {
    /** A code for the browser, or null when there is none to be had now. It blocks: call it off the main thread. */
    fun next(): String? = turns.lock.withLock {
        val session = runBlocking { tokens.current() } ?: return null
        val answer = try {
            refreshApi.browserCode(RefreshRequest(session.refreshToken)).execute()
        } catch (e: Exception) {
            return null
        }
        when {
            answer.isSuccessful -> answer.body()?.let { given ->
                runBlocking { tokens.save(given.tokens, given.sessionId) }
                given.code
            }
            answer.code() == 401 -> {
                runBlocking { sessionEnd.end() }
                null
            }
            else -> null
        }
    }
}
