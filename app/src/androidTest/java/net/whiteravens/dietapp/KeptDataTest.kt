// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** What the installed app lets the system take from the phone. */
@RunWith(AndroidJUnit4::class)
class KeptDataTest {
    @Test
    fun theInstalledAppAllowsNoBackup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo

        assertEquals(0, app.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
