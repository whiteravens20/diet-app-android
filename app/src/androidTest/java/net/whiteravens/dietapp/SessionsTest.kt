// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSettings
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionsTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun theSessionsAreListedWithThisPhoneNamedAndTheOtherThenEveryOneIsSignedOut() {
        // The sign-up opened a session of its own, as another app would have.
        val account = Instance.newAccount()
        val label = checkNotNull(Instance.sessions(account.accessToken)[account.sessionId])
        screen.signIn(account)
        screen.openAccountSettings(account)

        screen.waitForText(text(R.string.account_session_current))
        screen.onNodeWithText(text(R.string.account_session_current)).performScrollTo().assertIsDisplayed()
        screen.keepScreenshot("account-sessions")

        // The other session signed out from its row: asked again, the account no longer has it.
        screen.onNodeWithContentDescription(text(R.string.account_session_end_named, label)).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { !Instance.stillSignedIn(account.accessToken) }
        val fresh = Instance.signIn(account)
        val open = Instance.sessions(fresh)
        assertFalse(account.sessionId in open)
        assertEquals(2, open.size)

        // Every session, the phone's too, and the phone is back at the sign-in.
        screen.waitForEnabled(text(R.string.account_session_end_all))
        screen.onNodeWithText(text(R.string.account_session_end_all)).performScrollTo().performClick()
        screen.waitForText(text(R.string.auth_welcome_back))
        screen.waitForTheApi(20_000) { !Instance.stillSignedIn(fresh) }
    }

    @Test
    fun aChangedPasswordEndsEverySessionAndTheNewOneSignsIn() {
        val account = Instance.newAccount()
        val renewed = "${account.password}-Renewed"
        screen.signIn(account)
        screen.openAccountSettings(account)

        screen.onNodeWithText(text(R.string.account_password_current)).performScrollTo().performTextInput(account.password)
        screen.onNodeWithText(text(R.string.account_password_new)).performScrollTo().performTextInput(renewed)
        screen.onNodeWithText(text(R.string.account_password_change)).performScrollTo().performClick()
        screen.waitForText(text(R.string.account_password_changed))
        screen.keepScreenshot("account-password-changed")

        // Every session ended with the old password, the phone's and the sign-up's; the new one signs in.
        screen.waitForText(text(R.string.auth_welcome_back))
        assertFalse(Instance.stillSignedIn(account.accessToken))
        screen.signIn(account.copy(password = renewed))
    }
}
