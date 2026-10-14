// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage

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
