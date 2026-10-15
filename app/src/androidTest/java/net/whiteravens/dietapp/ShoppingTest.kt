// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
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
        screen.waitForEnabled(text(R.string.shopping_make_list))
        screen.onNode(hasText(text(R.string.shopping_make_list)) and hasClickAction()).performClick()

        // The list the server made, row by row.
        screen.waitUntil(30_000) { screen.onAllNodes(isToggleable()).fetchSemanticsNodes().isNotEmpty() }
        val rows = Instance.shoppingRows(account, planId, language)
        val name = rows.keys.first()
        assertEquals(false, rows[name])
        screen.keepScreenshot("shopping-a-list-made")

        val row = hasText(name) and isToggleable()
        // The list itself, not the rows of profiles and plans above it, which scroll sideways.
        val list = screen.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        list.performScrollToNode(row)
        screen.onNode(row).performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(row and isOn()).fetchSemanticsNodes().isNotEmpty() }

        screen.onNode(row).assertIsOn()
        assertEquals(true, Instance.shoppingRows(account, planId, language)[name])
        screen.keepScreenshot("shopping-a-row-ticked")

        // What is at home of another row is told in the amounts that open under it, and the server counts it.
        val other = rows.keys.elementAt(1)
        val amounts = hasContentDescription(text(R.string.shopping_amounts_of, other))
        list.performScrollToNode(amounts)
        screen.onNode(amounts).performClick()
        screen.onNodeWithText(text(R.string.shopping_at_home)).performTextReplacement("1")
        screen.keepScreenshot("shopping-the-amounts-of-a-row")
        screen.onNodeWithText(text(R.string.common_save)).performClick()
        screen.waitUntil(20_000) { Instance.shoppingRowAtHome(account, planId, other, language) == 1.0 }

        // A list made again from the window that asks for the plan's days stands in the place of the first.
        val first = Instance.shoppingListIds(account, planId)
        list.performScrollToNode(hasText(text(R.string.shopping_generate_title)))
        screen.onNodeWithText(text(R.string.shopping_generate_title)).performClick()
        screen.waitForText(text(R.string.shopping_generate_button))
        screen.keepScreenshot("shopping-the-days-of-a-list")
        screen.onNodeWithText(text(R.string.shopping_generate_button)).performClick()
        screen.waitUntil(30_000) { Instance.shoppingListIds(account, planId).let { it.size == 1 && it != first } }

        // The list is deleted from its menu once that is confirmed: the plan has none left.
        list.performScrollToNode(hasContentDescription(text(R.string.shopping_list_options)))
        screen.onNodeWithContentDescription(text(R.string.shopping_list_options)).performClick()
        screen.onNodeWithText(text(R.string.common_delete)).performClick()
        screen.waitForText(text(R.string.shopping_delete_confirm))
        screen.onNode(hasText(text(R.string.common_delete)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitUntil(20_000) { Instance.shoppingListIds(account, planId).isEmpty() }
        screen.waitForText(text(R.string.shopping_no_lists_title))
    }
}
