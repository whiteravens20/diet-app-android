// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.notify.PhoneNotificationsSwitch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ends the session on this device, whoever ends it: the phone's notifications of the account and
 * the account's cached plans and lists go first, then the tokens, whose going is what the screens
 * take as signed out.
 */
@Singleton
class SessionEnd @Inject constructor(
    private val tokens: TokenStore,
    private val cache: CacheDatabase,
    private val phoneNotifications: PhoneNotificationsSwitch,
) {
    suspend fun end() {
        phoneNotifications.forget()
        withContext(Dispatchers.IO) { cache.clearAllTables() }
        tokens.clear()
    }
}
