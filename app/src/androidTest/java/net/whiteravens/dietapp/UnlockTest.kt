// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.data.auth.StandInPhone
import net.whiteravens.dietapp.data.auth.UnlockStandIn
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSettings
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheShell
import net.whiteravens.dietapp.ui.unlock.UnlockGate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The unlock of the screens, with a stand-in for the phone: an emulator has no finger, and the system's prompt lies
 * outside Compose's reach. What the phone itself does with the key and the prompt is not seen here.
 */
@RunWith(AndroidJUnit4::class)
class UnlockTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private val phone = StandInPhone()

    @Before
    fun standIn() {
        UnlockStandIn.phone = phone
    }

    @After
    fun thePhoneItself() {
        UnlockStandIn.phone = null
    }

    private fun switch() = screen.onNode(hasText(text(R.string.unlock_switch)) and isToggleable())

    private fun turnedOn(on: Boolean) = screen.waitUntil(10_000) {
        runCatching { if (on) switch().assertIsOn() else switch().assertIsOff() }.isSuccess
    }

    /** The application in the background for half an hour and back, as the gate's clock sees it. */
    private fun awayForHalfAnHour() {
        screen.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        phone.shift += UnlockGate.AWAY_MILLIS
        screen.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test
    fun theUnlockIsTurnedOnInTheSettingsLocksAfterHalfAnHourAwayOpensAtAPromptThatPassesAndIsTurnedOff() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)
        switch().performScrollTo().assertIsOff()
        screen.keepScreenshot("unlock-setting")

        // Turned on after a prompt that passed.
        switch().performScrollTo().performClick()
        turnedOn(true)

        // Back after half an hour: locked, its prompt asked by itself and cancelled, nothing of the account behind it.
        phone.passes = false
        awayForHalfAnHour()
        screen.waitForText(text(R.string.unlock_locked))
        screen.onNodeWithText(account.email).assertDoesNotExist()
        screen.keepScreenshot("unlock-locked")

        // "Odblokuj" and a prompt that passes: the screens again, from their first.
        phone.passes = true
        screen.onNodeWithText(text(R.string.unlock_button)).performClick()
        screen.waitForTheShell()

        // Turned off with one more prompt: back after half an hour, nothing is asked.
        screen.openAccountSettings(account)
        switch().performScrollTo().performClick()
        turnedOn(false)
        awayForHalfAnHour()
        screen.waitForText(account.email)
        screen.onNodeWithText(text(R.string.unlock_locked)).assertDoesNotExist()
    }

    @Test
    fun aCancelledPromptLeavesItAsItWasOnAndOff() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)

        phone.passes = false
        switch().performScrollTo().performClick()
        screen.waitForText(text(R.string.unlock_turn_on_failed))
        switch().assertIsOff()

        phone.passes = true
        switch().performScrollTo().performClick()
        turnedOn(true)
        phone.passes = false
        switch().performScrollTo().performClick()
        // The prompt ended without turning it off: it stays on.
        screen.waitForIdle()
        switch().assertIsOn()
    }

    @Test
    fun aChangedFingerprintEndsTheSessionOnThePhoneSaysWhyAndTheSignInFollows() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)
        switch().performScrollTo().performClick()
        turnedOn(true)

        phone.voidTheKey()
        awayForHalfAnHour()
        screen.waitForText(text(R.string.unlock_voided))
        screen.keepScreenshot("unlock-voided")
        screen.onNodeWithText(text(R.string.auth_sign_in_button)).performClick()
        screen.waitForText(text(R.string.auth_slogan))
    }
}
