// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.airplaneMode
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import net.whiteravens.dietapp.testing.waitForTheShell
import net.whiteravens.dietapp.ui.common.formatDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * The application opened anew without a network, nothing of the time before left on the screen: the
 * dashboard's day and the diary are there from what the phone keeps, and what is written waits; so are
 * a plan and its list of days, from their copies.
 */
@RunWith(AndroidJUnit4::class)
class StartWithoutANetworkTest {
    @get:Rule
    val screen = createEmptyComposeRule()

    @After
    fun backOnline() = screen.airplaneMode(false)

    private fun number(value: Int) = NumberFormat.getNumberInstance(Locale.getDefault()).format(value)

    @Test
    fun theDashboardAndTheDiaryOpenFromWhatIsKeptAndAnEntryWaits() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val target = number(Instance.dailyTarget(account, profileId))
        Instance.writeInTheDiary(account, profileId, "Morning oats", 300)

        // Opened once with the network: the phone keeps the profiles and the day.
        ActivityScenario.launch(MainActivity::class.java).use {
            screen.startSignedIn(account)
            screen.waitForText(text(R.string.diary_today_title))
            screen.waitForText("≈ ${text(R.string.diary_eaten_line, "300", target)}")
        }

        // Opened anew without it: the day as it was read, said to be, and nothing that is not known.
        screen.airplaneMode(true)
        ActivityScenario.launch(MainActivity::class.java).use {
            screen.waitForTheShell()
            screen.waitForText("Ala")
            screen.waitForText("≈ ${text(R.string.diary_eaten_line, "300", target)}")
            val (asOf, _) = text(R.string.offline_cached, "\u0000").split("\u0000")
            screen.waitForText(asOf, substring = true)
            screen.keepScreenshot("start-without-a-network-the-dashboard")

            // Written from it: the window opens on what can be written without a network.
            screen.onNode(hasText(text(R.string.diary_add)) and hasClickAction()).performClick()
            screen.waitForText(text(R.string.diary_meal_name))
            screen.keepScreenshot("start-without-a-network-the-window")
            screen.onNode(hasText(text(R.string.diary_meal_name)) and hasSetTextAction()).performScrollTo().performTextInput("Road snack")
            screen.onNode(hasText(text(R.string.diary_calories)) and hasSetTextAction()).performScrollTo().performTextInput("150")
            screen.putKeyboardAway()
            screen.onNode(hasText(text(R.string.diary_submit)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            screen.scrollToText("Road snack")
            screen.waitForText(text(R.string.change_waiting))
            screen.waitForText(plural(R.plurals.diary_sum_leaves_out, 1, 1))
            screen.keepScreenshot("start-without-a-network-an-entry-waiting")

            // With the network back it goes by itself.
            screen.airplaneMode(false)
            screen.waitForTheApi(90_000) { Instance.diaryEntries(account, profileId).toSet() == setOf("Morning oats", "Road snack") }
        }
    }

    @Test
    fun aPlanOpensFromItsCopy() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val planId = Instance.newPlan(account, profileId)
        val (title, _) = Instance.firstMeal(account, planId)
        val card = formatDate(LocalDate.now().toString())

        // Opened once with the network: the phone keeps the list and the plan.
        ActivityScenario.launch(MainActivity::class.java).use {
            screen.startSignedIn(account)
            screen.waitForText("Ala")
            screen.openTab(text(R.string.tab_meal_plans))
            screen.scrollToText(card, substring = true)
            screen.onNodeWithText(card, substring = true).performClick()
            screen.scrollToText(title)
        }

        screen.airplaneMode(true)
        ActivityScenario.launch(MainActivity::class.java).use {
            screen.waitForTheShell()
            screen.openTab(text(R.string.tab_meal_plans))
            screen.scrollToText(card, substring = true)
            screen.keepScreenshot("start-without-a-network-the-plans")
            screen.onNodeWithText(card, substring = true).performClick()
            screen.scrollToText(title)
            screen.keepScreenshot("start-without-a-network-a-plan")
        }
    }
}
