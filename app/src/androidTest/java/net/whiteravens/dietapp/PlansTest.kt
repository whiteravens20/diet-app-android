// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.opensSignedInTheBrowser
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatDayDate
import org.junit.Assert.assertEquals
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

        screen.openTab(text(R.string.nav_meal_plans))
        screen.waitForEnabled(text(R.string.plans_generate))
        screen.onNode(hasText(text(R.string.plans_generate)) and hasClickAction()).performClick()

        // The new plan's form: three days, cooked ahead, four meals a day instead of the profile's own.
        screen.waitForText(text(R.string.plans_start_date))
        screen.onNodeWithText(text(R.string.plans_duration_days)).performTextReplacement("3")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.plans_meal_prep)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.plans_meals_a_day)).performScrollTo().performClick()
        screen.onNode(hasText("4") and hasClickAction()).performClick()
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

        // The plan is made again from its facts: what would change is said before it is asked for.
        val made = Instance.planRevision(account, planId)
        screen.scrollToText(text(R.string.plans_recalculate))
        screen.onNodeWithText(text(R.string.plans_recalculate)).performClick()
        screen.waitForText(text(R.string.plans_reroll_title))
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
        // A swap that balanced its day says so in a card over the meals.
        putAwayRebalance()

        // The meal as planned opens on a tap: its own lines, and its recipe a tap further.
        val (title, lines) = Instance.firstMeal(account, planId)
        screen.scrollToText(title)
        screen.onAllNodesWithText(title)[0].performScrollTo().performClick()
        screen.waitForText(lines.first())
        screen.keepScreenshot("plans-a-meal-as-planned")
        screen.onNodeWithText(text(R.string.plans_open_recipe)).performClick()
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.plans_open_recipe)).fetchSemanticsNodes().isEmpty() }
        screen.waitForText(title)
    }

    @Test
    fun theCalendarOpensAPlanAtTheDayChosenAndADayWithoutOneBeginsANewPlan() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val month = YearMonth.now()
        Instance.newPlan(account, profileId, days = 3, start = month.atDay(10))
        screen.signIn(account)

        screen.openTab(text(R.string.nav_meal_plans))
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

        screen.openTab(text(R.string.nav_meal_plans))
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

        screen.openTab(text(R.string.nav_meal_plans))
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

        screen.openTab(text(R.string.nav_meal_plans))
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
    fun anIngredientIsSubstitutedAndItsWindowOpenedAgainOnTheMealStartsAnew() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        // A day the catalogue suggests a substitute for one of its meals: a few plans are made until one has it.
        var planId = ""
        var found: Triple<String, String, String>? = null
        repeat(4) {
            if (found != null) return@repeat
            planId = Instance.newPlan(account, profileId, days = 1)
            found = Instance.substitutableMeal(account, planId)
            if (found == null) Instance.deletePlan(account, planId)
        }
        assumeTrue("No plan made had a meal with a substitute the catalogue suggests.", found != null)
        val (title, line, substitute) = found!!
        screen.startSignedIn(account)

        screen.openTab(text(R.string.nav_meal_plans))
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // The line leaves for what the catalogue suggests; where that moves a serving's figures, the change is taken.
        val change = hasContentDescription(text(R.string.plans_action_for, text(R.string.plans_change_meal), title))
        screen.scrollTo(change)
        screen.onAllNodes(change)[0].performClick()
        screen.onNodeWithText("${text(R.string.plans_substitute_ingr)} ⇄").performClick()
        screen.waitForText(text(R.string.substitute_step_pick))
        screen.onNode(hasText(line) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForText(text(R.string.substitute_catalogue_suggests))
        screen.onAllNodes(hasText(substitute) and hasClickAction() and hasAnyAncestor(isDialog()))[0].performClick()
        val accept = hasText(text(R.string.substitute_accept_change)) and hasAnyAncestor(isDialog())
        val apply = hasText(text(R.string.substitute_apply)) and isEnabled()
        screen.waitUntil(20_000) { screen.onAllNodes(accept).fetchSemanticsNodes().isNotEmpty() || screen.onAllNodes(apply).fetchSemanticsNodes().isNotEmpty() }
        if (screen.onAllNodes(accept).fetchSemanticsNodes().isNotEmpty()) screen.onNode(accept).performScrollTo().performClick()
        screen.waitForEnabled(text(R.string.substitute_apply))
        val before = Instance.planRevision(account, planId)
        screen.onNode(hasText(text(R.string.substitute_apply)) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.planRevision(account, planId) > before }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.substitute_step_pick)).fetchSemanticsNodes().isEmpty() }
        putAwayRebalance()

        // Opened again on the meal, the window starts anew and stays open: it does not hand back the substitution it made.
        screen.scrollTo(change)
        screen.onAllNodes(change)[0].performClick()
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

        screen.openTab(text(R.string.nav_meal_plans))
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

