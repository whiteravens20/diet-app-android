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
import net.whiteravens.dietapp.testing.pressToSignIn
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.savedStateHolds
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Assert.assertFalse
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
        screen.putKeyboardAway()
        screen.pressToSignIn()

        screen.waitForText(text(R.string.auth_second_step_title))
        screen.keepScreenshot("sign-in-second-step")
        // The code from the app is typed on a number pad.
        screen.onNodeWithText(text(R.string.account_second_factor_code)).performTextInput("000000")
        screen.putKeyboardAway()
        screen.pressToSignIn()
        screen.waitForText(text(R.string.error_code_invalid_second_factor_code))

        // A recovery code, of letters and digits, has a field of its own a press away.
        screen.onNodeWithText(text(R.string.account_second_factor_use_recovery)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.account_second_factor_recovery_code)).performTextInput(recoveryCodes.first())
        assertFalse("the recovery code is in the saved state", screen.savedStateHolds(recoveryCodes.first()))
        screen.putKeyboardAway()
        screen.pressToSignIn()
        screen.waitForText(text(R.string.dashboard_welcome))
        screen.onNodeWithText(text(R.string.dashboard_no_profiles)).assertIsDisplayed()
    }
}
