// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.opensSignedInTheBrowser
import net.whiteravens.dietapp.testing.opensTheBrowserAt
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.ui.common.ATTRIBUTION
import net.whiteravens.dietapp.ui.common.ATTRIBUTION_URL
import net.whiteravens.dietapp.ui.common.LICENCE_URL
import net.whiteravens.dietapp.ui.common.SOURCE_URL
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the application says of the instance and of itself under "More": the server's version and
 * what it allows, the instance's terms in the web application, the application's version, and the
 * legal notices with the attribution the licence's additional term asks for.
 */
@RunWith(AndroidJUnit4::class)
class AboutTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun theInstanceAndTheApplicationSayWhatTheyAreAndTheLegalNoticesKeepTheAttribution() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)

        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.about_title)).performClick()
        screen.waitForText(text(R.string.about_server_version))
        screen.waitForText(text(R.string.about_max_profiles))
        screen.onNodeWithText(BuildConfig.VERSION_NAME).performScrollTo()
        screen.onNodeWithText(text(R.string.about_notice, ATTRIBUTION, ATTRIBUTION_URL)).performScrollTo()
        screen.keepScreenshot("about-the-legal-notices")

        // Pages outside the instance are given to the browser as they are.
        opensTheBrowserAt(LICENCE_URL) {
            screen.onNodeWithText(text(R.string.about_licence_text)).performScrollTo().performClick()
        }
        opensTheBrowserAt(SOURCE_URL) {
            screen.onNodeWithText(text(R.string.about_source)).performScrollTo().performClick()
        }
        // The instance's terms are a page of its web application, opened signed in as every other.
        opensSignedInTheBrowser("/terms") {
            screen.onNodeWithText(text(R.string.about_terms)).performScrollTo().performClick()
        }
    }
}
