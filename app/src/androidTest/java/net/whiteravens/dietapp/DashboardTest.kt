// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun aProfileIsShownWithTheDailyTargetTheServerComputedForIt() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val target = Instance.dailyTarget(account, profileId)

        screen.signIn(account)

        screen.waitForText("Ala")
        screen.waitForText("$target ${text(R.string.unit_kcal)}")
        screen.keepScreenshot("dashboard-a-profile")

        // A profile with no plan is offered to make one, and the form for it opens.
        screen.waitForText(text(R.string.overview_none))
        screen.onNodeWithText(text(R.string.overview_none_cta)).performScrollTo().performClick()
        screen.waitForText(text(R.string.plans_start_date))
    }

    @Test
    fun thePlanUnderWaySaysWhatItComesToAndOpens() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        Instance.newPlan(account, profileId)
        screen.signIn(account)

        // A plan from today is the one under way.
        screen.waitForText(text(R.string.overview_title_active))
        screen.keepScreenshot("dashboard-the-plan-under-way")
        screen.onNodeWithText(text(R.string.overview_open)).performScrollTo().performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(hasContentDescription(text(R.string.plans_options))).fetchSemanticsNodes().isNotEmpty() }
    }
}
