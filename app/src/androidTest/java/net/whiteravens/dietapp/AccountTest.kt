// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAccountIsShownWithTheServerAndSigningOutLeadsBackToTheSignIn() {
        val account = Instance.newAccount()
        screen.signIn(account)

        // The account's settings are under "More", as the web application's menu has them.
        screen.onNodeWithText(text(R.string.nav_more)).performClick()
        screen.keepScreenshot("more")
        screen.onNodeWithText(text(R.string.account_title)).performClick()
        screen.waitForText(account.email)
        screen.onNodeWithText(account.displayName).assertIsDisplayed()
        screen.onNodeWithText(BuildConfig.API_BASE_URL).performScrollTo().assertIsDisplayed()
        screen.keepScreenshot("account-settings")

        screen.onNodeWithText(text(R.string.account_sign_out)).performScrollTo().performClick()
        screen.waitForText(text(R.string.auth_welcome_back))
    }
}
