// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.provider.Settings
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.data.update.UpdateChannel
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * A newer build offered on the dashboard, the phone's allowance asked for, the build downloaded,
 * checked and handed to the system's installer, whose question to the user is stood in for: the
 * build is this application's own APK, which the test serves as a newer one from a channel of its own.
 */
@RunWith(AndroidJUnit4::class)
class UpdateTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer()
    private val channel get() = File(context.filesDir, UpdateChannel.TEST_CHANNEL)

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    @Before
    fun serve() {
        // A test's channel is read by a debug build alone.
        assumeTrue(BuildConfig.DEBUG)
        val apk = File(context.applicationInfo.sourceDir).readBytes()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        val version = """{"versionCode":${BuildConfig.VERSION_CODE + 1},"versionName":"edge-newer","apiVersion":"${BuildConfig.API_VERSION}",""" +
            """"apk":"diet-app-edge.apk","sha256":"$sha256","size":${apk.size}}"""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/channel/version.json" -> MockResponse().setBody(version)
                "/channel/diet-app-edge.apk" -> MockResponse().setBody(Buffer().write(apk))
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        channel.writeText(server.url("/channel/version.json").toString())
    }

    @After
    fun stop() {
        channel.delete()
        runCatching { server.shutdown() }
        val installer = context.packageManager.packageInstaller
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        // The allowance to install stays as given: taking it back ends the application's process,
        // which is the one the test runs in.
    }

    @Test
    fun aNewerBuildIsOfferedAllowedDownloadedCheckedAndHandedToTheSystem() {
        val account = Instance.newAccount()
        Instance.newProfile(account)
        screen.startSignedIn(account)

        // Asked for from "About": the start asked before the test named its channel.
        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.about_title)).performClick()
        screen.onNodeWithText(text(R.string.about_update_check)).performScrollTo().performClick()
        val newer = text(R.string.about_update_newer, "edge-newer", "#").substringBefore("#")
        screen.waitForText(newer, substring = true)
        screen.onNodeWithContentDescription(text(R.string.common_back)).performScrollTo().performClick()

        // The dashboard offers the build, and asks first for the phone's allowance to install it.
        screen.openTab(text(R.string.tab_dashboard))
        screen.waitForText(text(R.string.update_available, "edge-newer"))
        screen.keepScreenshot("update-offered")
        Intents.init()
        try {
            // The system's page of the allowance and the installer's question, and nothing else: the test's
            // own activities, through which the activity is paused and resumed, go on.
            val systems = anyOf(hasAction(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES), hasAction(containsString("CONFIRM_INSTALL")))
            intending(systems).respondWith(ActivityResult(Activity.RESULT_OK, null))
            screen.onNodeWithText(text(R.string.update_allow)).performClick()
            intended(allOf(hasAction(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES), hasData("package:${context.packageName}")))

            // The allowance given, as the system's page gives it; the card reads it on coming back.
            shell("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow")
            screen.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            screen.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            screen.waitForText(text(R.string.update_get))
            screen.onNodeWithText(text(R.string.update_get)).performClick()

            // Downloaded, checked as this application signed as it is, and handed to the system,
            // which asks the user: its question, stood in for here, is started by the application.
            screen.waitForText(text(R.string.update_handed), timeoutMillis = 120_000)
            screen.waitUntil(20_000) { runCatching { intended(hasAction(containsString("CONFIRM_INSTALL"))) }.isSuccess }
            screen.keepScreenshot("update-handed")
        } finally {
            Intents.release()
        }

        // Put off, the build is offered no more.
        screen.onNodeWithText(text(R.string.update_later)).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.update_available, "edge-newer")).fetchSemanticsNodes().isEmpty() }
    }
}
