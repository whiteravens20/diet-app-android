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

private val Context.waterNoteDataStore by preferencesDataStore(name = "water_note")

/**
 * Whether the note on the water card, that the card can be turned off in the profile, was closed on
 * this phone: once it is, no card of any profile shows it again here.
 */
@Singleton
class WaterNoteStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.waterNoteDataStore)

    val closed: Flow<Boolean> = store.data.map { it[CLOSED] == true }.distinctUntilChanged()

    suspend fun close() {
        store.edit { it[CLOSED] = true }
    }

    private companion object {
        val CLOSED = booleanPreferencesKey("closed")
    }
}
