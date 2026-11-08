// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.airplaneMode
import net.whiteravens.dietapp.testing.heldTogether
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.opensSignedInTheBrowser
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.pressLink
import net.whiteravens.dietapp.testing.pullDown
import net.whiteravens.dietapp.testing.pullable
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DashboardTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    @After
    fun backOnline() = screen.airplaneMode(false)

    @Test
    fun aProfileIsShownWithTheDailyTargetTheServerComputedForIt() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val target = Instance.dailyTarget(account, profileId)

        screen.signIn(account)

        screen.waitForText("Ala")
        screen.waitForText(heldTogether("$target ${text(R.string.unit_kcal)}"))
        screen.keepScreenshot("dashboard-a-profile")

        // A profile's favourite sets are made and changed in the web application, whose dashboard opens in the browser.
        // The profile is one item of the list, its sets' card at the item's end: scrolled to itself, not to the item.
        screen.onNodeWithText(text(R.string.favorite_sets_in_the_browser)).performScrollTo().assertIsDisplayed()
        screen.keepScreenshot("dashboard-favourite-sets-in-the-browser")
        opensSignedInTheBrowser("/dashboard") {
            screen.onNodeWithText(text(R.string.web_open)).performScrollTo().performClick()
        }

        // A profile with no plan is offered to make one, and the form for it opens.
        screen.waitForText(text(R.string.overview_none))
        screen.onNodeWithText(text(R.string.overview_none_cta)).performScrollTo().performClick()
        screen.waitForText(text(R.string.plans_start_date))
    }

    @Test
    fun aProfileMadeElsewhereShowsWhenTheDashboardIsPulledDown() {
        val account = Instance.newAccount()
        Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        screen.waitForText("Ala")

        Instance.newProfile(account, name = "Ola")
        screen.pullDown()

        // A second profile's card is below the first, out of view: the list composes only what is in view.
        screen.scrollToText("Ola")
        screen.keepScreenshot("dashboard-pulled-down")
    }

    @Test
    fun aProfileMadeElsewhereShowsOnComingBackFromAnotherScreen() {
        val account = Instance.newAccount()
        Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        screen.waitForText("Ala")

        // The notifications open on the bell, which says no more than their name while none is unread.
        screen.onNode(hasContentDescription(text(R.string.notifications_title)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.notifications_title))
        Instance.newProfile(account, name = "Ola")
        screen.onNodeWithContentDescription(text(R.string.common_back)).performClick()

        // A second profile's card is below the first, out of view: the list composes only what is in view.
        screen.scrollToText("Ola")
    }

    @Test
    fun aScreenReaderReadsTheDashboardAnewByTheActionThePullOffers() {
        val account = Instance.newAccount()
        Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        screen.waitForText("Ala")

        Instance.newProfile(account, name = "Ola")
        val actions = screen.onNode(pullable).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        screen.runOnIdle { actions.first { it.label == text(R.string.pull_to_read) }.action() }

        // A second profile's card is below the first, out of view: the list composes only what is in view.
        screen.scrollToText("Ola")
    }

    @Test
    fun thePlanUnderWaySaysWhatItComesToAndOpens() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        Instance.newPlan(account, profileId)
        screen.signIn(account)

        // A plan from today is the one under way.
        screen.waitForText(text(R.string.overview_title_active))
        screen.keepScreenshot("dashboard-the-plan-under-way")
        screen.onNodeWithText(text(R.string.overview_open)).performScrollTo().performClick()
        screen.waitUntil(20_000) { screen.onAllNodes(hasContentDescription(text(R.string.plans_options))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun anAccountOnTheInstancesAiIsToldHowManyRequestsAreLeftThisMonthWhichLeadsToItsSettings() {
        val account = Instance.newAccount()
        Instance.newProfile(account, name = "Ala")
        Instance.useAi(account, "admin")
        val limit = NumberFormat.getNumberInstance(Locale.getDefault()).apply { isGroupingUsed = false }.format(Instance.aiQuota(account).getInt("limit"))
        screen.signIn(account)

        // None used yet: all of the month's are left.
        val left = text(R.string.dashboard_ai_left, limit, limit)
        screen.waitForText(left)
        screen.keepScreenshot("dashboard-ai-requests")
        screen.onNodeWithText(left).performClick()
        screen.waitForText(account.email)
    }

    @Test
    fun aWeightIsLoggedOnItsProfilesCardDrawnAndTheLastOneTakenBack() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val threeDaysAgo = LocalDate.now().minusDays(3)
        Instance.logWeight(account, profileId, 80.6, threeDaysAgo)
        Instance.chooseWeightUnit(account, "kg")
        screen.signIn(account)

        // The weight logged three days ago is a point of the chart, which says its day in words.
        screen.scrollToText(text(R.string.weight_title))
        screen.scrollTo(hasContentDescription(formatDate(threeDaysAgo.toString()), substring = true))

        // Today's weight, typed with a decimal comma, is logged and the field emptied.
        val field = hasText(text(R.string.weight_today, "kg"))
        screen.scrollTo(field)
        screen.onNode(field).performScrollTo().performTextInput("81,5")
        screen.putKeyboardAway()
        screen.onNode(hasText(text(R.string.weight_log)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.weights(account, profileId) == listOf(81.5, 80.6) }
        screen.waitUntil(20_000) {
            screen.onAllNodes(field and SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))).fetchSemanticsNodes().isNotEmpty()
        }
        // The chart's days are read again, and today is one of them.
        screen.scrollTo(hasContentDescription(formatDate(LocalDate.now().toString()), substring = true))
        screen.keepScreenshot("dashboard-a-weight-logged")

        // The last entry taken back once it is confirmed; the one before it stays, and is the one offered next.
        screen.onNodeWithText(text(R.string.weight_remove_latest)).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.common_delete)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi(20_000) { Instance.weights(account, profileId) == listOf(80.6) }
        val before = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 1 }.format(80.6)
        screen.waitForText("(${text(R.string.weight_remove_latest_label, heldTogether("$before kg"), formatDate(threeDaysAgo.toString()))})")
    }

    @Test
    fun waterIsAddedOnItsProfilesCardAtAPressOrTypedAndTheLastEntryTakenBack() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val today = LocalDate.now()
        screen.startSignedIn(account)
        fun drunk(ml: Int) = hasText(text(R.string.water_drunk, liters(ml), liters(2000)))

        // A new profile drinks to 2 l a day, and nothing was drunk today.
        screen.scrollToText(text(R.string.water_title))
        screen.scrollTo(drunk(0))

        // 250 ml at a press, on the card and on the server.
        val glass = hasText(text(R.string.water_add_ml, 250)) and hasClickAction()
        screen.scrollTo(glass)
        screen.waitForEnabled(text(R.string.water_add_ml, 250))
        screen.onNode(glass).performScrollTo().performClick()
        screen.waitForTheApi { Instance.water(account, profileId, today) == 250 }
        screen.scrollTo(drunk(250))

        // 120 ml typed, and the field emptied.
        val own = hasText(text(R.string.water_own)) and hasSetTextAction()
        screen.scrollTo(own)
        screen.onNode(own).performScrollTo().performTextInput("120")
        screen.putKeyboardAway()
        screen.onNode(hasText(text(R.string.water_add)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForTheApi { Instance.water(account, profileId, today) == 370 }
        screen.scrollTo(drunk(370))
        screen.onNodeWithText(text(R.string.water_remove_last)).performScrollTo()
        screen.keepScreenshot("dashboard-water")

        // The last entry taken back: the 120 ml, and the 250 ml are left.
        screen.onNodeWithText(text(R.string.water_remove_last)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.water(account, profileId, today) == 250 }
        screen.scrollTo(drunk(250))

        // The note under the card, that it can be turned off in the profile, closed, says it no more.
        val note = text(R.string.water_note, text(R.string.water_note_link))
        screen.onNode(hasContentDescription(text(R.string.common_close)) and hasAnySibling(hasText(note))).performScrollTo().performClick()
        screen.waitUntil(5_000) { screen.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty() }

        // The icon's shortcuts are the card's amounts, and one adds to the profile watered last without a screen.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            listOf("+200 ml", "+250 ml", "+330 ml"),
            ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }.map { it.shortLabel.toString().replace('\u00A0', ' ') },
        )
        context.startActivity(WaterShortcutActivity.adding(context, 330).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        screen.waitForTheApi { Instance.water(account, profileId, today) == 580 }
    }

    @Test
    fun theWaterCardSaysItCanBeTurnedOffInTheProfileWhereItsLinkLeadsAndTurnedOffThereTheCardIsGone() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        val link = text(R.string.water_note_link)
        val note = text(R.string.water_note, link)
        val tracking = text(R.string.profile_water_tracking)
        val target = hasText(text(R.string.profile_water_target)) and hasSetTextAction()

        // Under the card, a note whose link opens the profile's form.
        screen.scrollToText(text(R.string.water_title))
        screen.scrollToText(note)
        screen.keepScreenshot("dashboard-water-note")
        screen.pressLink(hasText(note), link)
        screen.waitForText(text(R.string.profiles_editing, "Ala"))

        // Turned off there, the target and the reminder to drink go out of sight. The form is one scrolled column, not a list.
        screen.waitUntil(20_000) { screen.onAllNodes(target).fetchSemanticsNodes().isNotEmpty() }
        screen.onNode(target).performScrollTo()
        screen.onNodeWithText(tracking).performScrollTo().performClick()
        screen.waitUntil(5_000) { screen.onAllNodes(target).fetchSemanticsNodes().isEmpty() }
        screen.onNodeWithText(tracking).performScrollTo()
        screen.keepScreenshot("profiles-water-not-tracked")
        screen.onNodeWithText(text(R.string.profile_save_changes)).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { !Instance.profile(account, profileId).getBoolean("waterTracking") }

        // Back on the dashboard, the profile has no water card: the card would stand above its split of macronutrients.
        screen.scrollToText(text(R.string.dashboard_macro_split))
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.water_title)).fetchSemanticsNodes().isEmpty() }
        screen.onNodeWithText(text(R.string.dashboard_macro_split)).assertIsDisplayed()
    }

    @Test
    fun threeWeeksWrittenProposeTheRealExpenditureWhichIsTakenAndTheWeekSaysThemInSum() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val today = LocalDate.now()
        // Sixteen of the 21 days before today written, and five weights over them: more is eaten than the formula says, and the weight holds.
        (1..21).filter { it % 4 != 0 }.forEach { back -> Instance.writeInTheDiary(account, profileId, "Day's meals", 3200, today.minusDays(back.toLong())) }
        listOf(20, 15, 10, 5, 1).forEach { back -> Instance.logWeight(account, profileId, 80.0, today.minusDays(back.toLong())) }
        screen.startSignedIn(account)

        // A proposal, said to be one, which the dashboard asks about.
        screen.scrollToText(text(R.string.expenditure_title))
        screen.scrollToText(text(R.string.expenditure_note))
        screen.keepScreenshot("dashboard-real-expenditure")

        // The week before today in sum: what was eaten against the target, the days counted and the weight's way,
        // its last line, brought into view with the card above it.
        screen.scrollToText(text(R.string.week_title))
        val (trend, _) = text(R.string.week_trend, "\u0000").split("\u0000")
        screen.waitForText(trend, substring = true)
        screen.onNodeWithText(trend, substring = true).performScrollTo()
        screen.keepScreenshot("dashboard-the-week-in-sum")

        // Taken: the profile keeps the correction, and nothing is proposed any more.
        screen.scrollToText(text(R.string.expenditure_note))
        screen.onNode(hasText(text(R.string.expenditure_accept)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { !Instance.profile(account, profileId).isNull("expenditureCorrectionKcal") }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.expenditure_title)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aProfileAtItsTargetWeightIsOfferedToKeepItAndMovesToKeepingIt() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala", targetWeightKg = 79.0)
        Instance.chooseWeightUnit(account, "kg")
        Instance.logWeight(account, profileId, 78.5, LocalDate.now())
        screen.startSignedIn(account)

        // The newest weight came down to the one worked towards: the dashboard offers to keep it.
        screen.scrollToText(text(R.string.keep_weight_title))
        screen.scrollToText(text(R.string.keep_weight_note))
        screen.keepScreenshot("dashboard-target-weight-reached")

        // Moved: the pace keeps the weight, and the offer goes.
        screen.onNode(hasText(text(R.string.keep_weight_keep)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForTheApi(20_000) { Instance.profile(account, profileId).isNull("weeklyLossTarget") }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.keep_weight_title)).fetchSemanticsNodes().isEmpty() }
    }

    /** Millilitres as the card says them, in litres kept with their unit. */
    private fun liters(ml: Int): String =
        heldTogether("${NumberFormat.getNumberInstance(Locale.getDefault()).apply { isGroupingUsed = false; maximumFractionDigits = 2 }.format(ml / 1000.0)} ${text(R.string.unit_liter)}")

    @Test
    fun aWeightLoggedWithoutANetworkWaitsAndIsLoggedOnTheServerOnceTheNetworkIsBack() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        Instance.logWeight(account, profileId, 80.6, LocalDate.now().minusDays(3))
        Instance.chooseWeightUnit(account, "kg")
        screen.signIn(account)
        screen.scrollToText(text(R.string.weight_title))
        val field = hasText(text(R.string.weight_today, "kg"))
        screen.scrollTo(field)

        // Without a network the weight is logged all the same: it is the last one, and says that it
        // waits to be sent; the dashboard says it is offline, and how many changes wait.
        screen.airplaneMode(true)
        screen.onNode(field).performScrollTo().performTextInput("81,5")
        screen.putKeyboardAway()
        screen.onNode(hasText(text(R.string.weight_log)) and hasClickAction()).performScrollTo().performClick()
        val today = formatDate(LocalDate.now().toString())
        val kg = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 1 }.format(81.5)
        val latest = "(${text(R.string.weight_remove_latest_label, heldTogether("$kg kg"), today)})"
        screen.waitForText(latest)
        screen.waitForText(text(R.string.change_waiting))
        screen.onNodeWithText(text(R.string.change_waiting)).performScrollTo()
        screen.keepScreenshot("dashboard-a-weight-logged-offline")
        val waiting = hasText(plural(R.plurals.changes_waiting, 1, 1), substring = true)
        screen.scrollTo(waiting)

        // With the network back, the weight goes by itself, the dashboard is no longer offline, and
        // the chart's days are read again, today among them.
        screen.airplaneMode(false)
        screen.waitForTheApi(90_000) { Instance.weights(account, profileId) == listOf(81.5, 80.6) }
        screen.waitUntil(30_000) { screen.onAllNodes(waiting).fetchSemanticsNodes().isEmpty() }
        screen.scrollTo(hasText(latest))
        screen.waitUntil(30_000) { screen.onAllNodesWithText(text(R.string.change_waiting)).fetchSemanticsNodes().isEmpty() }
        screen.scrollTo(hasContentDescription(today, substring = true))
        screen.keepScreenshot("dashboard-a-weight-logged-offline-sent")
    }
}
