// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
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
        screen.openTab(text(R.string.nav_more))
        screen.keepScreenshot("more")
        screen.onNodeWithText(text(R.string.account_title)).performClick()
        screen.waitForText(account.email)
        screen.onNodeWithText(account.displayName).assertIsDisplayed()
        screen.onNodeWithText(BuildConfig.API_BASE_URL).performScrollTo().assertIsDisplayed()
        screen.keepScreenshot("account-settings")

        screen.onNodeWithText(text(R.string.account_sign_out)).performScrollTo().performClick()
        screen.waitForText(text(R.string.auth_welcome_back))
    }

    @Test
    fun theUnitOfWeightsIsChosenForTheAccountAndAWeightIsThenTypedInIt() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        screen.signIn(account)

        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.account_title)).performClick()
        screen.waitForText(account.email)

        // Each choice is kept with the account at once, and "by where I am" gives the choice back.
        for ((label, kept) in listOf(R.string.account_weight_unit_kg to "kg", R.string.account_weight_unit_by_region to null, R.string.account_weight_unit_lb to "lb")) {
            screen.waitForEnabled(text(label))
            screen.onNodeWithText(text(label)).performScrollTo().performClick()
            screen.waitForTheApi(20_000) { Instance.weightUnit(account) == kept }
        }
        screen.keepScreenshot("account-weight-unit")

        // On the dashboard a weight is typed in pounds, and the API keeps it in kilograms.
        screen.openTab(text(R.string.nav_dashboard))
        val field = hasText(text(R.string.weight_today, "lb"))
        screen.scrollTo(field)
        screen.onNode(field).performScrollTo().performTextInput("180")
        screen.onNode(hasText(text(R.string.weight_log)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.weights(account, profileId) == listOf(81.65) }
    }
}
