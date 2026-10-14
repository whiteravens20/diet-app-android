// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecondStepTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun anAccountWithTwoStepSignInIsAskedForACodeAndARecoveryCodeLetsItIn() {
        val account = Instance.newAccount()
        val recoveryCodes = Instance.switchSecondStepOn(account)

        screen.onNodeWithText(text(R.string.auth_email)).performTextInput(account.email)
        screen.onNodeWithText(text(R.string.auth_password)).performTextInput(account.password)
        screen.onNodeWithText(text(R.string.auth_sign_in_button)).performClick()

        screen.waitForText(text(R.string.auth_second_step_title))
        screen.keepScreenshot("sign-in-second-step")
        screen.onNodeWithText(text(R.string.auth_code)).performTextInput("000000")
        screen.onNodeWithText(text(R.string.auth_sign_in_button)).performClick()
        screen.waitForText(text(R.string.error_invalid_second_factor_code))

        screen.onNodeWithText(text(R.string.auth_code)).performTextReplacement(recoveryCodes.first())
        screen.onNodeWithText(text(R.string.auth_sign_in_button)).performClick()
        screen.waitForText(text(R.string.dashboard_welcome))
        screen.onNodeWithText(text(R.string.dashboard_no_profiles)).assertIsDisplayed()
    }
}
