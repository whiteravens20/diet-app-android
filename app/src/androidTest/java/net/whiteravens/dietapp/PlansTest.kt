// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.ui.common.formatDate
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlansTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun aPlanIsMadeOpenedAndAMealSwappedForARecipeOffered() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        screen.signIn(account)

        screen.onNodeWithText(text(R.string.nav_meal_plans)).performClick()
        screen.waitForEnabled(text(R.string.plans_generate))
        screen.onNode(hasText(text(R.string.plans_generate)) and hasClickAction()).performClick()
        screen.onNode(hasText(text(R.string.plans_generate)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        // The plan is made on the server; the list shows it under its first day and its length.
        screen.waitUntil(60_000) { Instance.planIds(account, profileId).isNotEmpty() }
        val planId = Instance.planIds(account, profileId).first()
        val (startDate, days) = Instance.planStart(account, planId)
        val card = text(R.string.plans_card_title, formatDate(startDate), days)
        screen.waitForText(card)
        screen.keepScreenshot("plans-a-plan-made")
        screen.onNodeWithText(card).performClick()

        // The first meal of the first day is swapped: one offer is put away for another, which is taken.
        val swap = text(R.string.plans_swap)
        screen.waitUntil(20_000) { screen.onAllNodesWithContentDescription(swap).fetchSemanticsNodes().isNotEmpty() }
        val before = Instance.firstDayRecipes(account, planId)
        screen.onAllNodesWithContentDescription(swap)[0].performClick()
        screen.waitForEnabled(text(R.string.plans_swap_take))
        screen.keepScreenshot("plans-a-swap-offered")
        screen.onNodeWithText(text(R.string.plans_swap_another)).performClick()
        screen.waitForEnabled(text(R.string.plans_swap_take))
        screen.onNodeWithText(text(R.string.plans_swap_take)).performClick()
        screen.waitUntil(20_000) {
            screen.onAllNodesWithText(text(R.string.plans_swap_offer_title)).fetchSemanticsNodes().isEmpty()
        }

        assertNotEquals(before.first(), Instance.firstDayRecipes(account, planId).first())
        screen.keepScreenshot("plans-after-the-swap")

        // The meal as planned opens on a tap: its own lines, and its recipe a tap further.
        val (title, lines) = Instance.firstMeal(account, planId)
        screen.waitForText(title)
        screen.onAllNodesWithText(title)[0].performClick()
        screen.waitForText(lines.first())
        screen.keepScreenshot("plans-a-meal-as-planned")
        screen.onNodeWithText(text(R.string.plans_open_recipe)).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_open_recipe)).fetchSemanticsNodes().isEmpty() }
        screen.waitForText(title)
    }
}
