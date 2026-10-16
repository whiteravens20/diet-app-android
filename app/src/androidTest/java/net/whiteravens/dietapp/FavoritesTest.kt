// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The recipes a profile marked, as an account holder keeps them: narrowed by how they are marked,
 * and each recipe's mark changed, a tag put on and the mark taken off where it is shown. What the
 * screen did is read back from the API.
 */
@RunWith(AndroidJUnit4::class)
class FavoritesTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** Picks [option] in the list a field of choices opened. */
    private fun pick(option: String) = screen.onNode(hasText(option) and hasAnyAncestor(isPopup())).performClick()

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
        screen.waitForText(plural(R.plurals.favorites_count, 2, 2))
        screen.scrollToText(first.second)
        screen.keepScreenshot("favorites-the-list")

        // Narrowed to the recipes eaten often, the favourite is not listed; and back. The filter is
        // the field of marks that says no recipe's name.
        val markedAs = text(R.string.favorites_marked)
        val often = text(R.string.favorites_sentiment_often)
        val filter = hasText(markedAs) and !hasContentDescription(text(R.string.favorites_marked_as, second.second))
        val filterAny = filter and hasText(text(R.string.favorites_any))
        screen.scrollTo(filterAny)
        screen.onNode(filterAny).performScrollTo().performClick()
        pick(often)
        screen.waitForText(plural(R.plurals.favorites_count, 1, 1))
        screen.scrollToText(second.second)
        assertTrue(screen.onAllNodesWithText(first.second).fetchSemanticsNodes().isEmpty())
        val filterOften = filter and hasText(often)
        screen.scrollTo(filterOften)
        screen.onNode(filterOften).performScrollTo().performClick()
        pick(text(R.string.favorites_any))
        screen.waitForText(plural(R.plurals.favorites_count, 2, 2))

        // The favourite marked to avoid, in the field that names it.
        val firstMark = hasContentDescription(text(R.string.favorites_marked_as, first.second))
        screen.scrollTo(firstMark)
        screen.onNode(firstMark).performScrollTo().performClick()
        pick(text(R.string.favorites_sentiment_avoid))
        screen.waitUntil(20_000) { Instance.marks(account, profileId)[first.first]?.first == "avoid" }

        // A tag put on the other recipe, in the field that names it.
        val secondTag = hasContentDescription(text(R.string.favorites_new_tag_of, second.second))
        screen.scrollTo(secondTag)
        screen.onNode(secondTag).performScrollTo().performTextInput("for later")
        screen.onAllNodes(hasText(text(R.string.favorites_add_tag)) and isEnabled())[0].performScrollTo().performClick()
        screen.waitUntil(20_000) { Instance.marks(account, profileId)[second.first]?.second == listOf("for later") }
        screen.scrollToText("for later")
        screen.keepScreenshot("favorites-changed")

        // The mark taken off the first recipe.
        screen.onNodeWithContentDescription(text(R.string.favorites_remove_of, first.second)).performScrollTo().performClick()
        screen.waitUntil(20_000) { first.first !in Instance.marks(account, profileId) }
        screen.waitForText(plural(R.plurals.favorites_count, 1, 1))
    }
}
