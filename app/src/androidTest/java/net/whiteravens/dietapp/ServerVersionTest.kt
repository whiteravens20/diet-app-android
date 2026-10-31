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
import net.whiteravens.dietapp.testing.shownAddress
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sign-in screen's server dialog names what is wrong with an address, and the screen names
 * the version of the server the app is pointed at, when the build was not checked against it.
 * The server here is a local one in the test, answering what a server of another version, or
 * something that is not the API, would answer.
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
        val button = text(R.string.server_label, shownAddress)
        screen.waitForText(button)
        screen.onNodeWithText(button).performClick()
        screen.onNode(hasSetTextAction() and hasText(shownAddress)).performTextReplacement(address)
        screen.onNodeWithText(text(R.string.common_save)).performClick()
    }

    /** Answers [config] at the config route, and an API's health at the health route. */
    private fun serve(config: () -> MockResponse, health: () -> MockResponse = ::healthUp) {
        elsewhere.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/health" -> health()
                "/api/config" -> config()
                else -> MockResponse().setResponseCode(404)
            }
        }
        elsewhere.start()
    }

    private fun healthUp() = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"status":"ok","db":"up","time":"2026-10-15T00:00:00.000Z"}""")

    private fun notTheApi() = MockResponse()
        .setHeader("Content-Type", "text/html")
        .setBody("<!doctype html><title>Not the API</title>")

    @Test
    fun aServerOfAnotherVersionIsNamedBesideTheVersionTheAppWasCheckedAgainst() {
        serve(
            config = {
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"instance":{"supportUrl":null,"operatorContact":null,"version":"0.0.1"},""" +
                            """"turnstile":{"enabled":false,"siteKey":null}}""",
                    )
            },
        )

        useServer(elsewhere.url("/api/").toString())

        screen.waitForText(text(R.string.server_other_version, "0.0.1", BuildConfig.API_VERSION))
        screen.keepScreenshot("sign-in-server-of-another-version")
    }

    @Test
    fun anAddressWhereSomethingElseAnswersIsNamedAndKeptOnlyWhenAskedAgainAsAVersionTheAppDoesNotSupport() {
        serve(config = ::notTheApi, health = ::notTheApi)

        useServer(elsewhere.url("/api/").toString())

        screen.waitForText(text(R.string.server_not_the_api))
        screen.keepScreenshot("sign-in-server-not-the-api")
        screen.onNodeWithText(text(R.string.server_save_anyway)).performClick()
        screen.waitForText(text(R.string.server_unsupported))
    }

    @Test
    fun anAddressWithAPasswordInItIsNotKept() {
        useServer("https://ala:secret@diet.example.org")

        screen.waitForText(text(R.string.server_credentials))
        screen.onNodeWithText(text(R.string.common_cancel)).performClick()
        screen.waitForText(text(R.string.server_label, shownAddress))
    }
}
