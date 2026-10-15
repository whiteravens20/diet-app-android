// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.clickFirst
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipesTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun aRecipeIsFoundBySearchAndOpenedWithItsIngredients() {
        val account = Instance.newAccount()
        val (title, ingredient) = Instance.aRecipe(account, text(R.string.api_language))
        screen.signIn(account)

        screen.openTab(text(R.string.nav_recipes))
        screen.onNodeWithText(text(R.string.recipes_search_placeholder)).performTextInput(title)

        // The recipe's card, not the search field that holds its title too. The cards are below the
        // tabs, the search and the filters: with the keyboard up they may begin below its edge.
        val card = hasText(title) and hasClickAction() and !hasSetTextAction()
        val list = screen.onNode(hasScrollToNodeAction())
        screen.waitUntil(20_000) { runCatching { list.performScrollToNode(card) }.isSuccess }
        screen.keepScreenshot("recipes-found-by-search")
        screen.clickFirst(card)

        screen.waitForText(text(R.string.recipes_ingredients))
        screen.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(ingredient))
        screen.waitForText(ingredient)
        screen.keepScreenshot("recipes-a-recipe")
    }

    @Test
    fun theLibraryIsNarrowedInItsWindowOfFiltersAndOnesOwnRecipesHaveATabOfTheirOwn() {
        val account = Instance.newAccount()
        Instance.newProfile(account)
        screen.signIn(account)
        screen.openTab(text(R.string.nav_recipes))

        // The filters open in a window of their own and say, once applied, how many are at work.
        screen.onNodeWithText(text(R.string.recipes_filters)).performClick()
        screen.onNodeWithText(text(R.string.difficulty_easy)).performScrollTo().performClick()
        screen.keepScreenshot("recipes-the-filters")
        screen.onNodeWithText(text(R.string.recipes_filters_apply)).performScrollTo().performClick()
        screen.waitForText(plural(R.plurals.recipes_filters_on, 1, 1))
        screen.onNodeWithText(text(R.string.recipes_clear_filters)).performClick()
        screen.waitForText(text(R.string.recipes_filters))

        // A new account has no recipes of its own.
        screen.onNodeWithText(text(R.string.recipes_tab_mine)).performClick()
        screen.waitForText(text(R.string.recipes_mine_empty_title))
    }
}
