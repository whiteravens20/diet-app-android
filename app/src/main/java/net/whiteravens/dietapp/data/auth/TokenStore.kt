// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.whiteravens.dietapp.data.remote.AuthTokens
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth")

/** The persisted session: both tokens present, or no session at all. */
data class Session(val accessToken: String, val refreshToken: String)

/** A session signed out of on this device that is not revoked yet: its refresh token, and the server that issued it. */
@Serializable
data class PendingRevocation(val refreshToken: String, val server: String)

/**
 * DataStore-backed home of the token pair (see docs/sync-strategy.md — Auth).
 * The pair is written atomically on login/refresh and cleared on sign-out;
 * observers of [session] see sign-in state reactively. Every token is kept
 * sealed ([Sealer]): the file holds none of them, and a pair that cannot be
 * opened again is no session. Beside the pair is the id of the session it
 * belongs to, which is no token: it names the session, and opens nothing.
 */
@Singleton
class TokenStore internal constructor(
    private val store: DataStore<Preferences>,
    private val sealer: Sealer,
) {
    @Inject
    constructor(@ApplicationContext context: Context, sealer: Sealer) : this(context.authDataStore, sealer)

    /** The sealed pair last opened, and what it held: every request reads the session. */
    @Volatile
    private var opened: Pair<Pair<String, String>, Session?>? = null

    val session: Flow<Session?> = store.data.map(::open)

    suspend fun current(): Session? = session.first()

    /** Keeps [tokens] as the session, with the id of the session they belong to where the server named it. */
    suspend fun save(tokens: AuthTokens, sessionId: String?) {
        store.edit { prefs ->
            prefs[ACCESS_TOKEN] = sealer.seal(ACCESS_TOKEN.name, tokens.accessToken)
            prefs[REFRESH_TOKEN] = sealer.seal(REFRESH_TOKEN.name, tokens.refreshToken)
            if (sessionId != null) prefs[SESSION_ID] = sessionId else prefs.remove(SESSION_ID)
        }
    }

    /** The id of the session the phone is signed in to, as the account's list of sessions names it; null where the server did not say. */
    suspend fun sessionId(): String? = store.data.first()[SESSION_ID]

    suspend fun clear() {
        store.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
            prefs.remove(SESSION_ID)
        }
    }

    /**
     * The sessions signed out of that their servers have not revoked yet, the oldest first. One kept the way an
     * earlier version kept it, alone, comes first.
     */
    suspend fun pendingRevocations(): List<PendingRevocation> {
        val prefs = store.data.first()
        val alone = prefs[PENDING_REVOCATION]?.let { sealer.open(PENDING_REVOCATION.name, it) }?.let { token ->
            prefs[PENDING_REVOCATION_SERVER]?.let { server -> PendingRevocation(token, server) }
        }
        val listed = prefs[PENDING_REVOCATIONS]?.let { sealer.open(PENDING_REVOCATIONS.name, it) }
            ?.let { opened -> runCatching { Json.decodeFromString<List<PendingRevocation>>(opened) }.getOrNull() }
            .orEmpty()
        return listOfNotNull(alone) + listed
    }

    /** Keeps [pending] as the sessions to revoke, sealed as one, the newest [MOST_PENDING] of them. */
    suspend fun keepPendingRevocations(pending: List<PendingRevocation>) {
        store.edit { prefs ->
            prefs.remove(PENDING_REVOCATION)
            prefs.remove(PENDING_REVOCATION_SERVER)
            if (pending.isEmpty()) {
                prefs.remove(PENDING_REVOCATIONS)
            } else {
                prefs[PENDING_REVOCATIONS] = sealer.seal(PENDING_REVOCATIONS.name, Json.encodeToString(pending.takeLast(MOST_PENDING)))
            }
        }
    }

    private fun open(prefs: Preferences): Session? {
        val sealed = (prefs[ACCESS_TOKEN] ?: return null) to (prefs[REFRESH_TOKEN] ?: return null)
        opened?.let { (pair, session) -> if (pair == sealed) return session }
        val access = sealer.open(ACCESS_TOKEN.name, sealed.first)
        val refresh = sealer.open(REFRESH_TOKEN.name, sealed.second)
        val session = if (access != null && refresh != null) Session(access, refresh) else null
        opened = sealed to session
        return session
    }

    companion object {
        /** How many sessions signed out of without a server are kept to revoke: more sign-outs than that keep the newest. */
        const val MOST_PENDING = 5

        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val SESSION_ID = stringPreferencesKey("session_id")

        /** Where an earlier version kept the one session it had to revoke, and its server. */
        private val PENDING_REVOCATION = stringPreferencesKey("pending_revocation")
        private val PENDING_REVOCATION_SERVER = stringPreferencesKey("pending_revocation_server")
        private val PENDING_REVOCATIONS = stringPreferencesKey("pending_revocations")
    }
}
