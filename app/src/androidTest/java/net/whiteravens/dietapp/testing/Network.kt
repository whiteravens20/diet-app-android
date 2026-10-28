// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.BuildConfig
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/** Whether the instance answers a connection at all, as the app would reach it. */
fun instanceReachable(): Boolean {
    val address = URI(BuildConfig.API_BASE_URL)
    return try {
        Socket().use { it.connect(InetSocketAddress(address.host, address.port), 1_000) }
        true
    } catch (e: IOException) {
        false
    }
}

/**
 * Turns airplane mode on or off, and waits until the instance is out of reach or back. The test's
 * own requests to the instance go through the same network: none is made while it is on.
 */
fun ComposeTestRule.airplaneMode(on: Boolean) {
    val command = "cmd connectivity airplane-mode ${if (on) "enable" else "disable"}"
    ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
        .use { it.readBytes() }
    waitUntil(30_000) { instanceReachable() != on }
}
