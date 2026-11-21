// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the settings' list and the card say of the phone's notices, from one reading of the switch and the system. */
class PhoneNoticesTest {
    @Test
    fun offIsOffWhateverTheSystemAllows() {
        assertEquals(PhoneNotices.OFF, phoneNotices(on = false, allowed = true, unrestricted = true))
        assertEquals(PhoneNotices.OFF, phoneNotices(on = false, allowed = false, unrestricted = false))
        assertEquals(PhoneNotices.OFF, phoneNotices(on = null, allowed = true, unrestricted = true))
    }

    @Test
    fun onIsBlockedWhereTheSystemSaysNoBeforeTheBatteryIsAskedAbout() {
        assertEquals(PhoneNotices.BLOCKED, phoneNotices(on = true, allowed = false, unrestricted = true))
        assertEquals(PhoneNotices.BLOCKED, phoneNotices(on = true, allowed = false, unrestricted = false))
    }

    @Test
    fun onAndAllowedIsDelayedOnlyWhereTheBatteryRestrictsTheApp() {
        assertEquals(PhoneNotices.DELAYED, phoneNotices(on = true, allowed = true, unrestricted = false))
        assertEquals(PhoneNotices.ON, phoneNotices(on = true, allowed = true, unrestricted = true))
    }
}
