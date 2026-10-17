// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSettings
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.Assert.assertEquals
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
        // The name in the account's card, beside the field it is changed in.
        screen.onNode(hasText(account.displayName) and !hasSetTextAction()).assertIsDisplayed()
        screen.onNodeWithText(BuildConfig.API_BASE_URL).performScrollTo().assertIsDisplayed()
        screen.keepScreenshot("account-settings")

        // The rows of the account's sessions say whose sign-out they are; the account's own button says nothing more.
        screen.onNode(hasText(text(R.string.account_sign_out)) and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
            .performScrollTo().performClick()
        screen.waitForText(text(R.string.auth_welcome_back))
    }

    @Test
    fun theAccountIsGivenANewNameWithoutTheSpacesTypedAroundIt() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)

        val name = hasText(account.displayName) and hasSetTextAction()
        screen.onNode(name).performScrollTo().performTextReplacement("  Renamed on the phone ")
        screen.onNodeWithText(text(R.string.common_save)).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.me(account).getString("displayName") == "Renamed on the phone" }
        screen.waitForText(text(R.string.account_display_name_saved))
        screen.keepScreenshot("account-renamed")
    }

    @Test
    fun theLanguageOfTheMailsAndTheWebIsChosenWhileTheAppKeepsThePhonesLanguage() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)

        // Polish, named in itself; the app's texts stay as the phone has them.
        screen.onNodeWithText("Polski").performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.me(account).getString("locale") == "pl" }
        screen.keepScreenshot("account-language")
        screen.onNodeWithText(text(R.string.account_language)).assertExists()
        screen.onNodeWithText(text(R.string.account_title)).assertExists()
    }

    @Test
    fun anotherAddressIsAskedForThroughAMailWhereTheInstanceSendsMailAndNotOfferedWhereItDoesNot() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)

        if (!Instance.mailsSent()) {
            screen.waitForText(text(R.string.account_email_unavailable))
            screen.onNodeWithText(text(R.string.account_email_unavailable)).performScrollTo().assertIsDisplayed()
            screen.onNodeWithText(text(R.string.account_email_change)).assertDoesNotExist()
            return
        }
        screen.waitForText(text(R.string.account_email_change))
        screen.onNodeWithText(text(R.string.account_email_change)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.account_email_new)).performScrollTo().performTextInput("moved-${account.email}")
        // The form's own password, below the password card's.
        screen.onAllNodesWithText(text(R.string.account_password_current))[1].performScrollTo().performTextInput(account.password)
        screen.onNodeWithText(text(R.string.account_email_submit)).performScrollTo().performClick()
        screen.waitForText(text(R.string.account_email_sent))
        screen.keepScreenshot("account-address-asked-for")
        // The address changes once the link in the mail is followed, not before.
        assertEquals(account.email, Instance.me(account).getString("email"))
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

        // On the dashboard, reached from the bar the account's screen does not show, a weight is typed
        // in pounds, and the API keeps it in kilograms.
        screen.onNodeWithContentDescription(text(R.string.common_back)).performScrollTo().performClick()
        screen.openTab(text(R.string.nav_dashboard))
        val field = hasText(text(R.string.weight_today, "lb"))
        screen.scrollTo(field)
        screen.onNode(field).performScrollTo().performTextInput("180")
        screen.onNode(hasText(text(R.string.weight_log)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.weights(account, profileId) == listOf(81.65) }
    }
}
