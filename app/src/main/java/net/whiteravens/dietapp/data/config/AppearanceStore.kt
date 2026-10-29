// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.whiteravens.dietapp.domain.Palette
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

/**
 * The colour scheme the application is drawn in. One chosen on this phone stays with the phone,
 * whoever signs in; until one is, the application follows the account's, which the web sets and
 * every answer about the account says.
 */
@Singleton
class AppearanceStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.appearanceDataStore)

    /** The scheme chosen on this phone, or null where the account's is followed. */
    val chosen: Flow<Palette?> = store.data.map { Palette.of(it[CHOSEN]) }.distinctUntilChanged()

    /** The account's scheme as an answer last said it, or null before one did. One this version does not know is drawn as the default. */
    val account: Flow<Palette?> = store.data.map { accountOf(it) }.distinctUntilChanged()

    /** The scheme the application is drawn in: the one chosen here, else the account's, else the default. */
    val palette: Flow<Palette> = store.data.map { Palette.of(it[CHOSEN]) ?: accountOf(it) ?: Palette.DEFAULT }.distinctUntilChanged()

    /** Chooses [palette] on this phone; null follows the account's again. */
    suspend fun choose(palette: Palette?) {
        store.edit { if (palette == null) it.remove(CHOSEN) else it[CHOSEN] = palette.wire }
    }

    /** What an answer about the account says its scheme is, by the API's name for it. */
    suspend fun heard(accountPalette: String) {
        store.edit { if (it[ACCOUNT] != accountPalette) it[ACCOUNT] = accountPalette }
    }

    /** At signing out: the account's scheme goes with the account, and the one chosen here stays. */
    suspend fun forgetAccount() {
        store.edit { it.remove(ACCOUNT) }
    }

    private fun accountOf(prefs: Preferences): Palette? = prefs[ACCOUNT]?.let { Palette.of(it) ?: Palette.DEFAULT }

    private companion object {
        val CHOSEN = stringPreferencesKey("palette")
        val ACCOUNT = stringPreferencesKey("account_palette")
    }
}
