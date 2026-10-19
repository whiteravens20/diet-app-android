// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.opensInTheBrowser
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollTo
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import net.whiteravens.dietapp.testing.webApp
import net.whiteravens.dietapp.ui.common.formatDate
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
        opensInTheBrowser("${webApp}dashboard") {
            screen.onNodeWithText(text(R.string.web_open)).performScrollTo().performClick()
        }

        // A profile with no plan is offered to make one, and the form for it opens.
        screen.waitForText(text(R.string.overview_none))
        screen.onNodeWithText(text(R.string.overview_none_cta)).performScrollTo().performClick()
        screen.waitForText(text(R.string.plans_start_date))
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
        screen.keepScreenshot("dashboard-a-weight-logged")

        // The last entry taken back once it is confirmed; the one before it stays, and is the one offered next.
        screen.onNodeWithText(text(R.string.weight_remove_latest)).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.common_delete)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi(20_000) { Instance.weights(account, profileId) == listOf(80.6) }
        val before = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 1 }.format(80.6)
        screen.waitForText("(${text(R.string.weight_remove_latest_label, "$before kg", formatDate(threeDaysAgo.toString()))})")
    }
}
