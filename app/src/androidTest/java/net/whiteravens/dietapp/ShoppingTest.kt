// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun aListIsMadeForThePlanAndARowTickedIsTickedOnTheServer() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId)
        val language = text(R.string.api_language)
        screen.signIn(account)

        screen.onNodeWithText(text(R.string.nav_shopping)).performClick()
        screen.waitForEnabled(text(R.string.shopping_generate))
        screen.onNode(hasText(text(R.string.shopping_generate)) and hasClickAction()).performClick()

        // The list the server made, row by row.
        screen.waitUntil(30_000) { screen.onAllNodes(isToggleable()).fetchSemanticsNodes().isNotEmpty() }
        val rows = Instance.shoppingRows(account, planId, language)
        val name = rows.keys.first()
        assertEquals(false, rows[name])
        screen.keepScreenshot("shopping-a-list-made")

        val row = hasText(name) and isToggleable()
        screen.onNode(hasScrollToNodeAction()).performScrollToNode(row)
        screen.onNode(row).performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(row and isOn()).fetchSemanticsNodes().isNotEmpty() }

        screen.onNode(row).assertIsOn()
        assertEquals(true, Instance.shoppingRows(account, planId, language)[name])
        screen.keepScreenshot("shopping-a-row-ticked")
    }
}
