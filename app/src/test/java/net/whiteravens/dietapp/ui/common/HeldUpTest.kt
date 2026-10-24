// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import net.whiteravens.dietapp.data.remote.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When a refusal is said as a wait, as the web application says it. */
class HeldUpTest {
    @Test
    fun `a request held up is said as the wait the server named`() {
        assertEquals(240, heldUpSeconds(ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 240)))
        assertNull(heldUpSeconds(ApiException(429, "RATE_LIMITED", "Too many requests.")))
        assertNull(heldUpSeconds(ApiException(409, "CONFLICT", "Taken.", retryAfterSeconds = 30)))
    }

    @Test
    fun `a refusal that says what to do is said in its own words rather than as minutes`() {
        // A day of sign-ups from one address, and a mail with a link that went out a moment ago.
        assertNull(heldUpSeconds(ApiException(429, "TOO_MANY_SIGN_UPS", "This address has opened 3 accounts in the last day.", retryAfterSeconds = 86_220)))
        assertNull(heldUpSeconds(ApiException(429, "VERIFICATION_SENT_RECENTLY", "A verification mail was sent a moment ago.", retryAfterSeconds = 75)))
    }
}
