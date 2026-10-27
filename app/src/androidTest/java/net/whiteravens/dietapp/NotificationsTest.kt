// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.util.Locale

/**
 * The account's notifications, counted on the dashboard's bell, followed, put away and read all at
 * once. CI makes the account before the instance's worker starts (.github/scripts/start-diet-app.sh)
 * and names it in the arguments `notifiedEmail` and `notifiedPassword`: its profiles, Ala of 64 kg
 * and Ola of 58 kg, were reminded to log a weight by the worker's first pass, and asked to review
 * their preferences by the catalogue update, which took away an ingredient they favoured that no
 * catalogue has. Without the arguments there is nothing to read.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** The dashboard's bell, saying how many notifications are not read yet. */
    private fun bell(unread: Int): SemanticsMatcher =
        hasContentDescription(if (unread > 0) plural(R.plurals.notifications_unread, unread, unread) else text(R.string.notifications_title)) and
            hasClickAction()

    /** The notification of [profile] that says [said], which opens where it leads. */
    private fun notification(profile: String, said: String): SemanticsMatcher = hasText(profile) and hasText(said) and hasClickAction()

    /** Brings the bell into view, waiting for it to count [unread], and opens the notifications. */
    private fun openTheBell(unread: Int) {
        screen.scrollTo(bell(unread))
        screen.onNode(bell(unread)).performClick()
        screen.waitForText(text(R.string.notifications_title))
    }

    @Test
    fun theBellCountsWhatIsUnreadAndANotificationIsReadWhereItLeadsPutAwayOrReadWithTheRest() {
        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString("notifiedEmail")
        val password = arguments.getString("notifiedPassword")
        assumeTrue("no account with notifications", email != null && password != null)
        val account = Instance.signedIn(email!!, password!!)
        screen.startSignedIn(account)
        val reminder = text(R.string.notifications_weight_first)
        val review = text(R.string.notifications_preferences_review)

        // Each notification is said with the profile it is for. The list is scrolled to each: a small
        // phone shows some of them only, and a list composes only what is in view.
        openTheBell(4)
        listOf("Ala", "Ola").forEach { profile ->
            screen.scrollTo(notification(profile, reminder))
            screen.scrollTo(notification(profile, review))
        }
        screen.keepScreenshot("notifications")

        // A reminder leads to the weights of its profile on the dashboard: Ola's, the second profile's, out of sight before.
        screen.scrollTo(notification("Ola", reminder))
        screen.onNode(notification("Ola", reminder)).performClick()
        val olasWeights = text(R.string.weight_reference_hint, "${NumberFormat.getNumberInstance(Locale.getDefault()).format(58)} kg")
        screen.waitForText(olasWeights)
        screen.onNodeWithText(olasWeights).assertIsDisplayed()
        screen.keepScreenshot("notifications-a-reminder-followed")

        // A review of preferences leads to the profiles, whose preferences it names.
        openTheBell(3)
        screen.scrollTo(notification("Ala", review))
        screen.onNode(notification("Ala", review)).performClick()
        screen.waitForText(text(R.string.profiles_title))
        screen.onNodeWithContentDescription(text(R.string.common_back)).performClick()

        // One is put away where it stands, Ola's review, the newest; the last is read with the rest.
        openTheBell(2)
        screen.scrollTo(notification("Ola", review))
        screen.onAllNodes(hasText(text(R.string.notifications_dismiss)) and hasClickAction())[0].performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(notification("Ola", review)).fetchSemanticsNodes().isEmpty() }
        screen.onNode(notification("Ala", reminder)).assertExists()
        screen.onNodeWithText(text(R.string.notifications_read_all)).performClick()
        screen.waitForText(text(R.string.notifications_empty))
        screen.keepScreenshot("notifications-none")
        screen.waitForTheApi { Instance.unreadNotifications(account) == 0 }

        // Back on the dashboard, the bell counts none.
        screen.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        screen.scrollTo(bell(0))
    }
}
