// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSettings
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.totp
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecondFactorTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** Whatever the screen shows, one text a node: [pattern] matched against each of them. */
    private fun shown(pattern: Regex): List<String> =
        screen.onAllNodes(SemanticsMatcher("shows a text") { SemanticsProperties.Text in it.config })
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            .filter(pattern::matches)

    /** The second of two fields called [label]: the password card's comes first. */
    private fun typeIntoTheSecond(label: String, typed: String) {
        screen.onAllNodesWithText(label)[1].performScrollTo().performTextInput(typed)
    }

    @Test
    fun twoStepSignInIsSetUpWithTheKeyShownGivenNewRecoveryCodesAndSwitchedOff() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)

        screen.onNodeWithText(text(R.string.account_second_factor_set_up)).performScrollTo().performClick()
        typeIntoTheSecond(text(R.string.account_password_current), account.password)
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.account_second_factor_continue)).performScrollTo().performClick()

        // The key, as the screen shows it to be typed into an authenticator app, gives the first code.
        screen.waitForText(text(R.string.account_second_factor_type_instead))
        val key = shown(Regex("([A-Z2-7]{4} )+[A-Z2-7]{1,4}")).single().replace(" ", "")
        // The same secret drawn as a QR code, for an app on another device.
        screen.onNodeWithContentDescription(text(R.string.account_second_factor_qr)).performScrollTo().assertIsDisplayed()
        screen.keepScreenshot("second-factor-set-up")
        screen.onNodeWithText(text(R.string.account_second_factor_code)).performScrollTo().performTextInput(totp(key))
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.account_second_factor_switch_on)).performScrollTo().performClick()

        screen.waitForText(text(R.string.account_second_factor_on_now))
        val recovery = shown(RECOVERY_CODE)
        assertEquals(10, recovery.size)
        screen.keepScreenshot("second-factor-recovery-codes")
        screen.onNodeWithText(text(R.string.account_second_factor_saved)).performScrollTo().performClick()
        // The phone's session is the one that switched it on, and stays open.
        screen.waitForTheApi(20_000) { Instance.secondFactorOn(account) }

        // New recovery codes, for the password and one of the codes there were.
        screen.onNodeWithText(text(R.string.account_second_factor_new_codes)).performScrollTo().performClick()
        typeIntoTheSecond(text(R.string.account_password_current), account.password)
        screen.onNodeWithText(text(R.string.account_second_factor_code_or_recovery)).performScrollTo().performTextInput(recovery.first())
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.account_second_factor_new_codes)).performScrollTo().performClick()
        screen.waitForText(text(R.string.account_second_factor_recovery_title))
        val renewed = shown(RECOVERY_CODE)
        assertEquals(10, renewed.size)
        assertEquals(emptySet<String>(), renewed.toSet() intersect recovery.toSet())
        screen.onNodeWithText(text(R.string.account_second_factor_saved)).performScrollTo().performClick()

        // Switched off with one of the new codes.
        screen.waitForEnabled(text(R.string.account_second_factor_switch_off))
        screen.onNodeWithText(text(R.string.account_second_factor_switch_off)).performScrollTo().performClick()
        typeIntoTheSecond(text(R.string.account_password_current), account.password)
        screen.onNodeWithText(text(R.string.account_second_factor_code_or_recovery)).performScrollTo().performTextInput(renewed.first())
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.account_second_factor_switch_off)).performScrollTo().performClick()
        screen.waitForText(text(R.string.account_second_factor_off))
        screen.waitForTheApi(20_000) { !Instance.secondFactorOn(account) }
    }

    private companion object {
        /** A recovery code as the API writes one: two groups of five, a dash between them. */
        val RECOVERY_CODE = Regex("[0-9A-Z]{5}-[0-9A-Z]{5}")
    }
}
