// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import net.whiteravens.dietapp.R

/** A text of the app's, in the language the device runs in, with [args] put in. */
fun text(@StringRes id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

/** Waits until something on the screen shows [shown], as a person waits for a screen to load. */
fun ComposeTestRule.waitForText(shown: String, timeoutMillis: Long = 20_000) {
    waitUntil(timeoutMillis) { onAllNodesWithText(shown).fetchSemanticsNodes().isNotEmpty() }
}

/** Keeps a picture of the screen among the run's artefacts, under `screenshots/`. */
fun ComposeTestRule.keepScreenshot(name: String) {
    val picture = onRoot().captureToImage().asAndroidBitmap()
    TestStorage().openOutputFile("screenshots/$name.png").use { out ->
        picture.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
}

/** Waits until a button reading [label] can be pressed. */
fun ComposeTestRule.waitForEnabled(label: String, timeoutMillis: Long = 20_000) {
    waitUntil(timeoutMillis) { onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
}

/** Signs [account] in on the sign-in screen, as a person would, and waits for the dashboard. */
fun ComposeTestRule.signIn(account: Account) {
    onNodeWithText(text(R.string.auth_email)).performTextInput(account.email)
    onNodeWithText(text(R.string.auth_password)).performTextInput(account.password)
    onNodeWithText(text(R.string.auth_sign_in_button)).performClick()
    waitForText(text(R.string.nav_dashboard))
}
