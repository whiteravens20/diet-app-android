// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.opensSignedInTheBrowser
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.printedAs
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.sharedBy
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatDayDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PlansTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @Test
    fun aPlanIsMadeOpenedAndAMealSwappedForARecipeOffered() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        screen.signIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        screen.waitForEnabled(text(R.string.plans_generate))
        screen.onNode(hasText(text(R.string.plans_generate)) and hasClickAction()).performClick()

        // The new plan's form: three days, cooked ahead, four meals a day instead of the profile's own.
        screen.waitForText(text(R.string.plans_start_date))
        screen.onNodeWithText(text(R.string.plans_duration_days)).performTextReplacement("3")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.plans_meal_prep)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.plans_meals_a_day)).performScrollTo().performClick()
        screen.onNode(hasText("4") and hasClickAction()).performClick()
        // A box to tick is a finger high, whatever its label's length.
        screen.onNode(hasText(text(R.string.plans_honour_avoid)) and isToggleable()).performScrollTo().assertHeightIsAtLeast(48.dp)
        screen.keepScreenshot("plans-a-new-plan")
        screen.onNode(hasText(text(R.string.plans_generate_button)) and hasClickAction()).performScrollTo().performClick()

        // The plan is made on the server with what the form chose, and opens in its place.
        screen.waitForTheApi(60_000) { Instance.planIds(account, profileId).isNotEmpty() }
        val planId = Instance.planIds(account, profileId).first()
        val options = Instance.planOptions(account, planId)
        assertEquals(4, options.getInt("mealCount"))
        assertTrue(options.getBoolean("mealPrepFriendly"))
        assertEquals(3, Instance.planStart(account, planId).second)
        // The plan says what it is, on the profile's diet.
        screen.waitForText(plural(R.plurals.plans_plan_summary, 3, 3, text(R.string.diet_balanced)))
        screen.keepScreenshot("plans-a-plan-made")

        // The plan begins today: its day is marked so, and one press goes back to it from another day.
        val today = hasText(formatDayDate(LocalDate.now().toString())) and hasText(text(R.string.plans_today))
        screen.waitUntil(20_000) { screen.onAllNodes(today and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        screen.onNodeWithText(formatDayDate(LocalDate.now().plusDays(1).toString())).performScrollTo().performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(today and isSelected()).fetchSemanticsNodes().isEmpty() }
        screen.onNodeWithText(text(R.string.plans_go_to_today)).performScrollTo().performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(today and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        screen.keepScreenshot("plans-today")

        // The plan is made again from its facts: what would change is said before it is asked for.
        val made = Instance.planRevision(account, planId)
        screen.scrollToText(text(R.string.plans_recalculate))
        screen.onNodeWithText(text(R.string.plans_recalculate)).performClick()
        screen.waitForText(text(R.string.plans_reroll_title))
        // The button waits for the API's answer to the choice on the screen.
        screen.waitForEnabled(text(R.string.plans_reroll_confirm))
        screen.keepScreenshot("plans-make-again")
        screen.onNodeWithText(text(R.string.plans_reroll_confirm)).performClick()
        screen.waitForTheApi(60_000) { Instance.planRevision(account, planId) > made }

        // The first day is filled another way, from the ways its window offers.
        val remade = Instance.planRevision(account, planId)
        screen.scrollToText(text(R.string.plans_other_ways))
        screen.onNodeWithText(text(R.string.plans_other_ways)).performClick()
        screen.waitForText(text(R.string.plans_ways_take), timeoutMillis = 60_000)
        screen.keepScreenshot("plans-other-ways")
        screen.onAllNodesWithText(text(R.string.plans_ways_take))[0].performClick()
        screen.waitForTheApi(60_000) { Instance.planRevision(account, planId) > remade }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_ways_take)).fetchSemanticsNodes().isEmpty() }

        // Opened again on the day, the window offers ways anew and stays open: it does not hand back the way it took.
        screen.scrollToText(text(R.string.plans_other_ways))
        screen.onNodeWithText(text(R.string.plans_other_ways)).performClick()
        screen.waitForText(text(R.string.plans_ways_take), timeoutMillis = 60_000)
        screen.waitForIdle()
        assertTrue(screen.onAllNodes(hasText(text(R.string.common_close)) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty())
        screen.onNode(hasText(text(R.string.common_close)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_ways_take)).fetchSemanticsNodes().isEmpty() }

        // The first meal of the first day is swapped from the ways to change it, named by the meal:
        // one offer is put away for another, which is taken.
        val before = Instance.firstDayRecipes(account, planId)
        val change = text(R.string.plans_action_for, text(R.string.plans_change_meal), Instance.firstMeal(account, planId).first)
        screen.waitUntil(20_000) { screen.onAllNodesWithContentDescription(change).fetchSemanticsNodes().isNotEmpty() }
        screen.onAllNodesWithContentDescription(change)[0].performScrollTo().performClick()
        screen.waitForText(text(R.string.plans_tooltip_swap_by_fav))
        screen.keepScreenshot("plans-the-ways-to-change-a-meal")
        screen.onNodeWithText(text(R.string.plans_swap)).performClick()
        screen.waitForEnabled(text(R.string.plans_offer_take))
        screen.keepScreenshot("plans-a-swap-offered")
        screen.onNodeWithText(text(R.string.plans_offer_another)).performClick()
        screen.waitForEnabled(text(R.string.plans_offer_take))
        screen.onNodeWithText(text(R.string.plans_offer_take)).performClick()
        screen.waitUntil(20_000) {
            screen.onAllNodesWithText(text(R.string.plans_offer_title)).fetchSemanticsNodes().isEmpty()
        }

        assertNotEquals(before.first(), Instance.firstDayRecipes(account, planId).first())
        screen.keepScreenshot("plans-after-the-swap")
        // The meal that was changed stands where it stood.
        screen.onAllNodesWithText(Instance.firstMeal(account, planId).first)[0].assertIsDisplayed()
        // A swap says what its day holds in a card over the meals, and can be taken back from there.
        val swappedAt = Instance.planRevision(account, planId)
        screen.waitForText(text(R.string.plans_undo_swap))
        screen.onNodeWithText(text(R.string.plans_undo_swap)).performClick()
        screen.waitForTheApi(20_000) { Instance.planRevision(account, planId) > swappedAt }
        assertEquals(before, Instance.firstDayRecipes(account, planId))
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_undo_swap)).fetchSemanticsNodes().isEmpty() }

        // The meal as planned opens on a tap, over the whole screen: its lines and its steps, and its recipe a tap further.
        val (title, lines) = Instance.firstMeal(account, planId)
        screen.scrollToText(title)
        screen.onAllNodesWithText(title)[0].performScrollTo().performClick()
        screen.waitForText(lines.first())
        screen.onNode(hasText(text(R.string.recipes_steps)) and hasAnyAncestor(isDialog())).performScrollTo()
        screen.keepScreenshot("plans-a-meal-as-planned")
        screen.onNodeWithText(text(R.string.plans_open_recipe)).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_open_recipe)).fetchSemanticsNodes().isEmpty() }
        screen.waitForText(title)
    }


    @Test
    fun aPlanThatWastesLessCountsThePantryAndOnlyTheWholePlanMadeAgainChangesIt() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        screen.signIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        screen.waitForEnabled(text(R.string.plans_generate))
        screen.onNode(hasText(text(R.string.plans_generate)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.plans_start_date))
        screen.onNodeWithText(text(R.string.plans_duration_days)).performTextReplacement("3")
        screen.putKeyboardAway()

        // The pantry left aside, then a plan that wastes less: it counts the pantry, and holds its box ticked.
        val pantry = hasText(text(R.string.plans_prefer_inventory)) and isToggleable()
        val lessWaste = hasText(text(R.string.plans_less_waste)) and isToggleable()
        screen.onNode(pantry).performScrollTo().performClick()
        screen.onNode(pantry).assert(isOff())
        screen.onNode(lessWaste).performScrollTo().performClick()
        screen.onNode(pantry).assert(isOn()).assert(isNotEnabled())
        // What it costs, in the instance's figures for the profile's diet, the balanced one.
        screen.onNode(lessWaste and hasText(text(R.string.plans_less_waste_hint, "62", "35", "83", "80", "19"), substring = true)).assertExists()
        screen.keepScreenshot("plans-a-plan-that-wastes-less-asked-for")
        screen.onNode(hasText(text(R.string.plans_generate_button)) and hasClickAction()).performScrollTo().performClick()

        // Made so on the server, the pantry counted, and the plan says it.
        screen.waitForTheApi(60_000) { Instance.planIds(account, profileId).isNotEmpty() }
        val planId = Instance.planIds(account, profileId).first()
        val options = Instance.planOptions(account, planId)
        assertTrue(options.getBoolean("lessWaste"))
        assertTrue(options.getBoolean("respectInventory"))
        screen.scrollToText(text(R.string.plans_less_waste_line))
        screen.keepScreenshot("plans-a-plan-that-wastes-less")

        // Made again: the whole plan begins as it is and may stop wasting less; a day of it is made as the plan is.
        screen.scrollToText(text(R.string.plans_recalculate))
        screen.onNodeWithText(text(R.string.plans_recalculate)).performClick()
        screen.waitForText(text(R.string.plans_reroll_title))
        screen.waitForEnabled(text(R.string.plans_reroll_confirm))
        screen.onNode(lessWaste).assert(isOn())
        screen.keepScreenshot("plans-made-again-wasting-less")
        screen.onNodeWithText(text(R.string.plans_reroll_day)).performScrollTo().performClick()
        screen.onNode(lessWaste).assertDoesNotExist()
        screen.onNodeWithText(text(R.string.plans_reroll_whole)).performScrollTo().performClick()
        val made = Instance.planRevision(account, planId)
        screen.onNode(lessWaste).performScrollTo().performClick()
        screen.waitForEnabled(text(R.string.plans_reroll_confirm))
        screen.onNodeWithText(text(R.string.plans_reroll_confirm)).performClick()
        screen.waitForTheApi(60_000) { Instance.planRevision(account, planId) > made }
        assertFalse(Instance.planOptions(account, planId).getBoolean("lessWaste"))
    }
    @Test
    fun aDishCookedForTwoDaysChangesOnBothAndItsMealsSayWhereItComesFrom() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId, days = 2, cookAhead = true)
        screen.startSignedIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // The first day's meal says what is cooked; asked to swap, it says what else that changes.
        screen.scrollToText(text(R.string.plans_cook_days))
        screen.keepScreenshot("plans-a-dish-to-cook-for-two-days")
        val title = Instance.mealToCook(account, planId)
        val before = Instance.dayRecipes(account, planId, 0)
        val change = text(R.string.plans_action_for, text(R.string.plans_change_meal), title)
        screen.waitUntil(20_000) { screen.onAllNodesWithContentDescription(change).fetchSemanticsNodes().isNotEmpty() }
        screen.onAllNodesWithContentDescription(change)[0].performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.plans_swap)).performClick()
        screen.waitForEnabled(text(R.string.plans_offer_take))
        screen.onNode(hasText(text(R.string.plans_cooking_only)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        screen.keepScreenshot("plans-an-offer-that-changes-both-days")
        screen.onNodeWithText(text(R.string.plans_offer_take)).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_offer_title)).fetchSemanticsNodes().isEmpty() }

        // The dish stands on both days, and the second says it is the rest of the first.
        screen.waitForTheApi(20_000) { Instance.dayRecipes(account, planId, 0) != before }
        val dish = (Instance.dayRecipes(account, planId, 0) - before.toSet()).single()
        assertTrue(dish in Instance.dayRecipes(account, planId, 1))
        putAwayRebalance()
        screen.onNodeWithText(formatDayDate(LocalDate.now().plusDays(1).toString())).performScrollTo().performClick()
        val fromToday = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getStringArray(R.array.plans_cooking_day_from)[LocalDate.now().dayOfWeek.value - 1]
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        screen.scrollToText(fromToday)
        screen.keepScreenshot("plans-a-dish-from-the-day-before")
    }

    @Test
    fun aDishCookedAheadIsSplitByWeightFromItsMealAndAnyDishOnAScreenOfItsOwn() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId, days = 2, cookAhead = true)
        screen.startSignedIn(account)
        val grams = SemanticsMatcher("an amount in grams") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { Regex("^\\d+\u00A0g$").matches(it.text) }
        }
        // The amounts of the window in front alone: a meal's window under it lists its lines in grams too.
        val inFront = { window: SemanticsMatcher -> hasAnyAncestor(isDialog() and hasAnyDescendant(window)) }
        val amountsIn = { within: SemanticsMatcher ->
            screen.onAllNodes(grams and within).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].single().text.substringBefore('\u00A0').toLong() }
        }
        val amounts = { amountsIn(SemanticsMatcher("anywhere") { true }) }

        // The plans' screen leads to splitting any dish: there two equal portions of 1001 g are 501 g and 500 g, and what
        // a split cannot promise is said beside them.
        screen.openTab(text(R.string.tab_meal_plans))
        val line = text(R.string.plans_split_line, text(R.string.plans_split_link))
        screen.waitForText(line)
        screen.keepScreenshot("plans-the-way-to-splitting")
        screen.onNodeWithText(line).performFirstLinkClick()
        screen.waitForText(text(R.string.split_page_intro))
        screen.onNode(hasText(text(R.string.split_on_scale)) and hasSetTextAction()).performTextInput("1001")
        screen.waitUntil(10_000) { amounts() == listOf(501L, 500L) }
        // The sentence of the risk stands under this one; the journeys' texts are formatted, and its "100%" is no format.
        screen.onNodeWithText(text(R.string.split_tare)).performScrollTo()
        screen.keepScreenshot("split-a-dish-on-its-own")
        screen.onNodeWithContentDescription(text(R.string.common_back)).performScrollTo().performClick()

        // The dish cooked for two days is split from its meal, by the energy the plan gives each day's portion, into
        // whole grams that add up to the food alone: the scale's weight less the empty pot's.
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()
        screen.scrollToText(text(R.string.plans_cook_days))
        val title = Instance.mealToCook(account, planId)
        screen.onAllNodesWithText(title)[0].performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.split_open)) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        screen.waitForText(text(R.string.split_plan_intro))
        screen.onNode(hasText(text(R.string.split_on_scale)) and hasSetTextAction()).performTextInput("2660")
        screen.onNodeWithText(text(R.string.split_in_pot)).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.split_empty_pot)) and hasSetTextAction()).performScrollTo().performTextInput("1410")
        val split = inFront(hasText(text(R.string.split_plan_intro)))
        screen.waitUntil(10_000) { amountsIn(split).size == 2 && amountsIn(split).sum() == 1250L }
        screen.putKeyboardAway()
        screen.onNode(hasText(text(R.string.split_tare)) and split).performScrollTo()
        screen.keepScreenshot("plans-a-dish-split-by-weight")
    }

    @Test
    fun aPlanMadeTogetherSharesItsMealsMarksOneEatenForBothAndSplitsItsPotIntoBothPortions() {
        val account = Instance.newAccount()
        val adam = Instance.newProfile(account, name = "Adam")
        val ewa = Instance.newProfile(account, name = "Ewa")
        screen.signIn(account)
        val back = hasContentDescription(text(R.string.common_back))
        val inAWindow = hasAnyAncestor(isDialog())

        screen.openTab(text(R.string.tab_meal_plans))
        screen.waitForEnabled(text(R.string.plans_generate))
        screen.onNode(hasText(text(R.string.plans_generate)) and hasClickAction()).performClick()

        // The form: two days made together with the other profile of the account.
        screen.waitForText(text(R.string.plans_start_date))
        screen.onNodeWithText(text(R.string.plans_duration_days)).performTextReplacement("2")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.plans_joint_plan_with)).performScrollTo().performClick()
        screen.onNode(hasText("Ewa") and hasClickAction()).performClick()
        screen.onNodeWithText(text(R.string.plans_joint_plan_hint)).performScrollTo()
        screen.keepScreenshot("plans-a-plan-made-together")
        screen.onNode(hasText(text(R.string.plans_generate_button)) and hasClickAction()).performScrollTo().performClick()

        // Each person gets a plan of their own; the one opened says with whom it is made.
        screen.waitForTheApi(60_000) { Instance.planIds(account, adam).isNotEmpty() && Instance.planIds(account, ewa).isNotEmpty() }
        val planId = Instance.planIds(account, adam).first()
        screen.waitForText(text(R.string.plans_joint_with, "Ewa"))

        // A meal both eat says so, with the other's portion.
        val shared = Instance.sharedMeal(account, planId)
        val together = hasContentDescription(text(R.string.plans_action_for, text(R.string.plans_eaten_together), shared.title))
        val change = hasContentDescription(text(R.string.plans_action_for, text(R.string.plans_change_meal), shared.title))
        screen.scrollTo(together)
        screen.waitForText("Ewa: ", substring = true)
        screen.keepScreenshot("plans-a-shared-meal")

        // Another dish for it is asked for both, and the offer says it can be for this plan alone.
        screen.onAllNodes(change)[0].performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.plans_swap)).performClick()
        screen.waitForText(text(R.string.plans_offer_for_all, "Ewa"))
        screen.keepScreenshot("plans-a-shared-swap")
        screen.onNode(hasText(text(R.string.common_cancel)) and inAWindow).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_offer_title)).fetchSemanticsNodes().isEmpty() }

        // Taking it off its day asks whether off both days or off this one alone.
        screen.onAllNodes(change)[0].performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.plans_remove)).performClick()
        screen.waitForText(text(R.string.plans_remove_for_all))
        screen.keepScreenshot("plans-a-shared-removal")
        screen.onNode(hasText(text(R.string.common_cancel)) and inAWindow).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_remove_for_all)).fetchSemanticsNodes().isEmpty() }

        // One press marks it eaten in both plans.
        screen.onAllNodes(together)[0].performScrollTo().performClick()
        screen.waitForTheApi(20_000) {
            Instance.mealEaten(account, planId, shared.mealId) && Instance.mealEaten(account, shared.theirPlanId, shared.theirMealId)
        }
        putAwayRebalance()

        // Its window gives what the whole pot takes, for both portions, and the pot is split into the portions of both, each by name.
        screen.onAllNodesWithText(shared.title)[0].performScrollTo().performClick()
        val pot = text(R.string.plans_portion_pot, "\u0000", "\u0000", "\u0000", "\u0000", "\u0000").split("\u0000")[1].trim(' ', '.', ',', '\u00A0')
        screen.waitForText(pot, substring = true)
        screen.onNode(hasText(pot, substring = true) and inAWindow).performScrollTo()
        screen.keepScreenshot("plans-a-pot")
        screen.onNode(hasText(text(R.string.split_open)) and inAWindow).performScrollTo().performClick()
        screen.waitForText(text(R.string.split_plan_intro))
        // Looked for in the split's window alone: the meal's card under it names the other person too.
        val splitWindow = isDialog() and hasAnyDescendant(hasText(text(R.string.split_plan_intro)))
        screen.waitUntil(10_000) {
            listOf("Adam: ", "Ewa: ").all { name -> screen.onAllNodes(hasText(name, substring = true) and hasAnyAncestor(splitWindow)).fetchSemanticsNodes().isNotEmpty() }
        }
        screen.keepScreenshot("plans-a-pot-split")
        screen.onNode(hasText(text(R.string.common_close)) and hasAnyAncestor(splitWindow)).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.split_plan_intro)).fetchSemanticsNodes().isEmpty() }

        // Its recipe is saved for either person from one row, the two buttons together.
        screen.onNode(hasText(text(R.string.plans_open_recipe)) and inAWindow).performClick()
        screen.scrollToText(text(R.string.recipe_save_to_favorites))
        screen.keepScreenshot("recipes-saved-for-two")
        screen.scrollTo(back)
        screen.onNode(back).performClick()

        // The plan is made again for both, or for this plan alone, as its window asks.
        screen.scrollToText(text(R.string.plans_recalculate))
        screen.onNodeWithText(text(R.string.plans_recalculate)).performClick()
        screen.waitForText(text(R.string.plans_reroll_title))
        // Both choices in view, the window scrolled to the last of them.
        screen.onNode(hasText(text(R.string.plans_reroll_for_me_hint)) and hasAnyAncestor(isDialog())).performScrollTo()
        screen.keepScreenshot("plans-make-again-for-whom")
        screen.onNode(hasText(text(R.string.common_cancel)) and inAWindow).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_reroll_title)).fetchSemanticsNodes().isEmpty() }

        // The list says of the plan with whom it is made.
        screen.scrollTo(back)
        screen.onNode(back).performClick()
        screen.scrollToText(text(R.string.plans_joint_with, "Ewa"))
        screen.keepScreenshot("plans-a-list-together")

        // The diary says of a portion from the pot that a split comes only near it.
        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.diary_title)).performClick()
        screen.waitForText(text(R.string.diary_subhead))
        val fromPot = text(R.string.diary_meal_kcal_from_pot, "\u0000").split("\u0000")[1].trim()
        screen.scrollToText(fromPot, substring = true)
        screen.keepScreenshot("diary-a-pot-portion")
    }

    @Test
    fun aRecipeIsSentAsATextAndAsAPageToPrintFromAMealOfAPlanAndFromItsOwnScreen() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId, days = 2, cookAhead = true)
        val dish = Instance.dishToSend(account, planId)
        screen.startSignedIn(account)
        val inTheWindow = hasAnyAncestor(isDialog())

        // From the meal the dish is cooked at, it goes with what the window shows: the lines of its whole cooking, and
        // no figure of it.
        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()
        screen.scrollToText(text(R.string.plans_cook_days))
        screen.onAllNodesWithText(dish.title)[0].performScrollTo().performClick()
        val asText = screen.onNode(hasText(text(R.string.recipe_share_text)) and inTheWindow)
        asText.performScrollTo()
        screen.keepScreenshot("plans-a-meal-to-send")
        val fromTheMeal = sharedBy { asText.performClick() }
        assertEquals(dish.title, fromTheMeal.getStringExtra(Intent.EXTRA_SUBJECT))
        val mealText = fromTheMeal.getStringExtra(Intent.EXTRA_TEXT).orEmpty().lines()
        assertEquals(dish.title, mealText[0])
        assertTrue(mealText[2], mealText[2].startsWith(text(R.string.recipe_share_ingredients_for, "")))
        assertTrue(mealText[3], mealText[3].startsWith("- ${dish.cookingLine}: "))
        assertTrue(mealText.contains("${text(R.string.recipe_share_method)}:"))
        assertTrue(mealText.contains("1. ${dish.firstStep}"))
        assertFalse(mealText.any { "kcal" in it })

        // As a page to print, under the dish's title: the printing's window is pictured and closed.
        screen.printedAs(dish.title, "plans-a-meal-to-print") {
            screen.onNode(hasText(text(R.string.recipe_share_pdf)) and inTheWindow).performScrollTo().performClick()
        }

        // The recipe's own screen sends it as it is written there: its own servings and lines. The window's buttons
        // stand under its scrolling part.
        screen.onNode(hasText(text(R.string.plans_open_recipe)) and inTheWindow).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_open_recipe)).fetchSemanticsNodes().isEmpty() }
        screen.waitForText(text(R.string.recipes_nutrition_per_serving))
        screen.scrollToText(text(R.string.recipe_share_text))
        screen.onNodeWithText(text(R.string.recipe_share_text)).performScrollTo()
        screen.keepScreenshot("recipes-a-recipe-to-send")
        val fromTheRecipe = sharedBy { screen.onNodeWithText(text(R.string.recipe_share_text)).performClick() }
        val recipeText = fromTheRecipe.getStringExtra(Intent.EXTRA_TEXT).orEmpty().lines()
        assertEquals(dish.title, recipeText[0])
        // A text for a message has plain spaces where the app holds words together with no-break ones.
        assertEquals(
            "${text(R.string.recipe_share_ingredients_for, plural(R.plurals.plans_portion_recipe, dish.servings, dish.servings.toString()))}:"
                .replace('\u00A0', ' '),
            recipeText[2],
        )
        assertTrue(recipeText[3], recipeText[3].startsWith("- ${dish.recipeLine}: "))
    }

    @Test
    fun theCalendarOpensAPlanAtTheDayChosenAndADayWithoutOneBeginsANewPlan() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val month = YearMonth.now()
        Instance.newPlan(account, profileId, days = 3, start = month.atDay(10))
        screen.signIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        screen.waitForText(text(R.string.plans_calendar_title))

        // The plan's second day says what it holds once the month is read, and opens the plan there.
        val planned = month.atDay(11)
        val free = text(R.string.plans_calendar_day_free, spoken(planned))
        screen.waitUntil(20_000) {
            screen.onAllNodes(dayCell(planned)).fetchSemanticsNodes().any { it.config[SemanticsProperties.ContentDescription].single() != free }
        }
        screen.keepScreenshot("plans-the-calendar")
        screen.onNode(dayCell(planned)).performScrollTo().performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(hasText(formatDayDate(planned.toString())) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        // The chip is chosen before the day is drawn: the picture waits for the day's line of macronutrients.
        screen.waitForText(text(R.string.plans_day_macros_of, *Array(6) { "#" }).substringBefore("#"), substring = true)
        screen.keepScreenshot("plans-a-plan-at-its-day")
        screen.onNodeWithContentDescription(text(R.string.common_back)).performClick()

        // A day that no plan holds begins a new plan on it.
        val open = month.atDay(20)
        screen.waitForText(text(R.string.plans_calendar_title))
        screen.onNode(dayCell(open)).performScrollTo().performClick()
        screen.waitForText(text(R.string.plans_start_date))
        screen.waitForText(formatDate(open.toString()))
    }

    @Test
    fun aFavouriteSetIsPutOnEveryDayOfAPlanFromItsMenu() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val (recipeId, _) = Instance.aRecipeFor(account, "breakfast", text(R.string.api_language))
        Instance.mark(account, profileId, recipeId, "favorite")
        Instance.newFavoriteSet(account, profileId, "Weekday", mapOf("breakfast" to recipeId))
        val planId = Instance.newPlan(account, profileId, days = 2)
        screen.signIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // The set picked in the window the plan's menu opens, on every day of the plan, as it begins.
        val options = hasContentDescription(text(R.string.plans_options))
        screen.scrollTo(options)
        screen.onNode(options).performClick()
        screen.onNodeWithText(text(R.string.favorite_sets_apply)).performClick()
        // The profile's sets are read once the window is open.
        screen.waitForText(text(R.string.favorite_sets_apply_pick_set))
        screen.onNode(hasText(text(R.string.favorite_sets_apply_pick_set)) and hasAnyAncestor(isDialog())).performClick()
        screen.onNode(hasText("Weekday") and hasAnyAncestor(isPopup())).performClick()
        screen.keepScreenshot("plans-a-favourite-set-to-put-on")
        screen.onNode(hasText(text(R.string.favorite_sets_apply_confirm)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi(20_000) { Instance.recipesFor(account, planId, "breakfast") == listOf(recipeId, recipeId) }
        screen.waitUntil(20_000) { screen.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aMealIsMarkedEatenAMealOfOnesOwnAddedAndThePlanDeleted() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId)
        val (title, _) = Instance.firstMeal(account, planId)
        screen.signIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // The first meal is eaten: the box beside it, named by the meal, says so to the API.
        val eaten = text(R.string.plans_action_for, text(R.string.plans_mark_eaten), title)
        screen.waitUntil(20_000) { screen.onAllNodesWithContentDescription(eaten).fetchSemanticsNodes().isNotEmpty() }
        // The meals are below the plan's facts and its day: on a small screen the box may be at its edge.
        screen.onAllNodesWithContentDescription(eaten)[0].performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.firstMealEaten(account, planId) }
        screen.waitUntil(20_000) { screen.onAllNodes(hasContentDescription(eaten) and isOn()).fetchSemanticsNodes().isNotEmpty() }
        screen.keepScreenshot("plans-a-meal-eaten")
        // The meal that was marked stands where it stood.
        screen.onAllNodesWithContentDescription(eaten)[0].assertIsDisplayed()
        putAwayRebalance()

        // A meal of one's own: one serving and how many, and what that adds to the day before it is sent.
        screen.scrollToText(text(R.string.plans_add_custom_meal))
        screen.onNodeWithText(text(R.string.plans_add_custom_meal)).performClick()
        screen.waitForText(text(R.string.plans_custom_modal_title))
        screen.onNodeWithText(text(R.string.plans_custom_name)).performTextInput("Grandma's soup")
        screen.onNodeWithText(text(R.string.plans_custom_calories)).performScrollTo().performTextInput("300")
        screen.onNodeWithText(text(R.string.plans_custom_protein)).performScrollTo().performTextInput("12")
        screen.onNodeWithText(text(R.string.plans_custom_fat)).performScrollTo().performTextInput("8")
        screen.onNodeWithText(text(R.string.plans_custom_carbs)).performScrollTo().performTextInput("40")
        screen.onNodeWithText(text(R.string.plans_custom_servings)).performScrollTo().performTextReplacement("2")
        screen.waitForText(text(R.string.plans_custom_total, "600", "24", "16", "80"))
        screen.keepScreenshot("plans-a-meal-of-ones-own")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.plans_custom_submit)).performClick()
        screen.waitForTheApi(20_000) { Instance.ownMeals(account, planId) == listOf("Grandma's soup") }
        screen.scrollToText("Grandma's soup")
        putAwayRebalance()

        // A meal of one's own by its calories alone: the rest is estimated, and the meal says so.
        screen.scrollToText(text(R.string.plans_add_custom_meal))
        screen.onNodeWithText(text(R.string.plans_add_custom_meal)).performClick()
        screen.waitForText(text(R.string.plans_custom_modal_title))
        screen.onNodeWithText(text(R.string.plans_custom_name)).performTextInput("A sandwich out")
        screen.onNodeWithText(text(R.string.plans_custom_calories)).performScrollTo().performTextInput("450")
        screen.waitForText(text(R.string.plans_custom_total_estimated, "450"))
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.plans_custom_submit)).performClick()
        screen.waitForTheApi(20_000) { Instance.ownMeals(account, planId).contains("A sandwich out") }
        screen.scrollToText("A sandwich out")
        screen.waitForText(text(R.string.plans_estimated_chip))
        screen.keepScreenshot("plans-a-meal-by-its-calories")
        putAwayRebalance()

        // An ingredient of a meal is substituted in a window of its own: its lines first, then what may replace the one chosen.
        val (other, line) = Instance.uneatenMeal(account, planId)
        val changeOther = hasContentDescription(text(R.string.plans_action_for, text(R.string.plans_change_meal), other))
        screen.scrollTo(changeOther)
        screen.onAllNodes(changeOther)[0].performClick()
        screen.onNodeWithText("${text(R.string.plans_substitute_ingr)} ⇄").performClick()
        screen.waitForText(text(R.string.substitute_step_pick))
        // Looked for in the window alone: a meal behind it may be called as an ingredient is.
        screen.onNode(hasText(line) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForText(text(R.string.substitute_step_replace, line))
        screen.keepScreenshot("plans-an-ingredient-to-substitute")
        screen.onNode(hasText(text(R.string.common_close)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.substitute_step_pick)).fetchSemanticsNodes().isEmpty() }

        // The plan as a table or on paper is the web application's: the menu opens the plan's page there, in the browser.
        val options = hasContentDescription(text(R.string.plans_options))
        screen.scrollTo(options)
        screen.onNode(options).performClick()
        screen.waitForText(text(R.string.web_table_and_print))
        screen.keepScreenshot("plans-the-plan-menu")
        opensSignedInTheBrowser("/meal-plans?profile=$profileId&plan=$planId") {
            screen.onNodeWithText(text(R.string.web_table_and_print)).performClick()
        }

        // The plan is deleted from its menu once that is confirmed, and the list is back.
        screen.onNode(options).performClick()
        screen.onNodeWithText(text(R.string.common_delete)).performClick()
        screen.waitForText(text(R.string.plans_delete_confirm))
        screen.onNode(hasText(text(R.string.common_delete)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi(20_000) { Instance.planIds(account, profileId).isEmpty() }
        screen.waitForText(text(R.string.plans_calendar_title))
    }

    @Test
    fun aDayOfARunningPlanIsGivenAnotherNumberOfMealsAndAMealTakenOffIt() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId, days = 2)
        val (count, _) = Instance.firstDay(account, planId)
        val wanted = if (count < 5) count + 1 else count - 1
        screen.startSignedIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // Today's settings: another number of meals, and what that does said before it is saved.
        screen.scrollToText(text(R.string.plans_day_settings))
        screen.onNodeWithText(text(R.string.plans_day_settings)).performClick()
        screen.waitForText(text(R.string.plans_settings_unchanged))
        screen.onNode(hasText(text(R.string.plans_settings_meal_count)) and hasAnyAncestor(isDialog())).performClick()
        screen.onNode(hasText(plural(R.plurals.plans_settings_meal_count_option, wanted, wanted)) and hasAnyAncestor(isPopup())).performClick()
        screen.waitForText(text(R.string.plans_settings_other_days))
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_settings_unchanged)).fetchSemanticsNodes().isEmpty() }
        screen.keepScreenshot("plans-the-settings-of-a-day")
        screen.onNode(hasText(text(R.string.plans_settings_save)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi(60_000) { Instance.firstDay(account, planId).let { (set, meals) -> set == wanted && meals.size == wanted } }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_settings_title)).fetchSemanticsNodes().isEmpty() }

        // Opened again, the window starts from the day as it is now.
        screen.scrollToText(text(R.string.plans_day_settings))
        screen.onNodeWithText(text(R.string.plans_day_settings)).performClick()
        screen.waitForText(text(R.string.plans_settings_unchanged))
        screen.onNode(hasText(text(R.string.common_cancel)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_settings_title)).fetchSemanticsNodes().isEmpty() }

        // A meal is taken off its day once that is confirmed, with what it does said first.
        val (title, _) = Instance.firstMeal(account, planId)
        val change = hasContentDescription(text(R.string.plans_action_for, text(R.string.plans_change_meal), title))
        screen.scrollTo(change)
        screen.onAllNodes(change)[0].performClick()
        screen.onNodeWithText(text(R.string.plans_remove)).performClick()
        screen.waitForText(text(R.string.plans_remove_note))
        screen.keepScreenshot("plans-a-meal-taken-off-its-day")
        screen.onNode(hasText(text(R.string.plans_remove_confirm)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi(20_000) { Instance.firstDay(account, planId).second.size == wanted - 1 }
    }

    @Test
    fun aPlanWhoseProfilesTargetMovedSaysBothTargetsAndTakesTheProfilesFromToday() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId, days = 3)
        // A list of the plan's days: taking the new target says first that the list goes out of date.
        Instance.newShoppingList(account, planId, LocalDate.now(), LocalDate.now().plusDays(2))
        // Ten kilograms less today: the profile's target moves by more than the plan lets pass.
        Instance.logWeight(account, profileId, 70.0, LocalDate.now())
        val (profileTarget, planTarget) = Instance.targetMoved(account, planId)!!
        screen.startSignedIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // Both targets, what taking the profile's does, and what it does to the list.
        val moved = text(R.string.plans_target_moved, profileTarget, planTarget)
        screen.scrollToText(moved)
        val take = hasText(text(R.string.plans_retarget)) and hasClickAction()
        screen.scrollTo(take)
        screen.waitForText(text(R.string.plans_retarget_note))
        screen.waitForText(text(R.string.plans_list_out_of_date_days))
        screen.keepScreenshot("plans-a-target-moved")
        screen.onNode(take).performScrollTo().performClick()
        screen.waitForTheApi(60_000) { Instance.targetMoved(account, planId) == null }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(moved).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aDayMadeATrainingDayAloneLeavesItsWeekOffTheTargetTillTheWeekIsEvenedOut() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId, days = 7)
        // A list of the first three days: what changes them says first that the list goes out of date.
        Instance.newShoppingList(account, planId, LocalDate.now(), LocalDate.now().plusDays(2))
        screen.startSignedIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // Today made a training day: what that does to the week, and the two ways of saving it.
        screen.scrollToText(text(R.string.plans_day_settings))
        screen.onNodeWithText(text(R.string.plans_day_settings)).performClick()
        screen.waitForText(text(R.string.plans_settings_unchanged))
        // The day's own target and its cooking time may stay empty, and say so in their outline.
        val optional = hasStateDescription(text(R.string.common_optional))
        screen.onNode(hasText(text(R.string.plans_settings_own_target)) and hasSetTextAction() and hasAnyAncestor(isDialog())).assert(optional)
        screen.onNode(hasText(text(R.string.plans_settings_cook_time)) and hasSetTextAction() and hasAnyAncestor(isDialog())).assert(optional)
        screen.onNode(hasText(text(R.string.plans_settings_kind)) and hasAnyAncestor(isDialog())).performClick()
        screen.onNode(hasText(text(R.string.plans_day_kind_training)) and hasAnyAncestor(isPopup())).performClick()
        val saveDay = hasText(text(R.string.plans_settings_save_day)) and hasAnyAncestor(isDialog())
        screen.waitUntil(20_000) { screen.onAllNodes(saveDay).fetchSemanticsNodes().isNotEmpty() }
        screen.onNode(hasText(text(R.string.plans_list_out_of_date_days)) and hasAnyAncestor(isDialog())).performScrollTo()
        screen.keepScreenshot("plans-a-day-and-its-week")
        screen.onNode(saveDay).performClick()
        screen.waitForTheApi(60_000) { Instance.firstWeek(account, planId).getBoolean("uneven") }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_settings_title)).fetchSemanticsNodes().isEmpty() }

        // The plan says the week stands off its target, and evens it out.
        val evenWeek = hasText(text(R.string.plans_even_week)) and hasClickAction()
        screen.scrollTo(evenWeek)
        screen.waitForText(text(R.string.plans_list_out_of_date_days))
        screen.keepScreenshot("plans-a-week-off-its-target")
        screen.onNode(evenWeek).performClick()
        screen.waitForTheApi(60_000) { !Instance.firstWeek(account, planId).getBoolean("uneven") }
        screen.waitUntil(20_000) { screen.onAllNodes(evenWeek).fetchSemanticsNodes().isEmpty() }

        // A meal of a day the list covers is to be taken off it: the window says the list goes out of date.
        val (title, _) = Instance.firstMeal(account, planId)
        val change = hasContentDescription(text(R.string.plans_action_for, text(R.string.plans_change_meal), title))
        screen.scrollTo(change)
        screen.onAllNodes(change)[0].performClick()
        screen.onNodeWithText(text(R.string.plans_remove)).performClick()
        screen.waitForText(text(R.string.plans_settings_list_out_of_date))
        screen.keepScreenshot("plans-a-meal-taken-off-a-listed-day")
        screen.onNode(hasText(text(R.string.common_cancel)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_settings_list_out_of_date)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun anIngredientIsSubstitutedAndItsWindowOpenedAgainOnTheMealStartsAnew() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        // A day the catalogue suggests a substitute for one of its meals: a few plans are made until one has it.
        var planId = ""
        var found: Instance.Substitutable? = null
        repeat(4) {
            if (found != null) return@repeat
            planId = Instance.newPlan(account, profileId, days = 1)
            found = Instance.substitutableMeal(account, planId)
            if (found == null) Instance.deletePlan(account, planId)
        }
        assumeTrue("No plan made had a meal with a substitute the catalogue suggests.", found != null)
        val (mealId, title, line, substitute) = found!!
        screen.startSignedIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // The line leaves for what the catalogue suggests; where that moves a serving's figures, the change is taken.
        val change = hasContentDescription(text(R.string.plans_action_for, text(R.string.plans_change_meal), title))
        screen.scrollTo(change)
        screen.onAllNodes(change)[0].performClick()
        screen.onNodeWithText("${text(R.string.plans_substitute_ingr)} ⇄").performClick()
        screen.waitForText(text(R.string.substitute_step_pick))
        // The window scrolls: what is tapped is scrolled into view first, or the tap lands beside it.
        screen.onNode(hasText(line) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        screen.waitForText(text(R.string.substitute_catalogue_suggests))
        screen.onAllNodes(hasText(substitute) and hasClickAction() and hasAnyAncestor(isDialog()))[0].performScrollTo().performClick()
        val accept = hasText(text(R.string.substitute_accept_change)) and hasAnyAncestor(isDialog())
        val apply = hasText(text(R.string.substitute_apply)) and isEnabled()
        screen.waitUntil(20_000) { screen.onAllNodes(accept).fetchSemanticsNodes().isNotEmpty() || screen.onAllNodes(apply).fetchSemanticsNodes().isNotEmpty() }
        if (screen.onAllNodes(accept).fetchSemanticsNodes().isNotEmpty()) screen.onNode(accept).performScrollTo().performClick()
        screen.waitForEnabled(text(R.string.substitute_apply))
        val before = Instance.planRevision(account, planId)
        screen.onNode(hasText(text(R.string.substitute_apply)) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.planRevision(account, planId) > before }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.substitute_step_pick)).fetchSemanticsNodes().isEmpty() }

        // Made without a model, the recipe's words can be corrected: they go into it, and the plan reads them.
        screen.onNode(hasText(text(R.string.plans_reword_button)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.plans_reword_intro))
        screen.onNode(hasText(text(R.string.own_recipe_field_title)) and hasSetTextAction()).performTextReplacement("Moja poprawiona miska")
        screen.putKeyboardAway()
        screen.keepScreenshot("plans-the-words-of-a-swap-corrected")
        screen.onNode(hasText(text(R.string.common_save)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi(20_000) { Instance.mealTitle(account, planId, mealId) == "Moja poprawiona miska" }
        screen.scrollToText("Moja poprawiona miska")
        putAwayRebalance()

        // Opened again on the meal, the window starts anew and stays open: it does not hand back the substitution it made.
        // The substitution may have reworded the meal's title, which names the way to change it.
        val changed = hasContentDescription(text(R.string.plans_action_for, text(R.string.plans_change_meal), Instance.mealTitle(account, planId, mealId)))
        screen.scrollTo(changed)
        screen.onAllNodes(changed)[0].performClick()
        screen.onNodeWithText("${text(R.string.plans_substitute_ingr)} ⇄").performClick()
        screen.waitForText(text(R.string.substitute_step_pick))
        screen.waitForIdle()
        assertTrue(screen.onAllNodes(hasText(text(R.string.substitute_step_pick)) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty())
        screen.onNode(hasText(text(R.string.common_close)) and hasAnyAncestor(isDialog())).performClick()
    }

    @Test
    fun theSwapsThatWouldUseUpWhatAPlanOpensAreFoundUnderItsPacksAndOneIsTaken() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        // A day's menu opens more than it finishes.
        val planId = Instance.newPlan(account, profileId, days = 1)
        assumeTrue("The plan made leaves no more than a fifth of any pack.", Instance.wastesAPack(account, planId))
        val swaps = Instance.useUpSwaps(account, planId)
        screen.signIn(account)

        screen.openTab(text(R.string.tab_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // The line of packs unfolds into what the plan leaves of each, and the way to use them up.
        val packs = SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)
        screen.waitUntil(20_000) { screen.onAllNodes(packs).fetchSemanticsNodes().isNotEmpty() }
        screen.onNode(packs).performScrollTo().performClick()
        screen.scrollToText(text(R.string.plans_use_up_ask))
        screen.onNodeWithText(text(R.string.plans_use_up_ask)).performClick()

        // The screen offers what the API finds for the plan.
        if (swaps == 0) {
            screen.scrollToText(text(R.string.plans_use_up_none))
            return
        }
        val take = text(R.string.plans_offer_take)
        screen.waitUntil(30_000) { screen.onAllNodesWithText(take).fetchSemanticsNodes().size == swaps }
        screen.keepScreenshot("plans-use-up")
        val found = Instance.planRevision(account, planId)
        screen.onAllNodesWithText(take)[0].performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.planRevision(account, planId) > found }
        // The swaps were for the plan as it was: they go with it.
        screen.waitUntil(20_000) { screen.onAllNodesWithText(take).fetchSemanticsNodes().isEmpty() }
    }

    /** Puts away the card that says how a change balanced its day, where the change left one. */
    private fun putAwayRebalance() {
        val dismiss = text(R.string.plans_rebalance_dismiss)
        if (screen.onAllNodesWithText(dismiss).fetchSemanticsNodes().isNotEmpty()) runCatching { screen.onNodeWithText(dismiss).performClick() }
    }

    /** The cell of [date] in the calendar: what it says begins with the day's name. */
    private fun dayCell(date: LocalDate): SemanticsMatcher = hasContentDescription("${spoken(date)}:", substring = true)

    private fun spoken(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
}

