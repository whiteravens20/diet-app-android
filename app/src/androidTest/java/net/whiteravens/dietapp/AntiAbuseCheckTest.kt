// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A server that asks for the anti-abuse check gets its answer with a sign-up. The server here is
 * a local one in the test, saying in `/config` that the check is on with Cloudflare's published
 * test key, which passes by itself; the check itself is Cloudflare's, loaded into the app.
 */
@RunWith(AndroidJUnit4::class)
class AntiAbuseCheckTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private val server = MockWebServer()

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun aSignUpOnAServerThatAsksForTheCheckSendsTheCheckAnswerWithIt() {
        val signUps = LinkedBlockingQueue<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/health" -> json(HEALTH)
                "/api/config" -> json(CONFIG)
                "/api/auth/register" -> {
                    signUps += request
                    json(SESSION, 201)
                }
                "/api/profiles" -> json("[]")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()

        // The server is chosen as a person chooses it; its version, named on the screen, says
        // that its config has been read.
        val button = text(R.string.server_label, BuildConfig.API_BASE_URL)
        screen.waitForText(button)
        screen.onNodeWithText(button).performClick()
        screen.onNode(hasSetTextAction() and hasText(BuildConfig.API_BASE_URL))
            .performTextReplacement(server.url("/api/").toString())
        screen.onNodeWithText(text(R.string.common_save)).performClick()
        screen.waitForText(text(R.string.server_other_version, VERSION, BuildConfig.API_VERSION))

        screen.onNodeWithText(text(R.string.auth_no_account)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.auth_name)).performTextInput("Check")
        screen.onNodeWithText(text(R.string.auth_email)).performTextInput("check@example.test")
        screen.onNodeWithText(text(R.string.auth_password)).performTextInput("Check-Test-Passw0rd")
        screen.putKeyboardAway()
        // The card's title reads the same as its button.
        screen.onNode(hasText(text(R.string.auth_create_account_button)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForText(text(R.string.auth_check_title))
        screen.keepScreenshot("sign-up-the-check")

        val signUp = signUps.poll(60, TimeUnit.SECONDS)
        assertNotNull("no sign-up reached the server", signUp)
        val sent = JSONObject(signUp!!.body.readUtf8())
        assertEquals("check@example.test", sent.getString("email"))
        assertTrue(sent.optString("turnstileToken").isNotBlank())
        screen.waitForText(text(R.string.dashboard_welcome))
    }

    private companion object {
        const val VERSION = "0.0.1-check"

        const val HEALTH = """{"status":"ok","db":"up","time":"2026-10-15T00:00:00.000Z"}"""

        val CONFIG = """
            {"instance":{"supportUrl":null,"operatorContact":null,"version":"$VERSION"},
             "turnstile":{"enabled":true,"siteKey":"1x00000000000000000000AA"}}
        """.trimIndent()

        val SESSION = """
            {"user":{"id":"8b3c7a52-0e0f-4c55-9e2a-3c6d1f1e0a01","email":"check@example.test","displayName":"Check",
                     "role":"user","emailVerified":false},
             "tokens":{"accessToken":"access","refreshToken":"refresh","expiresIn":900,"refreshExpiresIn":2592000},
             "sessionId":"5c1f0b8e-7a4d-4e3b-8f61-2d9a6c0e4b12"}
        """.trimIndent()
    }
}
