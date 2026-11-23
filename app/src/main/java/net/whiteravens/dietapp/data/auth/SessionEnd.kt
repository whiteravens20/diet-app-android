// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.whiteravens.dietapp.data.config.AppearanceStore
import net.whiteravens.dietapp.data.config.ConfirmEmailStore
import net.whiteravens.dietapp.data.config.UnlockStore
import net.whiteravens.dietapp.data.config.WaterNoteStore
import net.whiteravens.dietapp.data.config.WateredStore
import net.whiteravens.dietapp.data.local.CacheDatabase
import net.whiteravens.dietapp.data.notify.PhoneNotificationsSwitch
import net.whiteravens.dietapp.notify.WaterShortcuts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ends the session on this device, whoever ends it: the phone's notifications of the account, the
 * account's colour scheme, the icon's shortcuts with the profile they add water to, the notice of an
 * unconfirmed address put aside for it, the water card's note closed, and its cached plans and lists
 * go first, then the tokens, whose going is what the screens take as signed out, and the cached copies
 * once more after them: a request under way at the sign-out that answers after the first clearing
 * writes no copy of the account's for the next one on the phone to see. The unlock goes last,
 * its setting and its key, so that the gate in front of the screens opens only onto the sign-in. A
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
    private val confirmEmail: ConfirmEmailStore,
    private val waterNote: WaterNoteStore,
    private val unlock: UnlockStore,
    private val unlockDevice: UnlockDevice,
) {
    suspend fun end() {
        phoneNotifications.forget()
        appearance.forgetAccount()
        shortcuts.remove()
        watered.forget()
        confirmEmail.forget()
        waterNote.forget()
        withContext(Dispatchers.IO) { cache.clearAllTables() }
        tokens.clear()
        withContext(Dispatchers.IO) { cache.clearAllTables() }
        unlock.forget()
        unlockDevice.deleteKey()
    }
}
