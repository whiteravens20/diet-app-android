// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
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
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import net.whiteravens.dietapp.R
import java.io.IOException

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
 * Scrolls the screen's list until it shows [shown] (or a text holding it, where [substring]), once
 * the list has it, as a person scrolls to what they look for: a list composes only what is in view.
 */
fun ComposeTestRule.scrollToText(shown: String, substring: Boolean = false, timeoutMillis: Long = 20_000) {
    val sought = hasText(shown, substring = substring)
    waitUntil(timeoutMillis) {
        runCatching { onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(sought) }.isSuccess
    }
}

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

/** Signs [account] in on the sign-in screen, as a person would, and waits for the dashboard. */
fun ComposeTestRule.signIn(account: Account) {
    onNodeWithText(text(R.string.auth_email)).performTextInput(account.email)
    onNodeWithText(text(R.string.auth_password)).performTextInput(account.password)
    // Above the keyboard the field leaves open.
    onNodeWithText(text(R.string.auth_sign_in_button)).performScrollTo().performClick()
    waitForText(text(R.string.nav_dashboard))
}
