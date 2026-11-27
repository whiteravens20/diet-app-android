// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * A change of a plan's day and its history: the card after the change says that the shopping list stopped matching the
 * plan, with the way to it; the day's history tells what holds the day and its changes, opened from the card, from the
 * day's link beside its diary and from the diary.
 */
@RunWith(AndroidJUnit4::class)
class DayHistoryTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** What is clickable in the window in front: the same words may stand on the screen under it. */
    private fun inTheWindow(words: String): SemanticsMatcher = hasText(words) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun waitForTheHistory() {
        screen.waitUntil(20_000) {
            screen.onAllNodes(hasText(text(R.string.history_changes_title)) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aDaySizedAgainSaysWhatItDidToTheListAndItsHistoryTellsIt() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val planId = Instance.newPlan(account, profileId, days = 3)
        // Today over its target as the user chose it; the list made then matches the plan until the day is sized again.
        assumeTrue("the entry sized no meal of the day", Instance.todayOverAsChosen(account, profileId, planId) > 0)
        Instance.newShoppingList(account, planId, LocalDate.now(), LocalDate.now().plusDays(2))
        screen.startSignedIn(account)

        // The plan, today's day over its target: its mark asks what to do, and the day is sized again.
        screen.waitForText(text(R.string.overview_title_active))
        screen.onNode(hasText(text(R.string.overview_open)) and hasClickAction()).performScrollTo().performClick()
        val over = hasText(text(R.string.plans_day_over)) and hasClickAction()
        screen.scrollTo(over)
        screen.onNode(over).performScrollTo().performClick()
        screen.onNode(inTheWindow(text(R.string.plans_resize_day))).performClick()

        // The card says the list no longer matches the plan, with the way to it, and leads to the day's history.
        val list = text(R.string.plans_effect_open_list)
        // The way to it is a link at the end of the list's sentence.
        screen.waitUntil(30_000) { screen.onAllNodes(hasText(list, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        screen.keepScreenshot("history-the-card-after-a-change")

        // The history from the card: the day sized again is its latest change, the user's choice it ended said with it.
        clickTheCardsHistory()
        waitForTheHistory()
        screen.waitForText(text(R.string.history_cause_resize))
        screen.keepScreenshot("history-the-window-from-the-card")
        screen.onNode(inTheWindow(text(R.string.history_keep))).performScrollTo()
        screen.keepScreenshot("history-the-window-its-ways")
        screen.onNode(hasText(text(R.string.history_cause_undo)) and hasAnyAncestor(isDialog())).performScrollTo()
        screen.keepScreenshot("history-the-window-its-changes")
        screen.onNode(inTheWindow(text(R.string.common_close))).performClick()

        // The day's links in sight: its diary and its history.
        screen.onNode(hasText(text(R.string.plans_rebalance_dismiss)) and hasClickAction()).performClick()
        val history = hasText(text(R.string.plans_history_link)) and hasClickAction()
        screen.scrollTo(history)
        screen.onNode(history).performScrollTo()
        screen.keepScreenshot("history-the-days-links")

        // From the diary of today, beside the way to its plan.
        screen.onNode(hasText(text(R.string.plans_open_diary)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForText(text(R.string.diary_subhead))
        val fromTheDiary = hasText(text(R.string.plans_history_link)) and hasClickAction()
        screen.scrollTo(fromTheDiary)
        screen.keepScreenshot("history-the-diarys-link")
        screen.onNode(fromTheDiary).performScrollTo().performClick()
        waitForTheHistory()
        screen.waitForText(text(R.string.history_open_in_plan))
        screen.keepScreenshot("history-the-window-from-the-diary")
    }

    /** The card's own "Historia dnia": the day's link under the card has the same words. */
    private fun clickTheCardsHistory() {
        val dismiss = screen.onNode(hasText(text(R.string.plans_rebalance_dismiss)) and hasClickAction()).fetchSemanticsNode()
        val links = screen.onAllNodes(hasText(text(R.string.plans_history_link)) and hasClickAction()).fetchSemanticsNodes()
        // The card's link stands on the card's last row, beside "Zamknij", the lowest of them on the screen.
        val card = links.minBy { kotlin.math.abs(it.boundsInRoot.top - dismiss.boundsInRoot.top) }
        screen.onAllNodes(hasText(text(R.string.plans_history_link)) and hasClickAction())[links.indexOf(card)].performClick()
    }
}
