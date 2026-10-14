// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sign-in screen names the version of the server the app is pointed at, when the build
 * was not checked against it. The server here is a local one in the test, answering what a
 * server of another version, or something that is not the API, would answer.
 */
@RunWith(AndroidJUnit4::class)
class ServerVersionTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private val elsewhere = MockWebServer()

    @After
    fun stop() {
        runCatching { elsewhere.shutdown() }
    }

    /** Points the app at [address] with the sign-in screen's server dialog, as a person would. */
    private fun useServer(address: String) {
        val button = text(R.string.server_label, BuildConfig.API_BASE_URL)
        screen.waitForText(button)
        screen.onNodeWithText(button).performClick()
        screen.onNode(hasSetTextAction() and hasText(BuildConfig.API_BASE_URL)).performTextReplacement(address)
        screen.onNodeWithText(text(R.string.common_save)).performClick()
    }

    @Test
    fun aServerOfAnotherVersionIsNamedBesideTheVersionTheAppWasCheckedAgainst() {
        elsewhere.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"instance":{"supportUrl":null,"operatorContact":null,"version":"0.0.1"}}"""),
        )
        elsewhere.start()

        useServer(elsewhere.url("/api/").toString())

        screen.waitForText(text(R.string.server_other_version, "0.0.1", BuildConfig.API_VERSION))
        screen.keepScreenshot("sign-in-server-of-another-version")
    }

    @Test
    fun aServerWhoseAnswerCannotBeReadRunsAVersionTheAppDoesNotSupport() {
        elsewhere.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<!doctype html><title>Not the API</title>"),
        )
        elsewhere.start()

        useServer(elsewhere.url("/api/").toString())

        screen.waitForText(text(R.string.server_unsupported))
        screen.keepScreenshot("sign-in-server-not-supported")
    }
}
