// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The page of the account's own products: a product written, changed and removed, and a code the base knows. */
@RunWith(AndroidJUnit4::class)
class ProductsTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** What is clickable in the window in front: the same words may stand on the screen under it. */
    private fun inTheWindow(words: String): SemanticsMatcher = hasText(words) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun openTheProducts() {
        screen.openTab(text(R.string.nav_more))
        // The entry stands there once the instance has said that it has products.
        screen.waitForText(text(R.string.products_title))
        screen.onNodeWithText(text(R.string.products_title)).performScrollTo().performClick()
        screen.waitForText(text(R.string.products_subhead))
    }

    /** Opens the menu of the product called [name], which holds what the card does beside changing it. */
    private fun openTheMenuOf(name: String) {
        val options = hasContentDescription(text(R.string.plans_action_for, text(R.string.products_options), name)) and hasClickAction()
        screen.onNode(options).performScrollTo().performClick()
    }

    /** What the open menu offers in [words]. */
    private fun inTheMenu(words: String): SemanticsMatcher = hasText(words) and hasAnyAncestor(isPopup())

    private fun type(label: Int, words: String) {
        screen.onNode(hasText(text(label)) and hasSetTextAction()).performScrollTo().performTextReplacement(words)
    }

    private fun save() {
        screen.putKeyboardAway()
        screen.onNode(inTheWindow(text(R.string.common_save))).performClick()
    }

    @Test
    fun aProductIsWrittenSaysWhatItLacksToBeSentIsChangedAndRemoved() {
        val account = Instance.newAccount()
        Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)

        openTheProducts()
        screen.waitForText(text(R.string.products_empty_title))
        // An account far from the most it may hold is told no limits; where the base comes from stands under the list.
        screen.onAllNodesWithText(text(R.string.products_limits, 500, 50, 10)).assertCountEquals(0)
        screen.scrollToText(text(R.string.products_source))
        val list = screen.onNodeWithText(text(R.string.products_empty_title)).fetchSemanticsNode().boundsInRoot
        val source = screen.onNodeWithText(text(R.string.products_source)).fetchSemanticsNode().boundsInRoot
        assertTrue("the source ($source) stands under the list ($list)", source.top >= list.bottom)
        screen.keepScreenshot("products-none-yet")

        screen.onNode(hasText(text(R.string.products_add)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.products_add_title))
        type(R.string.products_name, "Chleb babci")
        type(R.string.products_calories, "245")
        screen.keepScreenshot("products-the-form")
        save()

        screen.waitForText("Chleb babci")
        screen.waitForText(text(R.string.products_state_own))
        screen.waitForText(text(R.string.products_send_needs))
        screen.keepScreenshot("products-a-product")

        screen.onNode(hasText(text(R.string.products_edit)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForText(text(R.string.products_edit_title))
        type(R.string.products_brand, "Babcia")
        save()
        screen.waitUntil(20_000) { screen.onAllNodes(hasText(text(R.string.products_edit_title))).fetchSemanticsNodes().isEmpty() }
        screen.waitForText("Babcia")

        // Changing it is the card's one named action; removing it is in its menu.
        openTheMenuOf("Chleb babci")
        screen.keepScreenshot("products-the-card-menu")
        screen.onNode(inTheMenu(text(R.string.products_remove))).performClick()
        screen.waitForText(text(R.string.products_remove_note))
        screen.keepScreenshot("products-a-word-before-removing")
        // The word that answers the question stands under the button that asked it.
        screen.onAllNodes(hasText(text(R.string.products_remove_confirm)) and hasClickAction()).onLast().performScrollTo().performClick()
        // Under the page's head, below the screen's edge at a large font.
        screen.scrollToText(text(R.string.products_empty_title))
    }

    @Test
    fun aCodeTheBaseKnowsIsWrittenAsTheAccountsOwnCorrectionWhichStaysItsOwn() {
        val account = Instance.newAccount()
        Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)

        openTheProducts()
        screen.onNode(hasText(text(R.string.products_add)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.products_add_title))
        type(R.string.products_code, "5900512320359")
        type(R.string.products_name, "Moje mleko")
        type(R.string.products_calories, "60")
        save()

        screen.waitForText("Moje mleko")
        screen.waitForText(text(R.string.products_state_correction))
        screen.waitForText(text(R.string.products_correction_note))
        screen.keepScreenshot("products-a-correction")
        // A correction stays its author's: its menu offers nothing that sends it to the base.
        openTheMenuOf("Moje mleko")
        screen.onNode(inTheMenu(text(R.string.products_remove))).assertExists()
        screen.onAllNodes(inTheMenu(text(R.string.products_send))).assertCountEquals(0)
    }
}
