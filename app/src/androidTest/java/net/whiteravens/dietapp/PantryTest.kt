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
import net.whiteravens.dietapp.testing.airplaneMode
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * A profile's pantry, as an account holder keeps it: a row going soon said so, an ingredient found
 * in the catalogue and put in, more of it than a row holds refused in the API's words, an amount
 * and a day changed where they are shown, and a row taken out; without a network as well. What the
 * screen did is read back from the API.
 */
@RunWith(AndroidJUnit4::class)
class PantryTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @After
    fun backOnline() = screen.airplaneMode(false)

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

    @Test
    fun anIngredientPutInAndARowChangedWithoutANetworkWaitAndAreOnTheServerOnceTheNetworkIsBack() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val language = text(R.string.api_language)
        val (garlicId, garlic) = Instance.ingredient(account, "Garlic", language)
        val (eggId, egg) = Instance.ingredient(account, "Large egg", language)
        Instance.stock(account, profileId, garlicId, 3.0, "piece", LocalDate.now().plusDays(10))
        screen.startSignedIn(account)
        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.pantry_title)).performClick()
        screen.scrollToText(plural(R.plurals.amount_clove, 3, "3"))

        // The egg found while there is a network, and put in once there is none: it is shown among the
        // rows, and says that it waits to be sent.
        screen.scrollToText(text(R.string.pantry_ingredient))
        screen.onNodeWithText(text(R.string.pantry_ingredient)).performScrollTo().performTextInput(egg.take(4))
        screen.putKeyboardAway()
        val found = hasText(egg) and hasClickAction()
        screen.scrollTo(found)
        screen.onNode(found).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.pantry_quantity)).performScrollTo().performTextInput("6")
        screen.putKeyboardAway()
        screen.airplaneMode(true)
        screen.onNodeWithText(text(R.string.pantry_add)).performScrollTo().performClick()
        screen.scrollToText(plural(R.plurals.amount_piece, 6, "6"))
        screen.waitForText(text(R.string.change_waiting))

        // The garlic's amount changed behind it, and waiting too; the pantry says it is offline, and how
        // many changes wait.
        press(hasContentDescription(text(R.string.pantry_change_of, garlic)))
        val garlicAmount = hasContentDescription(text(R.string.pantry_quantity_of, garlic))
        screen.scrollTo(garlicAmount)
        screen.onNode(garlicAmount).performScrollTo().performTextReplacement("2")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.common_save)).performScrollTo().performClick()
        screen.scrollToText(plural(R.plurals.amount_clove, 2, "2"))
        screen.keepScreenshot("pantry-changes-waiting")
        val waiting = hasText(plural(R.plurals.changes_waiting, 2, 2), substring = true)
        screen.scrollTo(waiting)

        // With the network back, both go by themselves in their turn, and the pantry is read again.
        screen.airplaneMode(false)
        screen.waitForTheApi(90_000) {
            Instance.pantry(account, profileId).let { it[eggId]?.first == 6.0 && it[garlicId]?.first == 2.0 }
        }
        screen.waitUntil(30_000) { screen.onAllNodes(waiting).fetchSemanticsNodes().isEmpty() }
        screen.scrollToText(plural(R.plurals.amount_piece, 6, "6"))
        screen.waitUntil(30_000) { screen.onAllNodesWithText(text(R.string.change_waiting)).fetchSemanticsNodes().isEmpty() }
        screen.keepScreenshot("pantry-changes-sent")
    }
}
