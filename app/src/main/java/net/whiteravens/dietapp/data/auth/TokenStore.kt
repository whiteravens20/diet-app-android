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
import net.whiteravens.dietapp.data.remote.AuthTokens
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth")

/** The persisted session: both tokens present, or no session at all. */
data class Session(val accessToken: String, val refreshToken: String)

/** A session signed out of on this device that is not revoked yet: its refresh token, and the server that issued it. */
data class PendingRevocation(val refreshToken: String, val server: String)

/**
 * DataStore-backed home of the token pair (see docs/sync-strategy.md — Auth).
 * The pair is written atomically on login/refresh and cleared on sign-out;
 * observers of [session] see sign-in state reactively. Every token is kept
 * sealed ([Sealer]): the file holds none of them, and a pair that cannot be
 * opened again is no session.
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

    suspend fun save(tokens: AuthTokens) {
        store.edit { prefs ->
            prefs[ACCESS_TOKEN] = sealer.seal(ACCESS_TOKEN.name, tokens.accessToken)
            prefs[REFRESH_TOKEN] = sealer.seal(REFRESH_TOKEN.name, tokens.refreshToken)
        }
    }

    suspend fun clear() {
        store.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
        }
    }

    /** The session signed out of that its server has not revoked yet, or null. */
    suspend fun pendingRevocation(): PendingRevocation? {
        val prefs = store.data.first()
        val refreshToken = prefs[PENDING_REVOCATION]?.let { sealer.open(PENDING_REVOCATION.name, it) } ?: return null
        val server = prefs[PENDING_REVOCATION_SERVER] ?: return null
        return PendingRevocation(refreshToken, server)
    }

    suspend fun setPendingRevocation(pending: PendingRevocation?) {
        store.edit { prefs ->
            if (pending == null) {
                prefs.remove(PENDING_REVOCATION)
                prefs.remove(PENDING_REVOCATION_SERVER)
            } else {
                prefs[PENDING_REVOCATION] = sealer.seal(PENDING_REVOCATION.name, pending.refreshToken)
                prefs[PENDING_REVOCATION_SERVER] = pending.server
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

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val PENDING_REVOCATION = stringPreferencesKey("pending_revocation")
        val PENDING_REVOCATION_SERVER = stringPreferencesKey("pending_revocation_server")
    }
}
