// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One use of the session's refresh token at a time. The backend takes a refresh token once: a
 * refresh ([TokenAuthenticator]) and a code for the browser ([BrowserCodes]) both use it up, and
 * whichever comes second has to show the token the first one got.
 */
@Singleton
class RefreshTurns @Inject constructor() {
    val lock = ReentrantLock()
}
