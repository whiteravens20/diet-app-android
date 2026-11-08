// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.heldTogether
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.opensSignedInTheBrowser
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Profiles as an account holder makes and keeps them: the first one in three steps from the empty
 * dashboard, with the target the typed figures come to; one edited, its preferences changed in
 * place and deleted; several ingredients of one search ticked and added at once; and the
 * calculator, which begins with a profile's figures. What the screens say is checked against what
 * the API keeps.
 */
@RunWith(AndroidJUnit4::class)
class ProfilesTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** The unit the app shows weights in for an account that has chosen none: that of where the phone is. */
    private val unit = WeightUnit.of(null, Locale.getDefault())

    private fun type(label: String, value: String) = screen.onNodeWithText(label).performScrollTo().performTextInput(value)

    private fun press(label: String) {
        screen.putKeyboardAway()
        screen.onNodeWithText(label).performScrollTo().performClick()
    }

    @Test
    fun aFirstProfileIsSetUpInThreeStepsFromTheEmptyDashboard() {
        val account = Instance.newAccount()
        screen.signIn(account)

        screen.waitForText(text(R.string.dashboard_welcome))
        screen.onNodeWithText(text(R.string.dashboard_create_first_profile_cta)).performClick()
        screen.waitForText(text(R.string.profile_wizard_title))

        type(text(R.string.profile_name), "Ala")
        type(text(R.string.profile_age), "30")
        type(text(R.string.profile_height_cm), "168")
        // About 70 kg in the unit of where the phone is: a body the paces are not held for.
        val weight = if (unit == WeightUnit.LB) "154" else "70"
        type(text(R.string.profile_weight, unit.code), weight)
        screen.onNode(hasText(text(R.string.profile_weight, unit.code)) and hasSetTextAction())
            .assert(!hasStateDescription(text(R.string.common_optional)))
        // What the body comes to is shown as it is typed, said to be no medical advice: maintenance and
        // the figure at rest, as the step chooses no pace. The target follows on the step that does.
        screen.waitForText(text(R.string.calorie_preview_maintenance))
        screen.waitForText(text(R.string.calorie_preview_target_next))
        screen.onAllNodesWithText(text(R.string.calorie_preview_daily_target)).assertCountEquals(0)
        screen.waitForText(text(R.string.calorie_preview_not_advice, text(R.string.calorie_preview_terms)))
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.calorie_preview_target_next)).performScrollTo()
        screen.keepScreenshot("profiles-first-body")
        press(text(R.string.common_next))

        screen.waitForText(text(R.string.profile_wizard_goal_lead))
        screen.waitForText(text(R.string.calorie_preview_daily_target))
        // The weight to work towards and a typed target may stay empty and say so in their outline; a
        // field the body needs does not.
        val optional = hasStateDescription(text(R.string.common_optional))
        screen.onNode(hasText(text(R.string.profile_target_weight, unit.code)) and hasSetTextAction()).assert(optional)
        screen.onNode(hasText(text(R.string.profile_own_target)) and hasSetTextAction()).assert(optional)
        screen.onAllNodesWithText(text(R.string.calorie_preview_target_next)).assertCountEquals(0)
        screen.onNodeWithText(text(R.string.calorie_preview_daily_target)).performScrollTo()
        screen.keepScreenshot("profiles-first-goal")
        press(text(R.string.common_next))

        screen.waitForText(text(R.string.profile_wizard_food_lead))
        press(text(R.string.allergen_peanuts))
        screen.keepScreenshot("profiles-first-food")
        press(text(R.string.profile_create))

        screen.waitForText(text(R.string.profile_wizard_made, "Ala"))
        screen.keepScreenshot("profiles-first-made")
        val made = Instance.profiles(account).single()
        assertEquals("Ala", made.getString("name"))
        assertEquals(unit.toKilograms(weight.toDouble()), made.getDouble("weightKg"), 0.0)
        assertEquals("0.5", made.getString("weeklyLossTarget"))
        assertEquals("peanuts", made.getJSONObject("preferences").getJSONArray("allergens").getString(0))

        // The dashboard shows the target the profile now has.
        val target = Instance.dailyTarget(account, made.getString("id"))
        screen.onNodeWithContentDescription(text(R.string.common_back)).performScrollTo().performClick()
        screen.waitForText(heldTogether("$target ${text(R.string.unit_kcal)}"))
    }

    @Test
    fun aProfileIsEditedItsPreferencesChangedInPlaceAndItIsDeleted() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.signIn(account)

        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.profiles_title)).performClick()
        screen.waitForText("Ala")
        screen.keepScreenshot("profiles-list")

        press(text(R.string.common_edit))
        screen.waitForText(text(R.string.profiles_editing, "Ala"))
        screen.onNodeWithText(text(R.string.profile_age)).performScrollTo().performTextReplacement("31")
        // A choice of neither of the two is kept as it was made.
        press(text(R.string.profile_sex))
        screen.onNode(hasText(text(R.string.sex_non_binary)) and hasAnyAncestor(isPopup())).performClick()
        // What the chosen level of activity means stands under its field, and follows the choice.
        press(text(R.string.profile_activity_level))
        screen.onNode(hasText(text(R.string.activity_very_active)) and hasAnyAncestor(isPopup())).performClick()
        screen.waitForText(text(R.string.profile_activity_hint_very_active))
        screen.keepScreenshot("profiles-what-a-level-means")
        // The water a day may stay empty and says so; the profile drinks more, and is reminded to.
        val water = hasText(text(R.string.profile_water_target)) and hasSetTextAction()
        screen.onNode(water).assert(hasStateDescription(text(R.string.common_optional)))
        screen.onNode(water).performScrollTo().performTextReplacement("2500")
        press(text(R.string.profile_water_reminders))
        screen.onNodeWithText(text(R.string.profile_water_reminders)).performScrollTo()
        screen.keepScreenshot("profiles-water-and-reminders")
        press(text(R.string.profile_save_changes))
        screen.waitForText(text(R.string.profiles_add))

        val edited = Instance.profile(account, profileId)
        assertEquals(31, edited.getInt("age"))
        assertEquals("non_binary", edited.getString("sex"))
        assertEquals("very_active", edited.getString("activityLevel"))
        assertEquals(2500, edited.getInt("waterTargetMl"))
        assertEquals(true, edited.getBoolean("waterReminders"))
        assertEquals(true, edited.getBoolean("cookingReminders"))
        // A weight shown in another unit and left as it was is kept to the hundredth it was.
        assertEquals(80.0, edited.getDouble("weightKg"), 0.0)

        press(text(R.string.allergen_soy))
        screen.waitForTheApi(20_000) {
            Instance.profile(account, profileId).getJSONObject("preferences").getJSONArray("allergens").let { it.length() == 1 && it.getString(0) == "soy" }
        }
        press(text(R.string.cuisine_italian))
        screen.waitForTheApi(20_000) {
            Instance.profile(account, profileId).getJSONObject("preferences").getJSONArray("preferredCuisines").let { it.length() == 1 && it.getString(0) == "italian" }
        }
        screen.keepScreenshot("profiles-preferences")

        press(text(R.string.common_delete))
        screen.onNode(hasText(text(R.string.common_delete)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForText(text(R.string.profile_wizard_title))
        assertEquals(0, Instance.profiles(account).size)
    }

    @Test
    fun severalIngredientsOfOneSearchAreTickedAndAddedAtOnce() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)

        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.profiles_title)).performClick()
        screen.waitForText("Ala")
        screen.onNode(hasSetTextAction() and hasText(text(R.string.profile_favourites)))
            .performScrollTo()
            .performTextInput(if (text(R.string.api_language) == "pl") "ser" else "cheese")
        screen.waitUntil(20_000) { screen.onAllNodes(isToggleable()).fetchSemanticsNodes().size >= 2 }
        screen.putKeyboardAway()

        // Every result is a row a finger can take, as tall as a button.
        val results = screen.onAllNodes(isToggleable())
        results.fetchSemanticsNodes().indices.forEach { results[it].assertHeightIsAtLeast(48.dp) }
        results[0].performScrollTo().performClick()
        results[1].performScrollTo().performClick()
        press(text(R.string.pickers_add_ticked, 2))
        screen.waitForTheApi {
            Instance.profile(account, profileId).optJSONObject("preferences")?.optJSONArray("favoriteIngredientIds")?.length() == 2
        }

        // So is each limit from labels to turn on, in the profile's form.
        press(text(R.string.common_edit))
        screen.waitForText(text(R.string.profiles_editing, "Ala"))
        listOf(
            R.string.profile_label_limit_salt_at_most,
            R.string.profile_label_limit_fiber_at_least,
            R.string.profile_label_limit_saturated_fat_at_most,
            R.string.profile_label_limit_sugars_at_most,
        ).forEach { screen.onNode(hasText(text(it)) and isToggleable()).performScrollTo().assertHeightIsAtLeast(48.dp) }
    }

    @Test
    fun theCalculatorIsTheWebApplicationsAndOpensInTheBrowser() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)

        screen.openTab(text(R.string.nav_more))
        screen.waitForText(text(R.string.web_opens_in_the_browser))
        screen.keepScreenshot("more-the-calculator-in-the-browser")
        opensSignedInTheBrowser("/calculator") {
            screen.onNodeWithText(text(R.string.calculator_title)).performClick()
        }
    }
}
