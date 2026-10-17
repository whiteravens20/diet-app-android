// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import net.whiteravens.dietapp.R
import net.whiteravens.dietapp.data.auth.KeystoreSealer
import net.whiteravens.dietapp.data.auth.TokenStore
import net.whiteravens.dietapp.data.remote.AuthTokens
import java.io.IOException
import kotlinx.coroutines.runBlocking

/** A text of the app's, in the language the device runs in, with [args] put in. */
fun text(@StringRes id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

/** A text of the app's that is counted, in the form the language gives [count], with [args] put in. */
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(id, count, *args)

/** Waits until something on the screen shows [shown], or a text holding it where [substring], as a person waits for a screen to load. */
fun ComposeTestRule.waitForText(shown: String, timeoutMillis: Long = 20_000, substring: Boolean = false) {
    waitUntil(timeoutMillis) { onAllNodesWithText(shown, substring = substring).fetchSemanticsNodes().isNotEmpty() }
}

/**
 * Scrolls the screen's list until it shows what [sought] matches, once the list has it, as a person
 * scrolls to what they look for: a list composes only what is in view.
 */
fun ComposeTestRule.scrollTo(sought: SemanticsMatcher, timeoutMillis: Long = 20_000) {
    waitUntil(timeoutMillis) {
        runCatching { onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(sought) }.isSuccess
    }
}

/**
 * Puts the keyboard away, as a person does before reaching for what lies under it, and waits until
 * the window in front, the app's or a dialog's, no longer has it. An open keyboard shortens the
 * screen, and its height can change a moment after a field is chosen, which would move what was
 * scrolled to between finding it and tapping it.
 */
fun ComposeTestRule.putKeyboardAway() {
    Espresso.closeSoftKeyboard()
    waitUntil(5_000) { !keyboardOpen() }
}

/** Whether the keyboard is over the window in front. */
private fun keyboardOpen(): Boolean {
    var open = false
    Espresso.onView(isRoot()).check { root, _ ->
        open = ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }
    return open
}

/** [scrollTo] what shows [shown], or a text holding it where [substring]. */
fun ComposeTestRule.scrollToText(shown: String, substring: Boolean = false, timeoutMillis: Long = 20_000) =
    scrollTo(hasText(shown, substring = substring), timeoutMillis)

/**
 * Keeps a picture of the whole screen, dialogs included, among the run's artefacts, under
 * `screenshots/`. A picture the device does not give, or one that cannot be written, is left out
 * with a line in the log: it is a record of the run, not something the test checks.
 */
fun ComposeTestRule.keepScreenshot(name: String) {
    waitForIdle()
    val picture = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    if (picture == null) {
        println("No screenshot for $name: the device gave none")
        return
    }
    try {
        TestStorage().openOutputFile("screenshots/$name.png").use { out -> picture.compress(Bitmap.CompressFormat.PNG, 100, out) }
    } catch (e: IOException) {
        println("No screenshot for $name: ${e.message}")
    }
}

/**
 * Clicks the first node [matcher] finds, once there is one. A list read anew while the test waits,
 * as a search is once typing pauses, can take the node away between finding it and the click: a
 * click that found no node is tried again.
 */
fun ComposeTestRule.clickFirst(matcher: SemanticsMatcher, timeoutMillis: Long = 20_000) {
    waitUntil(timeoutMillis) {
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() && runCatching { onAllNodes(matcher)[0].performClick() }.isSuccess
    }
}

/**
 * Waits until [condition], which asks the API, holds. It is asked at most once a second, so that a
 * wait stays well within what the instance lets one account ask of a route in a minute (120 by
 * default), beside the app's own asking; the screen runs on between the askings.
 */
fun ComposeTestRule.waitForTheApi(timeoutMillis: Long = 20_000, condition: () -> Boolean) {
    var askedAt = 0L
    var holds = false
    waitUntil(timeoutMillis) {
        val now = System.currentTimeMillis()
        if (now - askedAt >= API_PAUSE_MILLIS) {
            askedAt = now
            holds = condition()
        }
        holds
    }
}

/** The least time between two askings of the API while a test waits on it. */
private const val API_PAUSE_MILLIS = 1_000L

/** Waits until a button reading [label] can be pressed. */
fun ComposeTestRule.waitForEnabled(label: String, timeoutMillis: Long = 20_000) {
    waitUntil(timeoutMillis) { onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
}

/**
 * Whether the activity's saved state, which the system may write to disk, holds [text] anywhere:
 * the state is written out as the system writes it, and the bytes are searched. Saving marks the
 * activity as stopped, so it is then taken through a stop and back, as when a person leaves the
 * app and returns to it, and the screen can be used on.
 */
fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.savedStateHolds(text: String): Boolean {
    waitForIdle()
    val saved = Bundle()
    runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity, saved) }
    activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    val parcel = Parcel.obtain()
    try {
        saved.writeToParcel(parcel, 0)
        val written = parcel.marshall()
        return listOf(Charsets.UTF_16LE, Charsets.UTF_8).any { charset -> written.contains(text.toByteArray(charset)) }
    } finally {
        parcel.recycle()
    }
}

private fun ByteArray.contains(part: ByteArray): Boolean =
    (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

/**
 * Starts the app signed in to the session [account]'s sign-up opened, as a sign-in leaves it, and
 * waits for the dashboard, asking the API for nothing: the instance lets one address sign in thirty
 * times a quarter of an hour, and every screens' test runs from the emulator's one. A test of signing
 * in, or of the session a sign-in on the phone opens, signs in on the screen instead ([signIn]).
 */
fun ComposeTestRule.startSignedIn(account: Account) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    runBlocking {
        TokenStore(context, KeystoreSealer()).save(AuthTokens(account.accessToken, account.refreshToken, ACCESS_TOKEN_SECONDS), account.sessionId)
    }
    waitForText(text(R.string.nav_dashboard))
}

/** How long an access token of the instance holds, as its sign-up answers it. */
private const val ACCESS_TOKEN_SECONDS = 900.0

/** Opens the account's settings from the bar's "More", and waits for the account to be read. */
fun ComposeTestRule.openAccountSettings(account: Account) {
    openTab(text(R.string.nav_more))
    onNodeWithText(text(R.string.account_title)).performClick()
    waitForText(account.email)
}

/** Signs [account] in on the sign-in screen, as a person would, and waits for the dashboard. */
fun ComposeTestRule.signIn(account: Account) {
    onNodeWithText(text(R.string.auth_email)).performTextInput(account.email)
    onNodeWithText(text(R.string.auth_password)).performTextInput(account.password)
    putKeyboardAway()
    onNodeWithText(text(R.string.auth_sign_in_button)).performScrollTo().performClick()
    waitForText(text(R.string.nav_dashboard))
}

/**
 * Waits for the platform's print service to show itself over the app, and puts it away with the
 * back key: once, and again only where it has not gone after a while, so that a key pressed while
 * the app comes back does not leave the screen the app is on.
 */
fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.putAwayPrintService() {
    waitUntil(20_000) { activityRule.scenario.state != Lifecycle.State.RESUMED }
    var pressed = 0L
    waitUntil(30_000) {
        val back = activityRule.scenario.state == Lifecycle.State.RESUMED
        if (!back && System.currentTimeMillis() - pressed > 5_000) {
            pressed = System.currentTimeMillis()
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        back
    }
}

/**
 * Opens the screen of the bottom bar's item called [label]: the bar's own item, which a text of the
 * same name on the screen above it, a figure or a heading, is not.
 */
fun ComposeTestRule.openTab(label: String) {
    onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
}
