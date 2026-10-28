// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.notify.PhoneNotificationsSwitch
import net.whiteravens.dietapp.notify.NotificationsWorker
import net.whiteravens.dietapp.notify.PhoneNotifier
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSettings
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The account's notifications on the phone, against the instance the CI starts: it makes an account
 * whose two profiles, Ala and Ola, have two notifications each, and names it in the arguments
 * `phoneEmail` and `phonePassword`. The background work is run by WorkManager's test builder, the
 * way Android would run it once its period came; the read on coming to the front, by WorkManager.
 */
@RunWith(AndroidJUnit4::class)
class PhoneNotificationsTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun scheduled(): Boolean =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(PhoneNotificationsSwitch.WORK).get().any { it.state == WorkInfo.State.ENQUEUED }

    /**
     * The application's notifications on the phone: the profile each is for, and what it says. Four
     * or more are bundled by Android under a summary of its own, which says nothing and is left out.
     */
    private fun shown(): List<Pair<String, String>> =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.notification.channelId == PhoneNotifier.CHANNEL && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .map { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString() to it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() }
            .sortedBy { it.toString() }

    private fun readAtTheFront(): Boolean =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(PhoneNotificationsSwitch.NOW).get().any { it.state == WorkInfo.State.SUCCEEDED }

    private fun runTheWork() {
        val worker = TestListenableWorkerBuilder.from(context, NotificationsWorker::class.java).build()
        assertEquals(ListenableWorker.Result.success(), runBlocking { worker.doWork() })
    }

    @Test
    fun notificationsTurnedOnAreShownOnThePhoneEachOnceATapOpensTheListAndTurnedOffTheyStop() {
        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString("phoneEmail")
        val password = arguments.getString("phonePassword")
        assumeTrue("no account with notifications for the phone", email != null && password != null)
        // Android 13 and later ask the user for the permission; it is given here as the user gives it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val account = Instance.signedIn(email!!, password!!)
        screen.startSignedIn(account)
        screen.openAccountSettings(account)

        // Off until they are turned on, which schedules the work.
        assertEquals(false, scheduled())
        screen.onNodeWithText(text(R.string.phone_notifications_show)).performScrollTo().performClick()
        screen.waitUntil(10_000) { scheduled() }
        screen.keepScreenshot("account-phone-notifications-on")

        // A run of the work shows the account's four notifications, each with its profile.
        runTheWork()
        val reminder = text(R.string.notifications_weight_first)
        val review = text(R.string.notifications_preferences_review)
        assertEquals(listOf("Ala" to reminder, "Ala" to review, "Ola" to reminder, "Ola" to review).sortedBy { it.toString() }, shown())

        // Each is shown once: put away, the next run does not show them again.
        context.getSystemService(NotificationManager::class.java).cancelAll()
        runTheWork()
        assertEquals(emptyList<Pair<String, String>>(), shown())

        // Coming back to the front reads them at once, and shows none of them again either.
        screen.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        screen.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        screen.waitUntil(15_000) { readAtTheFront() }
        assertEquals(emptyList<Pair<String, String>>(), shown())

        // A tap opens the application's list of them.
        context.startActivity(PhoneNotifier.opening(context))
        screen.waitForText(text(R.string.notifications_title))

        // Turned off, the work stops.
        screen.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        screen.onNodeWithText(text(R.string.phone_notifications_show)).performScrollTo().performClick()
        screen.waitUntil(10_000) { !scheduled() }
    }
}
