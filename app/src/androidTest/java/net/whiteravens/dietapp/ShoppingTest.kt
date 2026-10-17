// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.print.PrintManager
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.putAwayPrintService
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

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

        screen.openTab(text(R.string.nav_shopping))
        screen.waitForEnabled(text(R.string.shopping_make_list))
        screen.onNode(hasText(text(R.string.shopping_make_list)) and hasClickAction()).performClick()

        // The list the server made, row by row, under what the list says of itself: on a small screen
        // the rows begin below its edge, where a list composes nothing until it is scrolled to.
        // The list itself is scrolled, not the row of plans above it, which scrolls sideways.
        val list = screen.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        screen.waitUntil(30_000) { runCatching { list.performScrollToNode(isToggleable()) }.isSuccess }
        val rows = Instance.shoppingRows(account, planId, language)
        val name = rows.keys.first()
        assertEquals(false, rows[name])
        screen.keepScreenshot("shopping-a-list-made")

        val row = hasText(name) and isToggleable()
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
        screen.putKeyboardAway()
        // The amounts open under the row, which may reach below the screen's edge.
        screen.onNodeWithText(text(R.string.common_save)).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.shoppingRowAtHome(account, planId, other, language) == 1.0 }

        // The entries are found by a part of the name: the rows shown are those whose name holds it.
        list.performScrollToNode(hasText(text(R.string.shopping_find_entry)))
        screen.onNodeWithText(text(R.string.shopping_find_entry)).performTextInput(other)
        screen.putKeyboardAway()
        screen.waitUntil(20_000) {
            val shown = screen.onAllNodes(isToggleable()).fetchSemanticsNodes()
            shown.isNotEmpty() && shown.all { node -> node.config[SemanticsProperties.Text].any { other.lowercase() in it.text.lowercase() } }
        }
        screen.onNode(hasText(other) and isToggleable()).assertExists()
        screen.onNodeWithText(text(R.string.shopping_find_entry)).performTextReplacement("")

        // What the list could do without is what the API finds for it, and each leads to the plan.
        val proposals = Instance.listProposals(account, Instance.shoppingListIds(account, planId).first())
        list.performScrollToNode(hasText(text(R.string.shopping_proposals_ask)))
        screen.onNodeWithText(text(R.string.shopping_proposals_ask)).performClick()
        val ways = hasText(text(R.string.shopping_proposal_merge_how)) or hasText(text(R.string.shopping_proposal_leftover_how))
        if (proposals == 0) {
            screen.waitUntil(30_000) { screen.onAllNodes(hasText(text(R.string.shopping_proposals_none))).fetchSemanticsNodes().isNotEmpty() }
        } else {
            screen.waitUntil(30_000) { screen.onAllNodes(ways).fetchSemanticsNodes().size == proposals }
            screen.keepScreenshot("shopping-what-a-list-could-do-without")
            list.performScrollToNode(ways)
            screen.onAllNodes(ways)[0].performClick()
            screen.waitUntil(20_000) { screen.onAllNodes(hasContentDescription(text(R.string.plans_options))).fetchSemanticsNodes().isNotEmpty() }
            screen.onNodeWithContentDescription(text(R.string.common_back)).performClick()
            screen.waitUntil(20_000) { screen.onAllNodes(hasContentDescription(text(R.string.plans_options))).fetchSemanticsNodes().isEmpty() }
        }

        // The list goes from its menu to the platform's print service, which prints it or keeps it as a
        // PDF named by its days; it is put away there, back to the list. Sending it as a text and saving
        // it as a table are in the menu too.
        val (start, days) = Instance.planStart(account, planId)
        val sheet = "shopping-list-${start}_${LocalDate.parse(start).plusDays(days - 1L)}"
        val printing = screen.activity.getSystemService(PrintManager::class.java)
        list.performScrollToNode(hasContentDescription(text(R.string.shopping_list_options)))
        screen.onNodeWithContentDescription(text(R.string.shopping_list_options)).performClick()
        screen.onNodeWithText(text(R.string.shopping_share_text)).assertExists()
        screen.onNodeWithText(text(R.string.shopping_export_csv)).assertExists()
        screen.onNodeWithText(text(R.string.shopping_export_print)).performClick()
        screen.waitUntil(30_000) { printing.printJobs.any { it.info.label == sheet } }
        screen.putAwayPrintService()

        // A list made again from the window that asks for the plan's days stands in the place of the first.
        val first = Instance.shoppingListIds(account, planId)
        list.performScrollToNode(hasText(text(R.string.shopping_generate_title)))
        screen.onNodeWithText(text(R.string.shopping_generate_title)).performClick()
        screen.waitForText(text(R.string.shopping_generate_button))
        screen.keepScreenshot("shopping-the-days-of-a-list")
        screen.onNodeWithText(text(R.string.shopping_generate_button)).performClick()
        screen.waitForTheApi(30_000) { Instance.shoppingListIds(account, planId).let { it.size == 1 && it != first } }

        // The list is deleted from its menu once that is confirmed: the plan has none left.
        list.performScrollToNode(hasContentDescription(text(R.string.shopping_list_options)))
        screen.onNodeWithContentDescription(text(R.string.shopping_list_options)).performClick()
        screen.onNodeWithText(text(R.string.common_delete)).performClick()
        screen.waitForText(text(R.string.shopping_delete_confirm))
        screen.onNode(hasText(text(R.string.common_delete)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi(20_000) { Instance.shoppingListIds(account, planId).isEmpty() }
        screen.waitForText(text(R.string.shopping_no_lists_title))
    }
}
