// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
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
        screen.waitForText("$target ${text(R.string.unit_kcal)}")
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
        screen.waitForText("(${text(R.string.weight_remove_latest_label, "$before kg", formatDate(threeDaysAgo.toString()))})")
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

        // The icon's shortcuts are the card's amounts, and one adds to the profile watered last without a screen.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            listOf("+200 ml", "+250 ml", "+330 ml"),
            ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }.map { it.shortLabel.toString().replace('\u00A0', ' ') },
        )
        context.startActivity(WaterShortcutActivity.adding(context, 330).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        screen.waitForTheApi { Instance.water(account, profileId, today) == 580 }
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
        val latest = "(${text(R.string.weight_remove_latest_label, "$kg kg", today)})"
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
