// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * A profile's pantry, as an account holder keeps it: a row going soon said so, an ingredient found
 * in the catalogue and put in, more of it than a row holds refused in the API's words, an amount
 * and a day changed where they are shown, and a row taken out. What the screen did is read back
 * from the API.
 */
@RunWith(AndroidJUnit4::class)
class PantryTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** Presses what [button] finds once it can be pressed: a row's buttons wait while a change is on its way. */
    private fun press(button: SemanticsMatcher) {
        screen.scrollTo(button)
        screen.waitUntil(20_000) { screen.onAllNodes(button and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        screen.onNode(button).performScrollTo().performClick()
    }

    @Test
    fun anIngredientIsPutInItsAmountAndAnotherRowsDayChangedAndTheRowTakenOut() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val language = text(R.string.api_language)
        val (garlicId, garlic) = Instance.ingredient(account, "Garlic", language)
        val (eggId, egg) = Instance.ingredient(account, "Large egg", language)
        Instance.stock(account, profileId, garlicId, 3.0, "piece", LocalDate.now().plusDays(1))
        screen.startSignedIn(account)

        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.pantry_title)).performClick()
        // The garlic, counted in cloves, is best eaten tomorrow.
        screen.scrollToText(plural(R.plurals.amount_clove, 3, "3"))
        screen.scrollToText(text(R.string.pantry_expiring_soon))
        screen.keepScreenshot("pantry-a-row-going-soon")

        // The egg found by a part of its name, and put in in pieces, as it is counted: more than a
        // row holds is refused in the API's words, and the form keeps it to be put right.
        screen.scrollToText(text(R.string.pantry_ingredient))
        screen.onNodeWithText(text(R.string.pantry_ingredient)).performScrollTo().performTextInput(egg.take(4))
        screen.putKeyboardAway()
        val found = hasText(egg) and hasClickAction()
        screen.scrollTo(found)
        screen.onNode(found).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.pantry_quantity)).performScrollTo().performTextInput("2000")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.pantry_add)).performScrollTo().performClick()
        screen.scrollToText("${text(R.string.pantry_err_add)} ${text(R.string.error_code_quantity_too_large)}")
        screen.keepScreenshot("pantry-more-than-a-row-holds")

        screen.onNodeWithText(text(R.string.pantry_quantity)).performScrollTo().performTextReplacement("6")
        screen.putKeyboardAway()
        screen.waitForEnabled(text(R.string.pantry_add))
        screen.onNodeWithText(text(R.string.pantry_add)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.pantry(account, profileId)[eggId]?.let { it.first == 6.0 && it.second == "piece" } == true }
        screen.scrollToText(plural(R.plurals.amount_piece, 6, "6"))
        screen.keepScreenshot("pantry-two-rows")

        // The egg's amount changed in the fields that open under it.
        press(hasContentDescription(text(R.string.pantry_change_of, egg)))
        val eggAmount = hasContentDescription(text(R.string.pantry_quantity_of, egg))
        screen.scrollTo(eggAmount)
        screen.onNode(eggAmount).performScrollTo().performTextReplacement("4")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.common_save)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.pantry(account, profileId)[eggId]?.first == 4.0 }

        // The garlic's day taken away.
        press(hasContentDescription(text(R.string.pantry_change_of, garlic)))
        screen.onNodeWithText(text(R.string.common_clear)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.common_save)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.pantry(account, profileId)[garlicId]?.let { it.third == null } == true }

        // The egg's row taken out.
        press(hasContentDescription(text(R.string.pantry_remove_of, egg)))
        screen.waitForTheApi { eggId !in Instance.pantry(account, profileId) }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(egg).fetchSemanticsNodes().isEmpty() }
    }
}
