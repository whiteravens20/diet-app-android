package net.whiteravens.dietapp.data.auth

import android.content.Context
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

/**
 * DataStore-backed home of the token pair (see docs/sync-strategy.md — Auth).
 * The pair is written atomically on login/refresh and cleared on sign-out;
 * observers of [session] see sign-in state reactively.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {

    private val store = context.authDataStore

    val session: Flow<Session?> = store.data.map { prefs ->
        val access = prefs[ACCESS_TOKEN] ?: return@map null
        val refresh = prefs[REFRESH_TOKEN] ?: return@map null
        Session(access, refresh)
    }

    suspend fun current(): Session? = session.first()

    suspend fun save(tokens: AuthTokens) {
        store.edit { prefs ->
            prefs[ACCESS_TOKEN] = tokens.accessToken
            prefs[REFRESH_TOKEN] = tokens.refreshToken
        }
    }

    suspend fun clear() {
        store.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
        }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}
