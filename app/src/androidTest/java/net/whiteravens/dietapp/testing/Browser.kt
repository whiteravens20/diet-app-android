// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import net.whiteravens.dietapp.BuildConfig
import org.hamcrest.Matchers.allOf

/** The instance's web application: the address the app talks to, without its `api/`. */
val webApp: String = BuildConfig.API_BASE_URL.removeSuffix("api/")

/**
 * Checks that [act] gives the browser [page], which the test answers in its place: a page of the
 * instance's web application, which no browser on the emulator has to show.
 */
fun opensInTheBrowser(page: String, act: () -> Unit) {
    Intents.init()
    try {
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        act()
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(page)))
    } finally {
        Intents.release()
    }
}
