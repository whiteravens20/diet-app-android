// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updatesDataStore by preferencesDataStore(name = "updates")

/** When the application last asked for its newest version, and whether that got an answer. */
data class Asked(val at: Instant, val answered: Boolean)

/**
 * What the application knows of its newest version, on this phone whoever signs in: whether it asks
 * at all (on until turned off), when it last asked, the version file it was last answered with, and
 * the version the user put off.
 */
@Singleton
class UpdateStore internal constructor(private val store: DataStore<Preferences>, private val json: Json) {
    @Inject
    constructor(@ApplicationContext context: Context, json: Json) : this(context.updatesDataStore, json)

    val on: Flow<Boolean> = store.data.map { it[ON] ?: true }

    val asked: Flow<Asked?> = store.data.map { prefs -> prefs[ASKED_AT]?.let { Asked(Instant.ofEpochMilli(it), prefs[ANSWERED] ?: false) } }

    /** The version file of the last answer, or null before one came. */
    val latest: Flow<VersionFile?> = store.data.map { prefs -> prefs[LATEST]?.let { runCatching { json.decodeFromString<VersionFile>(it) }.getOrNull() } }

    /** The version code the user put off with "Later", or null. */
    val putOff: Flow<Int?> = store.data.map { it[PUT_OFF] }

    suspend fun turn(on: Boolean) {
        store.edit { it[ON] = on }
    }

    suspend fun isOn(): Boolean = on.first()

    suspend fun askedAt(): Instant? = asked.first()?.at

    /** Keeps the time of an ask, and the version file it was answered with, or that it got no answer. */
    suspend fun keep(at: Instant, answer: VersionFile?) {
        store.edit { prefs ->
            prefs[ASKED_AT] = at.toEpochMilli()
            prefs[ANSWERED] = answer != null
            if (answer != null) prefs[LATEST] = json.encodeToString(VersionFile.serializer(), answer)
        }
    }

    suspend fun putOff(versionCode: Int) {
        store.edit { it[PUT_OFF] = versionCode }
    }

    private companion object {
        val ON = booleanPreferencesKey("on")
        val ASKED_AT = longPreferencesKey("asked_at")
        val ANSWERED = booleanPreferencesKey("answered")
        val LATEST = stringPreferencesKey("latest")
        val PUT_OFF = intPreferencesKey("put_off")
    }
}
