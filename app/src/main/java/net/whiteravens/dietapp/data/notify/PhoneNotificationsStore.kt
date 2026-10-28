// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.notify

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.phoneNotificationsDataStore by preferencesDataStore(name = "phone_notifications")

/**
 * Whether the account's notifications are shown on the phone, which the user turns on in the
 * account's settings, and which of them the phone has shown already, so that each is shown once.
 */
@Singleton
class PhoneNotificationsStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.phoneNotificationsDataStore)

    val on: Flow<Boolean> = store.data.map { it[ON] ?: false }

    suspend fun isOn(): Boolean = on.first()

    suspend fun turn(on: Boolean) {
        store.edit { it[ON] = on }
    }

    /** The notifications shown already, each by the key [NewNotifications.keyOf] gives it. */
    suspend fun shown(): Set<String> = store.data.first()[SHOWN].orEmpty()

    suspend fun keepShown(keys: Set<String>) {
        store.edit { it[SHOWN] = keys }
    }

    /** Forgets everything: the switch off and nothing shown, as after signing out. */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val ON = booleanPreferencesKey("on")
        val SHOWN = stringSetPreferencesKey("shown")
    }
}
