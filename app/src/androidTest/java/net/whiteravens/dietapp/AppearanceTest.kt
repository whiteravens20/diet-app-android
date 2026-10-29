// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSettings
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The colour scheme the application is drawn in: the account's, until another is chosen on the phone. */
@RunWith(AndroidJUnit4::class)
class AppearanceTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private fun chosen(label: String): Boolean = screen.onAllNodes(hasText(label) and isSelected()).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theAccountsSchemeIsFollowedUntilAnotherIsChosenWhichStaysWhenTheAppStartsAgain() {
        val account = Instance.newAccount()
        Instance.newProfile(account)
        Instance.choosePalette(account, "ocean")
        screen.startSignedIn(account)
        screen.openAccountSettings(account)

        // The scheme the web set for the account is followed, and named in the choice that follows it.
        val followed = text(R.string.appearance_palette_account, text(R.string.appearance_palette_ocean))
        screen.waitForText(followed)
        screen.onNodeWithText(followed).performScrollTo()
        screen.waitUntil(20_000) { chosen(followed) }
        screen.keepScreenshot("account-colour-scheme-of-the-account")

        // A scheme chosen on the phone is drawn in its place, and stays when the application starts again.
        val forest = text(R.string.appearance_palette_forest)
        screen.onNodeWithText(forest).performScrollTo().performClick()
        screen.waitUntil(20_000) { chosen(forest) }
        screen.activityRule.scenario.recreate()
        screen.waitForText(forest)
        screen.onNodeWithText(forest).performScrollTo()
        screen.waitUntil(20_000) { chosen(forest) }
        screen.keepScreenshot("account-colour-scheme-forest")

        // Following the account again.
        screen.onNodeWithText(followed).performScrollTo().performClick()
        screen.waitUntil(20_000) { chosen(followed) && !chosen(forest) }
    }
}
