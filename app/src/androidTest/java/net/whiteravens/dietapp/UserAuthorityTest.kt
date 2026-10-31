// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.shownAddress
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A server whose certificate comes from an authority the user installed, as a self-hosted one
 * often has, is trusted, on Android 17 too, where Certificate Transparency is checked by default.
 * CI serves the test instance so, with the authority installed on the emulator, and names the
 * address in the argument `userAuthorityServer`; without it there is nothing to test against.
 */
@RunWith(AndroidJUnit4::class)
class UserAuthorityTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun aServerWithACertificateFromAnAuthorityTheUserInstalledIsTrusted() {
        val address = InstrumentationRegistry.getArguments().getString("userAuthorityServer")
        assumeTrue("no server with a certificate from a test authority", address != null)
        val account = Instance.newAccount()

        val button = text(R.string.server_label, shownAddress)
        screen.waitForText(button)
        screen.onNodeWithText(button).performClick()
        screen.onNode(hasSetTextAction() and hasText(shownAddress)).performTextReplacement(address!!)
        screen.onNodeWithText(text(R.string.common_save)).performClick()

        // Kept without a word: the app reached the server and trusted its certificate.
        screen.waitForText(text(R.string.server_label, address.removePrefix("https://").removeSuffix("/api/").removeSuffix("/")))
        screen.keepScreenshot("sign-in-server-of-a-user-authority")
        screen.signIn(account)
    }
}
