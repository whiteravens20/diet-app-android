// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.data.update.UpdateInstaller
import net.whiteravens.dietapp.notify.PhoneNotifier
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/**
 * What another application on the phone may send the main activity, which is exported as the launcher: the intents
 * of the audit's test application, sent here as it sends them. They start nothing and open nothing; the application's
 * own tap, beside them, still does.
 */
@RunWith(AndroidJUnit4::class)
class ForgedIntentsTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** An intent for the main activity, as another application addresses it: by name, to the activity that runs. */
    private fun toTheMainActivity(): Intent =
        Intent().setClassName(context.packageName, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Time for the activity to have acted on what it was sent, had it acted. */
    private fun given() = SystemClock.sleep(3_000)

    @Test
    fun anIntentSentAsTheInstallersQuestionStartsNothing() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        // The installer's answer, a question for the user, and inside it the application's water shortcut, which is not
        // exported: what the test application sent, which the old activity started as the application itself.
        val shortcut = Intent().setClassName(context.packageName, WaterShortcutActivity::class.java.name).putExtra("ml", 777)
        val forged = toTheMainActivity()
            .setAction(UpdateInstaller.STATUS)
            .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
            .putExtra(Intent.EXTRA_INTENT, shortcut)
        Intents.init()
        try {
            context.startActivity(forged)
            given()
            Intents.intended(hasComponent(WaterShortcutActivity::class.java.name), Intents.times(0))
        } finally {
            Intents.release()
        }
        assertEquals(0, Instance.water(account, profileId, LocalDate.now()))
    }

    @Test
    fun anIntentSentAsATapOnANotificationOpensNothingAndTheApplicationsOwnTapDoes() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        screen.openTab(text(R.string.nav_more))
        screen.waitForText(text(R.string.about_title))
        val listShown = { screen.onAllNodesWithText(text(R.string.notifications_title)).fetchSemanticsNodes().isNotEmpty() }

        // The actions of the application's own notifications, sent by another application: still where the person was.
        context.startActivity(toTheMainActivity().setAction(PhoneNotifier.OPEN_NOTIFICATIONS))
        context.startActivity(
            toTheMainActivity()
                .setAction(PhoneNotifier.OPEN_WATER)
                .putExtra(PhoneNotifier.EXTRA_PROFILE, profileId)
                .putExtra(PhoneNotifier.EXTRA_NOTIFICATION, UUID.randomUUID().toString()),
        )
        given()
        screen.waitForText(text(R.string.about_title))
        assertTrue("a forged tap opened the list of notifications", !listShown())

        // The application's own tap opens the list.
        context.startActivity(PhoneNotifier.opening(context))
        screen.waitUntil(20_000) { runCatching { listShown() }.getOrDefault(false) }
    }
}
