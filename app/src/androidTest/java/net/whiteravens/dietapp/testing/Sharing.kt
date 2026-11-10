// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.print.PrintManager
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference

/** How long the app may take to hand something to the system's sharing or printing. */
private const val HANDED_WAIT_MILLIS = 20_000L

/** The package of the system's printing, whose window shows what is to be printed. */
private const val PRINTING = "com.android.printspooler"

/**
 * Runs [act], which hands something to the system's window of apps to share with, answering that window
 * in the system's place, and returns what an app chosen there would be given. The stubs are released
 * only once the answer was given, as [opensTheBrowserAt]'s are.
 */
fun sharedBy(act: () -> Unit): Intent {
    val answered = AtomicReference<Intent?>(null)
    Intents.init()
    try {
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWithFunction { intent ->
            answered.set(intent)
            Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        }
        act()
        val deadline = SystemClock.uptimeMillis() + HANDED_WAIT_MILLIS
        while (answered.get() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        val chooser = checkNotNull(answered.get()) { "nothing was handed to the sharing" }
        // Read with the platform's own calls: a class of a library the minified app leaves out is not there for the test.
        val sent = if (Build.VERSION.SDK_INT >= 33) {
            chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        }
        return checkNotNull(sent) { "the sharing was given nothing to send" }
    } finally {
        Intents.release()
    }
}

/**
 * Runs [act], which hands a document to the system's printing, and checks that the printing has a job
 * of the app's named [title]. Its window, another app's, is kept as the picture [picture] and closed,
 * and the app is in front again.
 */
fun ComposeTestRule.printedAs(title: String, picture: String, act: () -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val printing = instrumentation.targetContext.getSystemService(PrintManager::class.java)
    val inFront = { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() }
    act()
    val deadline = SystemClock.uptimeMillis() + HANDED_WAIT_MILLIS
    while (printing.printJobs.none { it.info.label == title } && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
    check(printing.printJobs.any { it.info.label == title }) { "the printing has no job named \"$title\"" }
    while (inFront() != PRINTING && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
    // The printing draws its preview of the page when it has laid it out, with nothing a journey can wait
    // for: the picture is a record of the run, not a check, and is given that time.
    SystemClock.sleep(3_000)
    keepScreenshot(picture)
    instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    val back = SystemClock.uptimeMillis() + HANDED_WAIT_MILLIS
    while (inFront() != instrumentation.targetContext.packageName && SystemClock.uptimeMillis() < back) SystemClock.sleep(100)
    check(inFront() == instrumentation.targetContext.packageName) { "the printing's window stayed in front" }
}
