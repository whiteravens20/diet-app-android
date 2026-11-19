// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
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
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.After
import org.junit.Assert.assertEquals
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
    /**
     * An amount in pieces as a row the server answered shows it, with what it weighs: a clove of
     * garlic is 5 g and an egg 55 g in the catalogue. A row that waits to be sent says no weight.
     */
    private fun weighing(amount: String, grams: Int) = text(R.string.amount_with_grams, amount, text(R.string.amount_g, grams.toString()))

    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @After
    fun backOnline() = screen.airplaneMode(false)

    /** A button of a window over the pantry. */
    private fun inTheWindow(words: String): SemanticsMatcher = hasText(words) and hasClickAction() and hasAnyAncestor(isDialog())

    /** Opens the window things are put in with, from its button over the rows. */
    private fun openTheWindow() {
        val add = hasText(text(R.string.pantry_add)) and hasClickAction() and !hasAnyAncestor(isDialog())
        screen.scrollTo(add)
        screen.onNode(add).performScrollTo().performClick()
        screen.waitForText(text(R.string.pantry_add_title))
    }

    /** Finds [name] in the window's search by a part of it, and picks it. */
    private fun pick(name: String) {
        screen.onNode(hasText(text(R.string.pantry_ingredient)) and hasSetTextAction()).performTextInput(name.take(4))
        val found = hasText(name) and hasClickAction() and hasAnyAncestor(isDialog())
        screen.waitUntil(20_000) { screen.onAllNodes(found).fetchSemanticsNodes().isNotEmpty() }
        screen.putKeyboardAway()
        screen.onNode(found).performScrollTo().performClick()
    }

    /** The window's field of [label]. */
    private fun field(label: Int): SemanticsMatcher = hasText(text(label)) and hasSetTextAction() and hasAnyAncestor(isDialog())

    /** Puts in what the window holds, once that can be done: its button stands under the window's fields, always in sight. */
    private fun putIn() {
        val add = inTheWindow(text(R.string.pantry_add))
        screen.waitUntil(20_000) { screen.onAllNodes(add and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        screen.onNode(add).performClick()
    }

    /** Closes the window by its button: the rows are there again. */
    private fun closeTheWindow() {
        screen.onNode(inTheWindow(text(R.string.common_close))).performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(hasText(text(R.string.pantry_add_title))).fetchSemanticsNodes().isEmpty() }
    }

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
        screen.scrollToText(weighing(plural(R.plurals.amount_clove, 3, "3"), 15))
        screen.scrollToText(text(R.string.pantry_expiring_soon))
        screen.keepScreenshot("pantry-a-row-going-soon")

        // The egg found by a part of its name in the window the button over the rows opens, and put in
        // in pieces, as it is counted: more than a row holds is refused in the API's words, in the
        // window, and the form keeps it to be put right.
        openTheWindow()
        pick(egg)
        screen.onNode(field(R.string.pantry_quantity)).performScrollTo().performTextInput("2000")
        screen.putKeyboardAway()
        putIn()
        screen.waitForText("${text(R.string.pantry_err_add)} ${text(R.string.error_code_quantity_too_large)}")
        screen.keepScreenshot("pantry-more-than-a-row-holds")

        screen.onNode(field(R.string.pantry_quantity)).performScrollTo().performTextReplacement("6")
        screen.putKeyboardAway()
        putIn()
        screen.waitForTheApi { Instance.pantry(account, profileId)[eggId]?.let { it.first == 6.0 && it.second == "piece" } == true }
        // The window stays for the next, empty, and says what went in; its button closes it.
        screen.waitForText(text(R.string.pantry_added, egg, plural(R.plurals.amount_piece, 6, "6")))
        screen.onNode(field(R.string.pantry_quantity)).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        screen.keepScreenshot("pantry-the-window-after-something-went-in")
        closeTheWindow()
        screen.scrollToText(weighing(plural(R.plurals.amount_piece, 6, "6"), 330))
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
        screen.keepScreenshot("pantry-a-row-taken-out")
        screen.waitForTheApi { eggId !in Instance.pantry(account, profileId) }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(egg).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aFoodIsPutInFrozenInPortionsAndAFreshRowIsPutIntoTheFreezerFromItsWindowEachPackageDatedThirtyDaysOn() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val language = text(R.string.api_language)
        val (milkId, milk) = Instance.ingredient(account, "Whole milk", language)
        val (eggId, egg) = Instance.ingredient(account, "Large egg", language)
        Instance.stock(account, profileId, milkId, 1000.0, "ml", LocalDate.now().plusDays(3))
        val thirtyDaysOn = LocalDate.now().plusDays(30).toString()
        screen.startSignedIn(account)

        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.pantry_title)).performClick()
        screen.scrollToText(milk)

        // Six eggs put in with the freezer's box ticked, which dates them 30 days on, in four portions as even as they go.
        openTheWindow()
        pick(egg)
        screen.onNode(field(R.string.pantry_quantity)).performScrollTo().performTextInput("6")
        screen.putKeyboardAway()
        screen.onNode(hasText(text(R.string.pantry_frozen_box)) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        val portions = field(R.string.pantry_portions)
        screen.onNode(portions).performScrollTo().performTextReplacement("4")
        screen.putKeyboardAway()
        // The field and the line under it that says what each package holds.
        screen.onNode(portions).performScrollTo()
        screen.keepScreenshot("pantry-put-in-frozen-in-portions")
        putIn()
        screen.waitForTheApi { Instance.frozenPackages(account, profileId)[eggId]?.map { it.first }?.sorted() == listOf(1.0, 1.0, 2.0, 2.0) }
        assertEquals(setOf(thirtyDaysOn), Instance.frozenPackages(account, profileId).getValue(eggId).map { it.second }.toSet())
        screen.waitForText(plural(R.plurals.pantry_added_portions, 4, egg, plural(R.plurals.amount_piece, 6, "6"), 4))
        closeTheWindow()

        // The milk's fresh row put into the freezer from the window its button opens, in three portions of whole
        // millilitres, the larger first.
        press(hasContentDescription(text(R.string.pantry_freeze_of, milk)))
        screen.onNode(hasText(text(R.string.pantry_portions)) and hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("3")
        screen.putKeyboardAway()
        screen.keepScreenshot("pantry-the-freezing-window")
        screen.onNode(hasText(text(R.string.pantry_freeze)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi { Instance.frozenPackages(account, profileId)[milkId]?.size == 3 }
        assertEquals(
            listOf(333.0 to thirtyDaysOn, 333.0 to thirtyDaysOn, 334.0 to thirtyDaysOn),
            Instance.frozenPackages(account, profileId).getValue(milkId).sortedBy { it.first },
        )
        screen.waitUntil(20_000) {
            screen.onAllNodes(hasContentDescription(text(R.string.pantry_freeze_of, milk))).fetchSemanticsNodes().isEmpty()
        }
        screen.scrollToText(milk)
        screen.keepScreenshot("pantry-packages")
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
        screen.scrollToText(weighing(plural(R.plurals.amount_clove, 3, "3"), 15))

        // The egg found while there is a network, and put in once there is none: the window says it
        // went in, and it is shown among the rows, saying that it waits to be sent.
        openTheWindow()
        pick(egg)
        screen.onNode(field(R.string.pantry_quantity)).performScrollTo().performTextInput("6")
        screen.putKeyboardAway()
        screen.airplaneMode(true)
        putIn()
        screen.waitForText(text(R.string.pantry_added, egg, plural(R.plurals.amount_piece, 6, "6")))
        closeTheWindow()
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
        screen.scrollToText(weighing(plural(R.plurals.amount_piece, 6, "6"), 330))
        screen.waitUntil(30_000) { screen.onAllNodesWithText(text(R.string.change_waiting)).fetchSemanticsNodes().isEmpty() }
        screen.keepScreenshot("pantry-changes-sent")
    }
}
