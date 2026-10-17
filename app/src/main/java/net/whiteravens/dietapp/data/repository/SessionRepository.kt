// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.repository

import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.auth.SessionEnd
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.remote.AccountSessionDto
import net.whiteravens.dietapp.data.remote.DietApiService
import net.whiteravens.dietapp.data.remote.apiCall
import net.whiteravens.dietapp.domain.AccountSession
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the account is signed in: its open sessions, this phone's named among them by the id its
 * sign-in was given, and their end, one or all. A session the server ends for this phone ends here
 * too, the way a sign-out does, with nothing left to revoke.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: DietApiService,
    private val tokens: TokenStore,
    private val sessionEnd: SessionEnd,
    private val json: Json,
) {
    /** The account's open sessions, as the API lists them, the one used last first. */
    suspend fun sessions(): List<AccountSession> {
        val open = apiCall(json) { api.sessions() }
        val mine = tokens.sessionId()
        return open.map { it.toDomain(current = it.id == mine) }
    }

    /** Ends the session [id]; the phone's own ends on the phone as well. */
    suspend fun end(id: String) {
        val mine = tokens.sessionId()
        apiCall(json) { api.endSession(id) }
        if (id == mine) sessionEnd.end()
    }

    /** Ends every session of the account, the phone's own included. */
    suspend fun endAll() {
        apiCall(json) { api.endAllSessions() }
        sessionEnd.end()
    }
}

private fun AccountSessionDto.toDomain(current: Boolean) = AccountSession(
    id = id,
    label = label,
    startedAt = Instant.parse(startedAt),
    lastUsedAt = Instant.parse(lastUsedAt),
    endsAt = Instant.parse(endsAt),
    current = current,
)
