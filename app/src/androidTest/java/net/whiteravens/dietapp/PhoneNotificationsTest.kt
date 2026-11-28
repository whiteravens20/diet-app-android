// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import net.whiteravens.dietapp.domain.AccountNotification
import net.whiteravens.dietapp.domain.NotificationAbout
import net.whiteravens.dietapp.notify.NotificationsWorker
import net.whiteravens.dietapp.notify.PhoneNotifier
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSection
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The account's notifications on the phone, against the instance the CI starts: it makes an account
 * whose two profiles, Ala and Ola, have two notifications each, and names it in the arguments
 * `phoneEmail` and `phonePassword`. Turning them on has WorkManager run the background work at once;
 * the test runs it again with WorkManager's test builder, the way Android would once its period came.
 * The read on coming to the front is WorkManager's.
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
        screen.openAccountSection(account, R.string.phone_notifications_title)

        // Off until they are turned on, which schedules the work.
        assertEquals(false, scheduled())
        screen.onNodeWithText(text(R.string.phone_notifications_show)).performScrollTo().performClick()
        screen.waitUntil(10_000) { scheduled() }
        screen.keepScreenshot("account-phone-notifications-on")

        // The account's four notifications are shown, each with its profile, by whichever of the two runs
        // read them first: the other finds them shown already.
        runTheWork()
        val reminder = text(R.string.notifications_weight_first)
        val review = text(R.string.notifications_preferences_review)
        val four = listOf("Ala" to reminder, "Ala" to review, "Ola" to reminder, "Ola" to review).sortedBy { it.toString() }
        runCatching { screen.waitUntil(10_000) { shown() == four } }
        assertEquals(four, shown())

        // One read elsewhere, in the web application or on another phone, goes from this phone at the next read.
        val readBy = Instance.readElsewhere(account, "preferences_review")
        runTheWork()
        val three = four - (readBy to review)
        runCatching { screen.waitUntil(10_000) { shown() == three } }
        assertEquals(three, shown())

        // Each is shown once: put away, the next run does not show them again.
        context.getSystemService(NotificationManager::class.java).cancelAll()
        runTheWork()
        assertEquals(emptyList<Pair<String, String>>(), shown())

        // Coming back to the front reads them at once, and shows none of them again either.
        screen.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        screen.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        screen.waitUntil(15_000) { readAtTheFront() }
        assertEquals(emptyList<Pair<String, String>>(), shown())

        // A tap opens the application's list of them. The activity takes the tap as a new intent, paused
        // for it and resumed, and for that moment there is no screen to read.
        context.startActivity(PhoneNotifier.opening(context))
        screen.waitUntil(20_000) {
            runCatching { screen.onAllNodesWithText(text(R.string.notifications_title)).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }

        // Turned off, the work stops.
        screen.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        screen.onNodeWithText(text(R.string.phone_notifications_show)).performScrollTo().performClick()
        screen.waitUntil(10_000) { !scheduled() }
    }

    /** What the application's notifications on the phone say, as the eye reads them. */
    private fun shownTexts(): List<String> =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.notification.channelId == PhoneNotifier.CHANNEL }
            .map { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().replace('\u00A0', ' ') }

    @Test
    fun aPressOfAnAmountOnAReminderOfWaterAddsItWithoutOpeningTheAppAndSaysSoBeforeItGoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        val today = LocalDate.now()
        val reminder = AccountNotification(UUID.randomUUID().toString(), profileId, NotificationAbout.WaterReminder(today, 0, 2000), Instant.now())
        PhoneNotifier.show(context, listOf(reminder), mapOf(profileId to "Ala"), private = false)

        // The reminder carries the amounts of the water card.
        val said = text(R.string.notifications_water_reminder, "0\u00A0${text(R.string.unit_liter)}", "2\u00A0${text(R.string.unit_liter)}").replace('\u00A0', ' ')
        // The system takes a notification in a moment after it was posted.
        fun posted() = context.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull {
            it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().replace('\u00A0', ' ') == said
        }
        screen.waitUntil(10_000) { posted() != null }
        val posted = posted()!!
        assertEquals(listOf("+200 ml", "+250 ml", "+330 ml"), posted.notification.actions.map { it.title.toString().replace('\u00A0', ' ') })

        // A press adds its amount to the reminded profile's day, says so for a moment, and the reminder goes.
        posted.notification.actions[2].actionIntent.send()
        screen.waitForTheApi(20_000) { Instance.water(account, profileId, today) == 330 }
        val added = text(R.string.notifications_water_added, "330\u00A0${text(R.string.unit_ml)}", "Ala").replace('\u00A0', ' ')
        screen.waitUntil(10_000) { added in shownTexts() }
        screen.waitUntil(15_000) { added !in shownTexts() }
    }

    @Test
    fun aReminderOfWaterGoesOnceWaterIsAddedToItsDayOnTheCardAndOneOfAnotherDayStays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val manager = context.getSystemService(NotificationManager::class.java)
        fun reminder(day: LocalDate) = AccountNotification(UUID.randomUUID().toString(), profileId, NotificationAbout.WaterReminder(day, 0, 2000), Instant.now())
        fun remindsOf(day: LocalDate) = manager.activeNotifications.any { PhoneNotifier.remindsOfWater(it.notification, profileId, day) }
        PhoneNotifier.show(context, listOf(reminder(today), reminder(yesterday)), mapOf(profileId to "Ala"), private = false)
        screen.waitUntil(10_000) { remindsOf(today) && remindsOf(yesterday) }

        // A glass on the card: who just drank is not told to drink, so today's reminder goes; yesterday's stays.
        val glass = hasText(text(R.string.water_add_ml, 250)) and hasClickAction()
        screen.scrollToText(text(R.string.water_title))
        screen.scrollTo(glass)
        screen.waitForEnabled(text(R.string.water_add_ml, 250))
        screen.onNode(glass).performScrollTo().performClick()
        screen.waitForTheApi { Instance.water(account, profileId, today) == 250 }
        screen.waitUntil(10_000) { !remindsOf(today) }
        assertTrue(remindsOf(yesterday))
        manager.cancelAll()
    }

    @Test
    fun whileTheUnlockIsOnANotificationIsPrivateAndItsPublicLineSaysOnlyThatThereIsOne() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        fun posted(name: String) = manager.activeNotifications.firstOrNull { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == name }
        val reminder = { id: String -> AccountNotification(id, id, NotificationAbout.WaterReminder(LocalDate.now(), 0, 2000), Instant.now()) }

        // On: private, and a locked phone that hides sensitive content reads the application's name and one line.
        PhoneNotifier.show(context, listOf(reminder("unlock-on")), mapOf("unlock-on" to "Hidden"), private = true)
        screen.waitUntil(10_000) { posted("Hidden") != null }
        val hidden = posted("Hidden")!!.notification
        assertEquals(Notification.VISIBILITY_PRIVATE, hidden.visibility)
        assertEquals(text(R.string.app_name), hidden.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(text(R.string.phone_notifications_hidden), hidden.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())

        // Off: as before, no public line of its own.
        PhoneNotifier.show(context, listOf(reminder("unlock-off")), mapOf("unlock-off" to "Shown"), private = false)
        screen.waitUntil(10_000) { posted("Shown") != null }
        assertEquals(null, posted("Shown")!!.notification.publicVersion)
        manager.cancelAll()
    }
}
