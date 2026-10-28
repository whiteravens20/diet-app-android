// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.airplaneMode
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import net.whiteravens.dietapp.ui.common.formatDate
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * What the phone keeps of a plan is there to read when the network is not, and a meal is marked
 * eaten without it, the mark sent once the network is back.
 */
@RunWith(AndroidJUnit4::class)
class OfflineTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @After
    fun backOnline() = screen.airplaneMode(false)

    private companion object {
        /** Stands in the read-only text where the time goes, to be split at. */
        const val TIME = "\u0000"
    }

    @Test
    fun aPlanOpenedOnceIsReadWithoutANetworkFromTheCopyOnThePhone() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId)
        val (startDate, _) = Instance.planStart(account, planId)
        val (title, _) = Instance.firstMeal(account, planId)
        screen.signIn(account)

        // Opened once with the network: the phone keeps a copy.
        screen.openTab(text(R.string.tab_meal_plans))
        // The plan's card names the day it begins on; the list is below the calendar.
        val card = formatDate(startDate)
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()
        screen.scrollToText(title)
        // The way back is at the top of the plan, which the meals may have scrolled out of view.
        screen.onNodeWithContentDescription(text(R.string.common_back)).performScrollTo().performClick()
        screen.scrollToText(card, substring = true)

        // Opened again without it: the copy, said to be one with the time it was read, and its meals.
        screen.airplaneMode(true)
        screen.onNodeWithText(card, substring = true).performClick()
        // The text around the time, with the time itself told by the clock's form alone.
        val (asOf, readOnly) = text(R.string.offline_readonly, TIME).split(TIME)
        screen.waitForText(readOnly, substring = true, timeoutMillis = 40_000)
        val said = screen.onNodeWithText(readOnly, substring = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")
        assertTrue(said, said.matches(Regex(Regex.escape(asOf) + ".*\\d{2}:\\d{2}" + Regex.escape(readOnly))))
        screen.scrollToText(title)
        screen.keepScreenshot("offline-a-plan-from-the-copy")
    }

    @Test
    fun aMealMarkedEatenWithoutANetworkWaitsAndIsEatenOnTheServerOnceTheNetworkIsBack() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId)
        val (title, _) = Instance.firstMeal(account, planId)
        screen.signIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()
        val eaten = text(R.string.plans_action_for, text(R.string.plans_mark_eaten), title)
        screen.waitUntil(20_000) { screen.onAllNodesWithContentDescription(eaten).fetchSemanticsNodes().isNotEmpty() }
        val list = screen.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))

        // Without a network the meal is marked eaten all the same, and says that the mark waits to be
        // sent; the plan says it is offline, and how many changes wait.
        screen.airplaneMode(true)
        screen.onAllNodesWithContentDescription(eaten)[0].performScrollTo().performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(hasContentDescription(eaten) and isOn()).fetchSemanticsNodes().isNotEmpty() }
        screen.waitForText(text(R.string.change_waiting))
        screen.keepScreenshot("offline-a-meal-marked-eaten")
        val waiting = hasText(plural(R.plurals.changes_waiting, 1, 1), substring = true)
        screen.waitUntil(20_000) { runCatching { list.performScrollToNode(waiting) }.isSuccess }

        // With the network back, the mark goes by itself, the meal no longer waits, and the plan is no
        // longer offline.
        screen.airplaneMode(false)
        screen.waitForTheApi(90_000) { Instance.firstMealEaten(account, planId) }
        screen.waitUntil(30_000) { screen.onAllNodes(waiting).fetchSemanticsNodes().isEmpty() }
        screen.onAllNodesWithContentDescription(eaten)[0].performScrollTo()
        screen.waitUntil(30_000) { screen.onAllNodesWithText(text(R.string.change_waiting)).fetchSemanticsNodes().isEmpty() }
        screen.onAllNodes(hasContentDescription(eaten) and isOn())[0].performScrollTo()
        screen.keepScreenshot("offline-a-meal-marked-eaten-sent")
    }
}
