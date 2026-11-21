// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.airplaneMode
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import net.whiteravens.dietapp.ui.components.DOT
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The recipes a profile marked, as an account holder keeps them: narrowed by how they are marked,
 * and each recipe's mark changed, a tag put on and the mark taken off where it is shown, without a
 * network as well. What the screen did is read back from the API.
 */
@RunWith(AndroidJUnit4::class)
class FavoritesTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @After
    fun backOnline() = screen.airplaneMode(false)

    /** Picks [option] in the list a field of choices opened. */
    /** Presses [words] in the window in front. */
    private fun inTheWindow(words: String) = screen.onNode(hasText(words) and hasAnyAncestor(isDialog())).performClick()

    /** Opens the window where the mark of the recipe called [title] is changed. */
    private fun openTheMarkOf(title: String) {
        val change = hasContentDescription(text(R.string.favorites_change_of, title)) and hasClickAction()
        screen.scrollTo(change)
        screen.onNode(change).performScrollTo().performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(hasText(title) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Closes the window in front. */
    private fun closeTheWindow() {
        inTheWindow(text(R.string.common_close))
        screen.waitUntil(20_000) { screen.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theMarkedRecipesAreNarrowedAndEachMarkIsChangedTaggedAndTakenOff() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val (first, second) = Instance.recipes(account, 2, text(R.string.api_language))
        Instance.mark(account, profileId, first.first, "favorite", listOf("quick"))
        Instance.mark(account, profileId, second.first, "often")
        screen.signIn(account)

        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.favorites_title)).performClick()
        // Under the filters, below the screen's edge at a large font.
        screen.scrollToText(plural(R.plurals.favorites_count, 2, 2))
        screen.scrollToText(first.second)
        screen.keepScreenshot("favorites-the-list")

        // Narrowed to the recipes eaten often in the window of filters, the favourite is not listed; and back.
        screen.onNode(hasText(text(R.string.recipes_filters)) and hasClickAction()).performScrollTo().performClick()
        inTheWindow(text(R.string.favorites_sentiment_often))
        screen.keepScreenshot("favorites-the-filters")
        inTheWindow(text(R.string.recipes_filters_apply))
        screen.scrollToText(plural(R.plurals.favorites_count, 1, 1))
        screen.scrollToText(second.second)
        assertTrue(screen.onAllNodesWithText(first.second).fetchSemanticsNodes().isEmpty())
        screen.onNode(hasText(plural(R.plurals.recipes_filters_on, 1, 1)) and hasClickAction()).assertExists()
        screen.onNode(hasText(text(R.string.recipes_clear_filters)) and hasClickAction()).performClick()
        screen.scrollToText(plural(R.plurals.favorites_count, 2, 2))

        // The favourite marked to avoid, in its window.
        openTheMarkOf(first.second)
        inTheWindow(text(R.string.favorites_sentiment_avoid))
        screen.waitForTheApi(20_000) { Instance.marks(account, profileId)[first.first]?.first == "avoid" }
        closeTheWindow()

        // A tag put on the other recipe in its window, once the change before is in: a mark on its way to the API
        // holds every change.
        openTheMarkOf(second.second)
        val secondTag = hasContentDescription(text(R.string.favorites_new_tag_of, second.second))
        screen.waitUntil(20_000) { screen.onAllNodes(secondTag and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        screen.onNode(secondTag).performTextInput("for later")
        screen.putKeyboardAway()
        screen.onNode(hasText(text(R.string.favorites_add_tag)) and isEnabled() and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi(20_000) { Instance.marks(account, profileId)[second.first]?.second == listOf("for later") }
        screen.keepScreenshot("favorites-a-mark-in-its-window")
        closeTheWindow()
        // The row says the mark and the tag.
        screen.scrollToText("${text(R.string.favorites_sentiment_often)}$DOT" + "for later")
        screen.keepScreenshot("favorites-changed")

        // The mark taken off the first recipe in its window, once the tag is in: the window closes with it.
        openTheMarkOf(first.second)
        val remove = hasContentDescription(text(R.string.favorites_remove_of, first.second))
        screen.waitUntil(20_000) { screen.onAllNodes(remove and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        screen.onNode(remove).performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        screen.keepScreenshot("favorites-a-mark-taken-off")
        screen.waitForTheApi(20_000) { first.first !in Instance.marks(account, profileId) }
        screen.scrollToText(plural(R.plurals.favorites_count, 1, 1))
    }

    @Test
    fun aMarkChangedAndOneTakenOffWithoutANetworkWaitAndAreOnTheServerOnceTheNetworkIsBack() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val (first, second) = Instance.recipes(account, 2, text(R.string.api_language))
        Instance.mark(account, profileId, first.first, "favorite", listOf("quick"))
        Instance.mark(account, profileId, second.first, "often")
        screen.signIn(account)
        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.favorites_title)).performClick()
        // Under the filters, below the screen's edge at a large font.
        screen.scrollToText(plural(R.plurals.favorites_count, 2, 2))

        // Without a network the first is marked to avoid all the same, and says that the change waits
        // to be sent; the second's mark taken off leaves the list.
        screen.airplaneMode(true)
        openTheMarkOf(first.second)
        inTheWindow(text(R.string.favorites_sentiment_avoid))
        closeTheWindow()
        screen.waitForText(text(R.string.change_waiting))
        openTheMarkOf(second.second)
        val remove = hasContentDescription(text(R.string.favorites_remove_of, second.second))
        screen.waitUntil(20_000) { screen.onAllNodes(remove and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        screen.onNode(remove).performClick()
        screen.scrollToText(plural(R.plurals.favorites_count, 1, 1))
        val firstMark = hasContentDescription(text(R.string.favorites_change_of, first.second))
        screen.scrollTo(firstMark)
        screen.keepScreenshot("favorites-changes-waiting")
        val waiting = hasText(plural(R.plurals.changes_waiting, 2, 2), substring = true)
        screen.scrollTo(waiting)

        // With the network back, both go by themselves in their turn, and the list is read again.
        screen.airplaneMode(false)
        screen.waitForTheApi(90_000) { Instance.marks(account, profileId).let { it[first.first]?.first == "avoid" && second.first !in it } }
        screen.waitUntil(30_000) { screen.onAllNodes(waiting).fetchSemanticsNodes().isEmpty() }
        screen.scrollTo(firstMark)
        screen.waitUntil(30_000) { screen.onAllNodesWithText(text(R.string.change_waiting)).fetchSemanticsNodes().isEmpty() }
        screen.keepScreenshot("favorites-changes-sent")
    }
}
