// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.pressToSignIn
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun theFirstScreenAsksToSignInAndSaysThatTheAppIsAtAnEarlyStage() {
        screen.waitForText(text(R.string.auth_sign_in_title))
        screen.onNodeWithText(text(R.string.auth_slogan)).assertIsDisplayed()
        screen.onNodeWithText(text(R.string.early_stage_body)).assertDoesNotExist()
        screen.keepScreenshot("sign-in-first-screen")

        screen.onNodeWithText(text(R.string.early_stage_note)).performScrollTo().performClick()
        screen.waitForText(text(R.string.early_stage_body))
        screen.onNodeWithText(text(R.string.early_stage_title)).assertIsDisplayed()
        screen.keepScreenshot("sign-in-what-an-early-stage-means")

        screen.onNodeWithText(text(R.string.common_close)).performClick()
        screen.onNodeWithText(text(R.string.early_stage_body)).assertDoesNotExist()
    }

    @Test
    fun anAccountSignsInAndIsWelcomedToADashboardWithoutProfiles() {
        val account = Instance.newAccount()

        screen.onNodeWithText(text(R.string.auth_email)).performTextInput(account.email)
        screen.onNodeWithText(text(R.string.auth_password)).performTextInput(account.password)
        screen.putKeyboardAway()
        screen.pressToSignIn()

        screen.waitForText(text(R.string.dashboard_welcome))
        screen.onNodeWithText(text(R.string.dashboard_no_profiles)).assertIsDisplayed()
        screen.keepScreenshot("signed-in-dashboard-without-profiles")
    }

    @Test
    fun aSignUpAsksForThePasswordTwiceAndMakesNoAccountWhereTheTwoDiffer() {
        screen.onNodeWithText(text(R.string.auth_no_account)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.auth_name)).performTextInput("Twice")
        screen.onNodeWithText(text(R.string.auth_email)).performTextInput("twice-${System.currentTimeMillis()}@example.test")
        screen.onNodeWithText(text(R.string.auth_password)).performTextInput("Twice-Test-Passw0rd")
        screen.onNodeWithText(text(R.string.auth_repeat_password)).performTextInput("Twice-Test-Passw0rd!")
        screen.putKeyboardAway()
        screen.onNode(hasText(text(R.string.auth_create_account_button)) and hasClickAction()).performScrollTo().performClick()

        // Nothing is sent: the form says why at the second field, and stays.
        screen.waitForText(text(R.string.auth_passwords_dont_match))
        screen.onNode(hasText(text(R.string.auth_create_account_button)) and hasClickAction()).assertExists()
        screen.onNodeWithText(text(R.string.auth_repeat_password)).performScrollTo()
        screen.keepScreenshot("sign-up-passwords-differ")
    }

    @Test
    fun aPasswordIsShownByItsEyeUntilItIsSentOrTheAppIsLeft() {
        val account = Instance.newAccount()
        val wrong = "Wrong-Test-Passw0rd"
        val field = screen.onNode(hasText(text(R.string.auth_password)) and hasSetTextAction())
        val show = { screen.onNodeWithContentDescription(text(R.string.common_show_password)) }
        val hide = { screen.onNodeWithContentDescription(text(R.string.common_hide_password)) }
        screen.onNodeWithText(text(R.string.auth_email)).performTextInput(account.email)
        field.performTextInput(wrong)

        // Dots with the mark of a password until the eye is pressed; the press leaves the keyboard on the field, and a
        // screen reader is told what it did. The eye is drawn 40 dp wide, as Material's icon buttons are, and a
        // finger's 48 dp around it press it.
        assertHidden(field, wrong)
        val target = show().fetchSemanticsNode().touchBoundsInRoot
        with(screen.density) {
            assertTrue("the eye is a smaller target than a finger", target.width.toDp() >= 48.dp && target.height.toDp() >= 48.dp)
        }
        show().performClick()
        field.assertIsFocused()
        assertShown(field, wrong)
        hide().assert(hasStateDescription(text(R.string.common_password_shown)))
        screen.keepScreenshot("sign-in-password-shown")
        hide().performClick()
        assertHidden(field, wrong)
        show().assert(hasStateDescription(text(R.string.common_password_hidden)))

        // Shown when the app goes to the background, it is dots when the app comes back.
        show().performClick()
        screen.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        screen.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertHidden(field, wrong)

        // Shown when the form is sent, it is dots from the sending on, and the screen keeps what was typed.
        show().performClick()
        screen.putKeyboardAway()
        screen.pressToSignIn()
        screen.waitForText(text(R.string.error_code_invalid_credentials))
        assertHidden(field, wrong)
        show().performClick()
        assertShown(field, wrong)
    }

    @Test
    fun aForgottenPasswordIsAskedForByMailWhereTheInstanceSendsMail() {
        val account = Instance.newAccount()

        screen.onNodeWithText(text(R.string.auth_forgot_password)).performScrollTo().performClick()
        if (Instance.mailsSent()) {
            screen.onNodeWithText(text(R.string.auth_email)).performTextInput(account.email)
            screen.putKeyboardAway()
            screen.onNodeWithText(text(R.string.auth_reset_submit)).performScrollTo().performClick()
            screen.waitForText(text(R.string.auth_reset_sent))
        } else {
            // The link would be mailed: an instance that sends no mail says that it has none.
            screen.waitForText(text(R.string.auth_reset_unavailable))
        }
        screen.keepScreenshot("sign-in-forgotten-password")

        screen.onNodeWithText(text(R.string.auth_back_to_sign_in)).performScrollTo().performClick()
        screen.waitForText(text(R.string.auth_sign_in_title))
    }

    private fun assertHidden(field: SemanticsNodeInteraction, typed: String) {
        val config = field.fetchSemanticsNode().config
        assertTrue("the password's field has no mark of one", SemanticsProperties.Password in config)
        assertFalse("the password is shown", config[SemanticsProperties.EditableText].text.contains(typed))
    }

    private fun assertShown(field: SemanticsNodeInteraction, typed: String) {
        val config = field.fetchSemanticsNode().config
        assertFalse("the shown password keeps the mark of one", SemanticsProperties.Password in config)
        assertEquals(typed, config[SemanticsProperties.EditableText].text)
    }
}
