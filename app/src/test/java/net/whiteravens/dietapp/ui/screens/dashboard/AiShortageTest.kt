// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import net.whiteravens.dietapp.domain.AdminAccess
import net.whiteravens.dietapp.domain.AiModes
import net.whiteravens.dietapp.domain.AiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Why the dashboard says no AI request of the instance is left, in the order the web's chip says it. */
class AiShortageTest {
    private val frees = Instant.parse("2026-11-20T10:00:00Z")

    /** An account on the instance's AI with [left] of fifteen requests left. */
    private fun instance(left: Int) = AiStatus(
        mode = AiModes.INSTANCE, limit = 15, used = 15 - left, remaining = left, resetAt = frees,
        instanceLimitReached = false, adminProviderConfigured = true,
    )

    @Test
    fun `nothing is short while requests are left, or where the account asks its own provider`() {
        assertNull(shortageOf(instance(12)))
        assertNull(shortageOf(instance(0).copy(mode = AiModes.OWN, remaining = null)))
    }

    @Test
    fun `none left is said by why, the account's access first and its own allowance last`() {
        assertEquals(AiShortage.USED_UP, shortageOf(instance(0)))
        assertEquals(
            AiShortage.NEW_ACCOUNTS_USED_UP,
            shortageOf(instance(0).copy(newUntil = frees, newAccountsLimitReached = true)),
        )
        // All accounts together used the instance's, whatever the account has left of its own.
        assertEquals(AiShortage.INSTANCE_USED_UP, shortageOf(instance(0).copy(instanceLimitReached = true, newUntil = frees, newAccountsLimitReached = true)))
        // An instance that shares no request, as one with no provider or none to give.
        assertEquals(AiShortage.NOT_SHARED, shortageOf(instance(0).copy(limit = 0)))
        assertEquals(AiShortage.NOT_SHARED, shortageOf(instance(12).copy(adminProviderConfigured = false)))
        assertEquals(AiShortage.NO_ACCESS, shortageOf(instance(12).copy(adminAccess = AdminAccess.NOT_APPROVED)))
    }
}
