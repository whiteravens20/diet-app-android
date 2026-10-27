// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import net.whiteravens.dietapp.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.net.URLDecoder

/** The instance's web application: the address the app talks to, without its `api/`. */
val webApp: String = BuildConfig.API_BASE_URL.removeSuffix("api/")

/** How long the app may take to ask the instance for a code before it gives the browser the page. */
private const val CODE_WAIT_MILLIS = 15_000L

/**
 * Checks that [act] opens [next] of the instance's web application in the browser, signed in: the
 * browser is given the web application's page that signs it in, with a code and [next] in the
 * address's fragment and nothing of either in its path or query. The test answers in the
 * browser's place, as no browser on the emulator has to show the page. Returns the code.
 */
fun opensSignedInTheBrowser(next: String, act: () -> Unit): String {
    Intents.init()
    try {
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        act()
        // The app asks the instance for the code first: the browser is given the page a moment later.
        val deadline = SystemClock.uptimeMillis() + CODE_WAIT_MILLIS
        var opened = Intents.getIntents().lastOrNull { it.action == Intent.ACTION_VIEW }
        while (opened == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            opened = Intents.getIntents().lastOrNull { it.action == Intent.ACTION_VIEW }
        }
        val address = checkNotNull(opened?.data) { "no page was given to the browser" }
        assertEquals("${webApp}from-the-app", address.buildUpon().fragment(null).build().toString())
        assertNull(address.query)
        val fragment = address.encodedFragment.orEmpty().split('&').associate { part ->
            part.substringBefore('=') to URLDecoder.decode(part.substringAfter('='), "UTF-8")
        }
        val code = fragment["code"].orEmpty()
        assertTrue("a code of the instance's, not \"$code\"", code.matches(Regex("[0-9a-f]{64}")))
        assertEquals(next, fragment["next"])
        return code
    } finally {
        Intents.release()
    }
}

/**
 * Checks that [act] gives the browser [address], a page outside the instance, as it is: no code goes
 * with it. The test answers in the browser's place.
 */
fun opensTheBrowserAt(address: String, act: () -> Unit) {
    Intents.init()
    try {
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        act()
        val deadline = SystemClock.uptimeMillis() + CODE_WAIT_MILLIS
        var opened = Intents.getIntents().lastOrNull { it.action == Intent.ACTION_VIEW }
        while (opened == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            opened = Intents.getIntents().lastOrNull { it.action == Intent.ACTION_VIEW }
        }
        assertEquals(address, checkNotNull(opened?.data) { "no page was given to the browser" }.toString())
    } finally {
        Intents.release()
    }
}
