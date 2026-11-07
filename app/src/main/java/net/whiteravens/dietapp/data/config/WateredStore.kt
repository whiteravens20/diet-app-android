// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import net.whiteravens.dietapp.domain.Profile
import javax.inject.Inject
import javax.inject.Singleton

private val Context.wateredDataStore by preferencesDataStore(name = "watered")

/**
 * The profile water was added for last on this phone, by the water card, a reminder or a shortcut:
 * the one the shortcuts of the application's icon add water to. Forgotten with the session.
 */
@Singleton
class WateredStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.wateredDataStore)

    suspend fun watered(profileId: String) {
        store.edit { it[PROFILE] = profileId }
    }

    /** Of [profiles], the one watered last, or the first where none of them was. */
    suspend fun profileOf(profiles: List<Profile>): Profile? {
        val last = store.data.first()[PROFILE]
        return profiles.firstOrNull { it.id == last } ?: profiles.firstOrNull()
    }

    suspend fun forget() {
        store.edit { it.remove(PROFILE) }
    }

    private companion object {
        val PROFILE = stringPreferencesKey("profile")
    }
}
