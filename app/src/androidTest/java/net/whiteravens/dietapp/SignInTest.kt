// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun anAccountSignsInAndIsWelcomedToADashboardWithoutProfiles() {
        val account = Instance.newAccount()

        screen.onNodeWithText(text(R.string.auth_email)).performTextInput(account.email)
        screen.onNodeWithText(text(R.string.auth_password)).performTextInput(account.password)
        screen.onNodeWithText(text(R.string.auth_sign_in_button)).performScrollTo().performClick()

        screen.waitForText(text(R.string.dashboard_welcome))
        screen.onNodeWithText(text(R.string.dashboard_no_profiles)).assertIsDisplayed()
        screen.keepScreenshot("signed-in-dashboard-without-profiles")
    }

    @Test
    fun aForgottenPasswordIsAskedForByMailWhereTheInstanceSendsMail() {
        val account = Instance.newAccount()

        screen.onNodeWithText(text(R.string.auth_forgot_password)).performScrollTo().performClick()
        if (Instance.mailsSent()) {
            screen.onNodeWithText(text(R.string.auth_email)).performTextInput(account.email)
            screen.onNodeWithText(text(R.string.auth_reset_submit)).performScrollTo().performClick()
            screen.waitForText(text(R.string.auth_reset_sent))
        } else {
            // The link would be mailed: an instance that sends no mail says that it has none.
            screen.waitForText(text(R.string.auth_reset_unavailable))
        }
        screen.keepScreenshot("sign-in-forgotten-password")

        screen.onNodeWithText(text(R.string.auth_back_to_sign_in)).performScrollTo().performClick()
        screen.waitForText(text(R.string.auth_welcome_back))
    }
}
