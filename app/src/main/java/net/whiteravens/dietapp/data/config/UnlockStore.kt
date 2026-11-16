// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.unlockDataStore by preferencesDataStore(name = "unlock")

/**
 * Whether the application asks for a fingerprint, or the phone's screen lock, before it shows its screens: turned on
 * and off in the account's settings, for the account signed in on this phone. The end of the session forgets it.
 */
@Singleton
class UnlockStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.unlockDataStore)

    val on: Flow<Boolean> = store.data.map { it[ON] == true }.distinctUntilChanged()

    suspend fun turnOn() {
        store.edit { it[ON] = true }
    }

    suspend fun forget() {
        store.edit { it.remove(ON) }
    }

    private companion object {
        val ON = booleanPreferencesKey("on")
    }
}
