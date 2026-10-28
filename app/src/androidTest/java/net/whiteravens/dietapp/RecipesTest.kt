// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.clickFirst
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipesTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private fun type(label: String, value: String) = screen.onNodeWithText(label).performScrollTo().performTextInput(value)

    private fun press(label: String) {
        screen.putKeyboardAway()
        screen.onNodeWithText(label).performScrollTo().performClick()
    }

    /**
     * Finds [name] in the catalogue and puts it on a line of its own, [amount] of it. The whole name
     * is typed, so that it is among the few found that are offered; the field typed into, which
     * holds it too, is not what is picked.
     */
    private fun addIngredient(name: String, amount: String) {
        type(text(R.string.own_recipe_add_ingredient), name)
        screen.putKeyboardAway()
        val found = hasText(name) and hasClickAction() and !hasSetTextAction()
        screen.waitUntil(20_000) { screen.onAllNodes(found).fetchSemanticsNodes().isNotEmpty() }
        screen.onAllNodes(found)[0].performScrollTo().performClick()
        screen.onNodeWithContentDescription(text(R.string.own_recipe_amount_of, name)).performScrollTo().performTextInput(amount)
        screen.putKeyboardAway()
    }

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
        screen.putKeyboardAway()
        screen.clickFirst(card)

        screen.waitForText(text(R.string.recipes_ingredients))
        screen.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(ingredient))
        screen.waitForText(ingredient)
        screen.keepScreenshot("recipes-a-recipe")
    }

    @Test
    fun aRecipeIsMarkedForAProfileFromItsViewAndSoAreTheIngredientsOfItsLines() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val (recipeId, title) = Instance.recipes(account, 1, text(R.string.api_language)).single()
        screen.signIn(account)

        screen.openTab(text(R.string.nav_recipes))
        screen.onNodeWithText(text(R.string.recipes_search_placeholder)).performTextInput(title)
        val card = hasText(title) and hasClickAction() and !hasSetTextAction()
        val list = screen.onNode(hasScrollToNodeAction())
        screen.waitUntil(20_000) { runCatching { list.performScrollToNode(card) }.isSuccess }
        screen.putKeyboardAway()
        screen.clickFirst(card)

        // The profile's chip marks the recipe as a favourite, and takes the mark off again.
        screen.waitForText(text(R.string.recipe_save_to_favorites))
        val chip = hasText("Ala") and hasClickAction()
        screen.onNode(chip).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.marks(account, profileId)[recipeId]?.first == "favorite" }
        screen.keepScreenshot("recipes-marked-for-a-profile")
        screen.waitForEnabled("Ala")
        screen.onNode(chip).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { recipeId !in Instance.marks(account, profileId) }

        // The first line's ingredient marked as a favourite of the profile, then as one it avoids.
        val ingredient = Instance.recipeIngredients(account, recipeId).first()
        val favourite = text(R.string.recipe_mark_favorite)
        val avoid = text(R.string.recipe_mark_avoid)
        screen.onAllNodesWithContentDescription(favourite)[0].performScrollTo().performClick()
        screen.waitForTheApi(20_000) { ingredient in Instance.preferredIngredients(account, profileId, "favoriteIngredientIds") }
        screen.waitUntil(20_000) { screen.onAllNodes(hasContentDescription(avoid) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        screen.onAllNodesWithContentDescription(avoid)[0].performScrollTo().performClick()
        screen.waitForTheApi(20_000) {
            ingredient in Instance.preferredIngredients(account, profileId, "excludedIngredientIds") &&
                ingredient !in Instance.preferredIngredients(account, profileId, "favoriteIngredientIds")
        }
        screen.keepScreenshot("recipes-an-ingredient-avoided")
    }

    @Test
    fun theLibraryIsNarrowedInItsWindowOfFiltersAndOnesOwnRecipesHaveATabOfTheirOwn() {
        val account = Instance.newAccount()
        Instance.newProfile(account)
        screen.signIn(account)
        screen.openTab(text(R.string.nav_recipes))

        // A recipe drafted by a model waits for a model on the account: without one the button is
        // off and says why; with one it opens the window of choices.
        val draft = hasText(text(R.string.recipes_ai_draft)) and hasClickAction()
        screen.waitUntil(20_000) { screen.onAllNodes(draft).fetchSemanticsNodes().isNotEmpty() }
        if (Instance.aiMode(account) == "none") {
            screen.waitForText(text(R.string.ai_off_note, text(R.string.ai_off_turn_on)))
            screen.onNode(draft).assertIsNotEnabled()
        } else {
            screen.waitUntil(20_000) { screen.onAllNodes(draft and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            screen.onNode(draft).performScrollTo().performClick()
            screen.waitForText(text(R.string.draft_title))
            screen.onNodeWithText(text(R.string.common_close)).performClick()
        }

        // The filters open in a window of their own and say, once applied, how many are at work.
        screen.onNodeWithText(text(R.string.recipes_filters)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.difficulty_easy)).performScrollTo().performClick()
        screen.keepScreenshot("recipes-the-filters")
        screen.onNodeWithText(text(R.string.recipes_filters_apply)).performScrollTo().performClick()
        screen.waitForText(plural(R.plurals.recipes_filters_on, 1, 1))
        screen.onNodeWithText(text(R.string.recipes_clear_filters)).performScrollTo().performClick()
        screen.waitForText(text(R.string.recipes_filters))

        // A new account has no recipes of its own.
        screen.onNodeWithText(text(R.string.recipes_tab_mine)).performScrollTo().performClick()
        screen.waitForText(text(R.string.recipes_mine_empty_title))
    }

    @Test
    fun aRecipeIsWrittenByHandWithWhatAServingHoldsAndANewVersionOfItIsSavedFromItsView() {
        val account = Instance.newAccount()
        val language = text(R.string.api_language)
        val oats = Instance.ingredient(account, "Rolled oats", language).second
        val milk = Instance.ingredient(account, "Whole milk", language).second
        screen.signIn(account)
        screen.openTab(text(R.string.nav_recipes))

        screen.onNode(hasText(text(R.string.recipes_write)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForText(text(R.string.own_recipe_new_intro))
        type(text(R.string.own_recipe_field_description), "Oats cooked in milk.")
        press(text(R.string.meal_breakfast))
        addIngredient(oats, "60")
        addIngredient(milk, "250")
        type(text(R.string.own_recipe_step_number, 1), "Cook the oats in the milk for five minutes.")

        // What a serving holds is worked out as the recipe is written, and its allergens with it.
        val allergens = text(R.string.recipe_allergens)
        screen.waitForText(allergens, substring = true)
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.own_recipe_new_title)).performScrollTo()
        screen.keepScreenshot("recipes-written-by-hand")
        screen.onNodeWithText(allergens, substring = true).performScrollTo()
        screen.keepScreenshot("recipes-written-serving")

        // A recipe without a name is refused, and the field says why.
        press(text(R.string.own_recipe_save))
        screen.waitForText(text(R.string.refusal_empty))
        screen.waitForText("${text(R.string.own_recipe_failed)} ${text(R.string.own_recipe_fix_fields)}")
        screen.onNodeWithText(text(R.string.own_recipe_field_title)).performScrollTo()
        screen.keepScreenshot("recipes-written-without-a-name")
        type(text(R.string.own_recipe_field_title), "Oat porridge")
        press(text(R.string.own_recipe_save))

        // Saved, it opens as one of the account's own: its view, not the form, has the way to edit it.
        screen.waitForTheApi { Instance.ownRecipes(account).size == 1 }
        val written = Instance.ownRecipes(account).single().first
        val edit = hasContentDescription(text(R.string.own_recipe_edit_title))
        screen.waitUntil(20_000) { screen.onAllNodes(edit).fetchSemanticsNodes().isNotEmpty() }
        screen.waitForText("Oat porridge")

        // Edited from its view, it is saved as a new version in its place.
        screen.onNode(edit).performClick()
        screen.waitForText(text(R.string.own_recipe_edit_intro))
        // The form shows once the recipe is read.
        screen.waitForText(text(R.string.own_recipe_field_servings))
        screen.onNodeWithText(text(R.string.own_recipe_field_servings)).performScrollTo().performTextReplacement("2")
        press(text(R.string.own_recipe_save))
        screen.waitForTheApi { Instance.ownRecipes(account).singleOrNull()?.let { (id, servings) -> id != written && servings == 2 } == true }
        screen.waitUntil(20_000) { screen.onAllNodes(edit).fetchSemanticsNodes().isNotEmpty() }
        screen.keepScreenshot("recipes-written-and-edited")
    }
}
