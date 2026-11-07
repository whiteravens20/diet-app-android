// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.whiteravens.dietapp.data.config.AppearanceStore
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.notify.PhoneNotificationsSwitch
import net.whiteravens.dietapp.data.config.WateredStore
import net.whiteravens.dietapp.notify.WaterShortcuts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ends the session on this device, whoever ends it: the phone's notifications of the account, the
 * account's colour scheme, the icon's shortcuts with the profile they add water to, and its cached
 * plans and lists go first, then the tokens, whose going is what the screens take as signed out. A
 * colour scheme chosen on the phone stays.
 */
@Singleton
class SessionEnd @Inject constructor(
    private val tokens: TokenStore,
    private val cache: CacheDatabase,
    private val phoneNotifications: PhoneNotificationsSwitch,
    private val appearance: AppearanceStore,
    private val watered: WateredStore,
    private val shortcuts: WaterShortcuts,
) {
    suspend fun end() {
        phoneNotifications.forget()
        appearance.forgetAccount()
        shortcuts.remove()
        watered.forget()
        withContext(Dispatchers.IO) { cache.clearAllTables() }
        tokens.clear()
    }
}
