// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.view.View
import android.webkit.WebView
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.Matcher
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
 * a local one in the test, saying in `/config` that the check is on with one of Cloudflare's
 * published test keys; the check itself is Cloudflare's, loaded into the app.
 */
@RunWith(AndroidJUnit4::class)
class AntiAbuseCheckTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private val server = MockWebServer()
    private val signUps = LinkedBlockingQueue<RecordedRequest>()

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    /** The test's server, asking for the check with [siteKey]. */
    private fun serve(siteKey: String) {
        val config = """
            {"instance":{"supportUrl":null,"operatorContact":null,"version":"$VERSION"},
             "turnstile":{"enabled":true,"siteKey":"$siteKey"}}
        """.trimIndent()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/health" -> json(HEALTH)
                "/api/config" -> json(config)
                "/api/auth/register" -> {
                    signUps += request
                    json(SESSION, 201)
                }
                "/api/profiles" -> json("[]")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    /** The server chosen as a person chooses it, and a sign-up sent, which the check stops. */
    private fun signUpUntilTheCheck() {
        // Its version, named on the screen, says that its config has been read.
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
    }

    /** What the check's page measures: its widget's place, and how wide and tall the page is against its view. */
    private fun measureTheCheck(): JSONObject? {
        var measured: String? = null
        onView(isAssignableFrom(WebView::class.java)).inRoot(isDialog()).perform(
            object : ViewAction {
                override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)

                override fun getDescription() = "measure the check's page"

                override fun perform(uiController: UiController, view: View) {
                    (view as WebView).evaluateJavascript(MEASURE) { measured = it }
                    var waited = 0
                    while (measured == null && waited < 5_000) {
                        uiController.loopMainThreadForAtLeast(50)
                        waited += 50
                    }
                }
            },
        )
        return measured?.takeIf { it.startsWith("{") }?.let(::JSONObject)
    }

    @Test
    fun aSignUpOnAServerThatAsksForTheCheckSendsTheCheckAnswerWithIt() {
        // Cloudflare's test key that passes by itself.
        serve("1x00000000000000000000AA")
        signUpUntilTheCheck()
        screen.keepScreenshot("sign-up-the-check")

        val signUp = signUps.poll(60, TimeUnit.SECONDS)
        assertNotNull("no sign-up reached the server", signUp)
        val sent = JSONObject(signUp!!.body.readUtf8())
        assertEquals("check@example.test", sent.getString("email"))
        assertTrue(sent.optString("turnstileToken").isNotBlank())
        screen.waitForText(text(R.string.dashboard_welcome))
    }

    @Test
    fun cloudflaresWidgetFitsTheDialogItIsShownIn() {
        // Cloudflare's test key that always asks for a click: the widget stays to be measured.
        serve("3x00000000000000000000FF")
        signUpUntilTheCheck()

        var drawn: JSONObject? = null
        screen.waitUntil(60_000) {
            drawn = runCatching { measureTheCheck() }.getOrNull()?.takeIf { it.optDouble("checkHeight") >= 60.0 }
            drawn != null
        }
        val page = checkNotNull(drawn)
        // Nothing of the page is wider than its view, or taller.
        assertTrue(page.toString(), page.getDouble("scrollWidth") <= page.getDouble("innerWidth"))
        assertTrue(page.toString(), page.getDouble("scrollHeight") <= page.getDouble("innerHeight"))
        screen.keepScreenshot("sign-up-the-check-drawn")
        screen.onNodeWithText(text(R.string.common_cancel)).performClick()
    }

    private companion object {
        const val VERSION = "0.0.1-check"

        const val HEALTH = """{"status":"ok","db":"up","time":"2026-10-15T00:00:00.000Z"}"""

        val SESSION = """
            {"user":{"id":"8b3c7a52-0e0f-4c55-9e2a-3c6d1f1e0a01","email":"check@example.test","displayName":"Check",
                     "role":"user","emailVerified":false},
             "tokens":{"accessToken":"access","refreshToken":"refresh","expiresIn":900,"refreshExpiresIn":2592000},
             "sessionId":"5c1f0b8e-7a4d-4e3b-8f61-2d9a6c0e4b12"}
        """.trimIndent()

        /** The place of the check's widget, and the page's size against its view's, in CSS pixels. */
        val MEASURE = """
            (function () {
              var check = document.getElementById('check');
              var box = check ? check.getBoundingClientRect() : { width: 0, height: 0 };
              var page = document.documentElement;
              return { checkWidth: box.width, checkHeight: box.height, scrollWidth: page.scrollWidth,
                       innerWidth: window.innerWidth, scrollHeight: page.scrollHeight, innerHeight: window.innerHeight };
            })()
        """.trimIndent()
    }
}
