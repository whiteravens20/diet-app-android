// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.ui.common.formatDate
import net.whiteravens.dietapp.ui.common.formatDayDate
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

        screen.onNodeWithText(text(R.string.nav_meal_plans)).performClick()
        screen.waitForEnabled(text(R.string.plans_generate))
        screen.onNode(hasText(text(R.string.plans_generate)) and hasClickAction()).performClick()

        // The new plan's form: three days, cooked ahead, four meals a day instead of the profile's own.
        screen.waitForText(text(R.string.plans_start_date))
        screen.onNodeWithText(text(R.string.plans_duration_days)).performTextReplacement("3")
        screen.onNodeWithText(text(R.string.plans_meal_prep)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.plans_meals_a_day)).performScrollTo().performClick()
        screen.onNode(hasText("4") and hasClickAction()).performClick()
        screen.keepScreenshot("plans-a-new-plan")
        screen.onNode(hasText(text(R.string.plans_generate_button)) and hasClickAction()).performScrollTo().performClick()

        // The plan is made on the server with what the form chose, and opens in its place.
        screen.waitUntil(60_000) { Instance.planIds(account, profileId).isNotEmpty() }
        val planId = Instance.planIds(account, profileId).first()
        val options = Instance.planOptions(account, planId)
        assertEquals(4, options.getInt("mealCount"))
        assertTrue(options.getBoolean("mealPrepFriendly"))
        assertEquals(3, Instance.planStart(account, planId).second)
        // The plan says what it is, on the profile's diet.
        screen.waitForText(plural(R.plurals.plans_plan_summary, 3, 3, text(R.string.diet_balanced)))
        screen.keepScreenshot("plans-a-plan-made")

        // The first meal of the first day is swapped: one offer is put away for another, which is taken.
        val swap = text(R.string.plans_swap)
        screen.waitUntil(20_000) { screen.onAllNodesWithContentDescription(swap).fetchSemanticsNodes().isNotEmpty() }
        val before = Instance.firstDayRecipes(account, planId)
        screen.onAllNodesWithContentDescription(swap)[0].performClick()
        screen.waitForEnabled(text(R.string.plans_swap_take))
        screen.keepScreenshot("plans-a-swap-offered")
        screen.onNodeWithText(text(R.string.plans_swap_another)).performClick()
        screen.waitForEnabled(text(R.string.plans_swap_take))
        screen.onNodeWithText(text(R.string.plans_swap_take)).performClick()
        screen.waitUntil(20_000) {
            screen.onAllNodesWithText(text(R.string.plans_swap_offer_title)).fetchSemanticsNodes().isEmpty()
        }

        assertNotEquals(before.first(), Instance.firstDayRecipes(account, planId).first())
        screen.keepScreenshot("plans-after-the-swap")
        // A swap that balanced its day says so in a card over the meals.
        putAwayRebalance()

        // The meal as planned opens on a tap: its own lines, and its recipe a tap further.
        val (title, lines) = Instance.firstMeal(account, planId)
        screen.waitForText(title)
        screen.onAllNodesWithText(title)[0].performClick()
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

        screen.onNodeWithText(text(R.string.nav_meal_plans)).performClick()
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
    fun theListIsNarrowedToWhereAPlanStandsAndTheFilterCleared() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val next = YearMonth.now().plusMonths(1).atDay(10)
        Instance.newPlan(account, profileId, days = 2, start = next)
        screen.signIn(account)

        screen.onNodeWithText(text(R.string.nav_meal_plans)).performClick()
        // The plan's card names the day it begins on.
        val card = formatDate(next.toString())
        // The list is below the calendar.
        screen.scrollToText(card, substring = true)

        // A plan of next month is still to come: it is among those to come, and none of those that are over.
        screen.scrollToText(text(R.string.plans_status_past))
        screen.onNodeWithText(text(R.string.plans_status_past)).performClick()
        screen.scrollToText(text(R.string.plans_none_filtered))
        assertTrue(screen.onAllNodesWithText(card, substring = true).fetchSemanticsNodes().isEmpty())
        screen.keepScreenshot("plans-narrowed")
        screen.onNodeWithText(text(R.string.plans_status_upcoming)).performClick()
        screen.scrollToText(card, substring = true)

        screen.scrollToText(text(R.string.common_clear))
        screen.onNodeWithText(text(R.string.common_clear)).performClick()
        screen.scrollToText(card, substring = true)
        assertTrue(screen.onAllNodesWithText(text(R.string.common_clear)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aMealIsMarkedEaten() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        val planId = Instance.newPlan(account, profileId)
        val (title, _) = Instance.firstMeal(account, planId)
        screen.signIn(account)

        screen.onNodeWithText(text(R.string.nav_meal_plans)).performClick()
        val card = formatDate(LocalDate.now().toString())
        screen.scrollToText(card, substring = true)
        screen.onNodeWithText(card, substring = true).performClick()

        // The first meal is eaten: the box beside it, named by the meal, says so to the API.
        val eaten = text(R.string.plans_action_for, text(R.string.plans_mark_eaten), title)
        screen.waitUntil(20_000) { screen.onAllNodesWithContentDescription(eaten).fetchSemanticsNodes().isNotEmpty() }
        // The meals are below the plan's facts and its day: on a small screen the box may be at its edge.
        screen.onAllNodesWithContentDescription(eaten)[0].performScrollTo().performClick()
        screen.waitUntil(20_000) { Instance.firstMealEaten(account, planId) }
        screen.waitUntil(20_000) { screen.onAllNodes(hasContentDescription(eaten) and isOn()).fetchSemanticsNodes().isNotEmpty() }
        screen.keepScreenshot("plans-a-meal-eaten")
        putAwayRebalance()
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

