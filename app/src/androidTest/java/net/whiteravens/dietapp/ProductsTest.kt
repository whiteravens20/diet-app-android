// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
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

        screen.onNode(hasText(text(R.string.products_edit)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.products_edit_title))
        type(R.string.products_brand, "Babcia")
        save()
        screen.waitUntil(20_000) { screen.onAllNodes(hasText(text(R.string.products_edit_title))).fetchSemanticsNodes().isEmpty() }
        screen.waitForText("Babcia")

        screen.onNode(hasText(text(R.string.products_remove)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.products_remove_note))
        screen.keepScreenshot("products-a-word-before-removing")
        // The word that answers the question stands under the button that asked it.
        screen.onAllNodes(hasText(text(R.string.products_remove_confirm)) and hasClickAction()).onLast().performClick()
        screen.waitForText(text(R.string.products_empty_title))
    }

    @Test
    fun aCodeTheBaseKnowsShowsWhatItHoldsAndLeadsToOneWithoutACode() {
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

        screen.waitForText("Mleko próbne 3,2%")
        screen.keepScreenshot("products-a-code-the-base-knows")
        screen.onNode(inTheWindow(text(R.string.products_without_code))).performScrollTo().performClick()
        save()

        screen.waitForText("Moje mleko")
        screen.waitForText(text(R.string.products_send_needs))
    }
}
