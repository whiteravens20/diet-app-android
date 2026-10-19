// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSettings
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.opensInTheBrowser
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import net.whiteravens.dietapp.testing.webApp
import org.hamcrest.CoreMatchers.allOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        // The password, the address, the name and the language of the mails are changed in the web
        // application, whose settings the account's screen opens in the browser.
        opensInTheBrowser("${webApp}settings") {
            screen.onNodeWithText(text(R.string.web_open)).performScrollTo().performClick()
        }

        // The rows of the account's sessions say whose sign-out they are; the account's own button says nothing more.
        screen.onNode(hasText(text(R.string.account_sign_out)) and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
            .performScrollTo().performClick()
        screen.waitForText(text(R.string.auth_welcome_back))
    }

    @Test
    fun theAccountsDataIsSavedWholeToTheFileChosen() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "account.json").apply { delete() }

        // The platform's window that asks where to keep the file answers with a file of the test's.
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
            screen.onNodeWithText(text(R.string.account_data_button)).performScrollTo().performClick()
            screen.waitForText(text(R.string.account_data_saved))
            intended(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasExtra(Intent.EXTRA_TITLE, "diet-app-account-${LocalDate.now()}.json")))
        } finally {
            Intents.release()
        }
        screen.keepScreenshot("account-data-saved")

        val saved = JSONObject(file.readText())
        assertEquals("diet-app/account/1", saved.getString("format"))
        assertEquals(account.email, saved.getJSONObject("account").getString("email"))
        assertTrue(saved.getBoolean("complete"))
    }

    @Test
    fun theAccountIsDeletedOnceItsAddressIsTypedAgainAndItsPasswordGiven() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)

        screen.onNodeWithText(text(R.string.account_delete)).performScrollTo().performClick()
        // The address as typed in capitals is the account's all the same.
        screen.onNode(hasText(text(R.string.account_delete_email)) and hasSetTextAction()).performScrollTo().performTextInput(account.email.uppercase())
        screen.onNode(hasText(text(R.string.account_delete_password)) and hasSetTextAction()).performScrollTo().performTextInput(account.password)
        screen.keepScreenshot("account-deletion")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.account_delete_forever)).performScrollTo().performClick()

        screen.waitForText(text(R.string.auth_welcome_back))
        assertFalse(Instance.signsIn(account))
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
        screen.putKeyboardAway()
        screen.onNode(hasText(text(R.string.weight_log)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.weights(account, profileId) == listOf(81.65) }
    }
}
