// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.confirmEmailDataStore by preferencesDataStore(name = "confirm_email")

/** The account the notice of an unconfirmed address was put aside for on this phone, and until when. */
data class PutAside(val account: String, val until: Instant)

/**
 * "Not now" said to the notice of an unconfirmed address: it stays aside on this phone for
 * [PUT_ASIDE_FOR], for the account it was said for; another account signed in sees the notice.
 */
@Singleton
class ConfirmEmailStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.confirmEmailDataStore)

    val aside: Flow<PutAside?> = store.data.map { prefs ->
        val account = prefs[ACCOUNT] ?: return@map null
        val until = prefs[UNTIL] ?: return@map null
        PutAside(account, Instant.ofEpochMilli(until))
    }.distinctUntilChanged()

    suspend fun putAside(account: String, now: Instant = Instant.now()) {
        store.edit {
            it[ACCOUNT] = account
            it[UNTIL] = now.plus(PUT_ASIDE_FOR).toEpochMilli()
        }
    }

    companion object {
        /** How long "Not now" puts the notice aside. */
        val PUT_ASIDE_FOR: Duration = Duration.ofDays(30)
        private val ACCOUNT = stringPreferencesKey("account")
        private val UNTIL = longPreferencesKey("until")
    }
}
