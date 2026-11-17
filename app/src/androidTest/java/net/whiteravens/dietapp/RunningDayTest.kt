// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import net.whiteravens.dietapp.testing.Account
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.ui.common.formatDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A day of a running plan changed after its breakfast and lunch were eaten: the window of its settings says each dish's
 * new portion, and where the dishes left cannot reach a new target it says what the day falls short of and offers to
 * spread it over the next days of the plan, or says that the plan's last day has none.
 */
@RunWith(AndroidJUnit4::class)
class RunningDayTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** The beginning of a text of the app's that holds figures, up to its first one, read as it is written. */
    private fun startOf(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id).substringBefore("%")

    /** A plan from yesterday of [days] days for a new account's profile, with today's breakfast and lunch eaten. */
    private fun aPlanUnderWay(days: Int): Pair<Account, String> {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId, days = days, start = LocalDate.now().minusDays(1))
        for (slot in listOf("breakfast", "lunch")) Instance.markEaten(account, planId, Instance.mealAt(account, planId, 1, slot).first)
        return account to planId
    }

    /** The settings of today's day, from the plan's card: it opens at its first day, yesterday, and goes to today. */
    private fun openTodaysSettings(account: Account) {
        screen.startSignedIn(account)
        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().minusDays(1).toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()
        screen.scrollToText(text(R.string.plans_go_to_today))
        screen.onNodeWithText(text(R.string.plans_go_to_today)).performClick()
        screen.scrollToText(text(R.string.plans_day_settings))
        screen.onNodeWithText(text(R.string.plans_day_settings)).performClick()
        screen.waitForText(text(R.string.plans_settings_unchanged))
    }

    /** A target of the day's own far past what its dishes left can take. */
    private fun aTargetFarPast() {
        screen.onNode(hasText(text(R.string.plans_settings_own_target)) and hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("5000")
        screen.putKeyboardAway()
    }

    @Test
    fun aChangeOfARunningDaySaysEachDishsPortionAndOffersToSpreadWhatTheDayFallsShortOf() {
        val (account, planId) = aPlanUnderWay(days = 5)
        // A list of the days after today: spreading over them puts it out of date.
        Instance.newShoppingList(account, planId, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3))
        openTodaysSettings(account)

        // A training day: each dish left that grows says its servings and kcal now and after (one already at the most
        // its portion takes stays as it is), above what the day comes to.
        screen.onNode(hasText(text(R.string.plans_settings_kind)) and hasAnyAncestor(isDialog())).performClick()
        screen.onNode(hasText(text(R.string.plans_day_kind_training)) and hasAnyAncestor(isPopup())).performClick()
        val dayLine = startOf(R.string.plans_settings_day_calories)
        screen.waitForText(dayLine, substring = true)
        screen.onNodeWithText(dayLine, substring = true).performScrollTo()
        screen.keepScreenshot("running-day-portions")

        // Its own target far past what the dishes can take: what the day falls short of, and the next days offered it.
        aTargetFarPast()
        val spread = hasText(startOf(R.string.plans_settings_spread_short), substring = true) and hasAnyAncestor(isDialog())
        screen.waitUntil(20_000) { screen.onAllNodes(spread).fetchSemanticsNodes().isNotEmpty() }
        screen.waitForText(text(R.string.plans_list_out_of_date_days), substring = true)
        screen.onNode(spread).performScrollTo()
        screen.keepScreenshot("running-day-short")
        screen.onNode(hasText(text(R.string.common_cancel)) and hasAnyAncestor(isDialog())).performClick()
    }

    @Test
    fun theLastDayOfAPlanSaysThatWhatItFallsShortOfHasNoDayToGoTo() {
        val (account, _) = aPlanUnderWay(days = 2)
        openTodaysSettings(account)

        aTargetFarPast()
        val lastDay = startOf(R.string.plans_settings_short_last_day)
        screen.waitForText(lastDay, substring = true)
        screen.onNodeWithText(lastDay, substring = true).performScrollTo()
        screen.keepScreenshot("running-day-last-day")
        val spread = hasText(startOf(R.string.plans_settings_spread_short), substring = true) and hasAnyAncestor(isDialog())
        assertTrue(screen.onAllNodes(spread).fetchSemanticsNodes().isEmpty())
        screen.onNode(hasText(text(R.string.common_cancel)) and hasAnyAncestor(isDialog())).performClick()
    }
}
