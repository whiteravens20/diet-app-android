// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.ui.common.formatDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the phone keeps of a plan is there to read when the network is not. */
@RunWith(AndroidJUnit4::class)
class OfflineTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private fun airplaneMode(on: Boolean) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
            .close()
    }

    @After
    fun backOnline() = airplaneMode(false)

    @Test
    fun aPlanOpenedOnceIsReadWithoutANetworkFromTheCopyOnThePhone() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId)
        val (startDate, days) = Instance.planStart(account, planId)
        val (title, _) = Instance.firstMeal(account, planId)
        screen.signIn(account)

        // Opened once with the network: the phone keeps a copy.
        screen.onNodeWithText(text(R.string.nav_meal_plans)).performClick()
        val card = text(R.string.plans_card_title, formatDate(startDate), days)
        screen.waitForText(card)
        screen.onNodeWithText(card).performClick()
        screen.waitForText(title)
        screen.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        screen.waitForText(card)

        // Opened again without it: the copy, said to be one, with its meals.
        airplaneMode(true)
        screen.onNodeWithText(card).performClick()
        screen.waitForText(text(R.string.offline_readonly), timeoutMillis = 40_000)
        screen.waitForText(title)
        screen.keepScreenshot("offline-a-plan-from-the-copy")
    }
}
