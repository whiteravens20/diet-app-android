// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.airplaneMode
import net.whiteravens.dietapp.testing.joined
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.opensSignedInTheBrowser
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.shown
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.ui.common.formatDayMonth
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ShoppingTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @After
    fun backOnline() = screen.airplaneMode(false)

    @Test
    fun aRowTickedWithoutANetworkWaitsAndIsTickedOnTheServerOnceTheNetworkIsBack() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId)
        val language = text(R.string.api_language)
        screen.signIn(account)

        screen.openTab(text(R.string.nav_shopping))
        screen.waitForEnabled(text(R.string.shopping_make_list))
        screen.onNode(hasText(text(R.string.shopping_make_list)) and hasClickAction()).performClick()
        val list = screen.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        screen.waitUntil(30_000) { runCatching { list.performScrollToNode(isToggleable()) }.isSuccess }
        val name = Instance.shoppingRows(account, planId, language).keys.first()
        val row = hasText(shown(name)) and isToggleable()

        // Without a network the row is ticked all the same, and says that it waits to be sent; the
        // screen says it is offline, and how many changes wait.
        screen.airplaneMode(true)
        list.performScrollToNode(row)
        screen.onNode(row).performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(row and isOn()).fetchSemanticsNodes().isNotEmpty() }
        screen.waitForText(text(R.string.change_waiting))
        val waiting = hasText(plural(R.plurals.changes_waiting, 1, 1), substring = true)
        screen.waitUntil(20_000) { runCatching { list.performScrollToNode(waiting) }.isSuccess }
        list.performScrollToNode(row)
        screen.keepScreenshot("shopping-a-row-ticked-offline")

        // With the network back, the change goes by itself, the row no longer waits, and the screen
        // is no longer offline.
        screen.airplaneMode(false)
        screen.waitForTheApi(90_000) { Instance.shoppingRows(account, planId, language)[name] == true }
        screen.waitUntil(30_000) { screen.onAllNodesWithText(text(R.string.change_waiting)).fetchSemanticsNodes().isEmpty() }
        screen.onNode(row).assertIsOn()
        list.performScrollToIndex(0)
        screen.waitUntil(10_000) { screen.onAllNodes(waiting).fetchSemanticsNodes().isEmpty() }
        list.performScrollToNode(row)
        screen.keepScreenshot("shopping-a-row-ticked-offline-sent")
    }

    @Test
    fun aHouseholdListIsMadeOfAPlanOfEachProfileSaysWhomEachRowIsForAndARowTickedIsTickedOnTheServer() {
        val account = Instance.newAccount()
        val ala = Instance.newProfile(account, name = "Ala")
        val ola = Instance.newProfile(account, name = "Ola")
        Instance.newPlan(account, ala)
        Instance.newPlan(account, ola)
        val language = text(R.string.api_language)
        screen.signIn(account)
        screen.openTab(text(R.string.nav_shopping))
        val list = screen.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))

        // Two profiles: the card of household lists, whose form begins with each profile's plan of the days chosen.
        screen.waitUntil(30_000) { runCatching { list.performScrollToNode(hasText(text(R.string.household_open))) }.isSuccess }
        screen.onNode(hasText(text(R.string.household_open)) and hasClickAction()).performClick()
        val make = hasText(text(R.string.household_make)) and hasClickAction()
        list.performScrollToNode(make)
        screen.waitUntil(30_000) { runCatching { screen.onNode(make).assertIsEnabled() }.isSuccess }
        screen.onNodeWithText(text(R.string.household_replaces)).performScrollTo()
        screen.keepScreenshot("shopping-a-household-list-asked-for")
        screen.onNode(make).performScrollTo().performClick()
        screen.waitForTheApi(60_000) { Instance.householdRows(account, language).isNotEmpty() }

        // Made and opened: for whom, and under each row what each profile needs of it.
        val forWhom = text(R.string.household_for_whom, joined("Ala", "Ola"))
        screen.waitUntil(30_000) { runCatching { list.performScrollToNode(hasText(shown(forWhom))) }.isSuccess }
        screen.keepScreenshot("shopping-a-household-list")

        // A row ticked on it is ticked on the server.
        val name = Instance.householdRows(account, language).keys.first()
        val row = hasText(shown(name)) and isToggleable()
        list.performScrollToNode(row)
        // The household list stands above the plan's list, whose part may hold the same row.
        screen.onAllNodes(row)[0].performClick()
        screen.waitForTheApi(30_000) { Instance.householdRows(account, language)[name] == true }

        // Ala's plan's list says it is a part of the household list.
        screen.waitUntil(30_000) { runCatching { list.performScrollToNode(hasText(shown(text(R.string.shopping_part_of, joined("Ala", "Ola"))))) }.isSuccess }
        screen.keepScreenshot("shopping-a-part-of-a-household-list")
    }

    @Test
    fun aRowOfFreshMeatForALaterDaySaysWhichOfItsPacksToBuyLaterAndFromWhen() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.planWithChickenAtBothEnds(account, profileId)
        val (name, later) = checkNotNull(Instance.buyLater(account, planId, text(R.string.api_language)).entries.firstOrNull()) {
            "no row of the list says to buy a pack later"
        }.toPair()
        val (packs, from) = later
        screen.signIn(account)

        screen.openTab(text(R.string.nav_shopping))
        // The row of the week's list says it under its name, as the server counted it: the day in the app's language.
        val on = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getStringArray(R.array.shopping_buy_later_on)[LocalDate.parse(from).dayOfWeek.value - 1]
        val sentence = plural(R.plurals.shopping_buy_later, packs, packs, on, formatDayMonth(from))
        val list = screen.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        screen.waitUntil(30_000) { runCatching { list.performScrollToNode(hasText(shown(sentence))) }.isSuccess }
        screen.onNode(hasText(shown(name)) and isToggleable()).assertExists()
        screen.keepScreenshot("shopping-fresh-meat-bought-later")
    }

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

        val row = hasText(shown(name)) and isToggleable()
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

        // The list goes from its menu as a text to send; as a table or on paper it is the web
        // application's, whose page of the lists the menu opens in the browser.
        list.performScrollToNode(hasContentDescription(text(R.string.shopping_list_options)))
        screen.onNodeWithContentDescription(text(R.string.shopping_list_options)).performClick()
        screen.onNodeWithText(text(R.string.shopping_share_text)).assertExists()
        screen.keepScreenshot("shopping-the-list-menu")
        opensSignedInTheBrowser("/shopping-lists") {
            screen.onNodeWithText(text(R.string.web_table_and_print)).performClick()
        }

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
