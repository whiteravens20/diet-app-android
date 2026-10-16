// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.util.Locale

/**
 * Profiles as an account holder makes and keeps them: the first one in three steps from the empty
 * dashboard, with the target the typed figures come to; one edited, its preferences changed in
 * place and deleted; and the calculator, which begins with a profile's figures. What the screens
 * say is checked against what the API keeps.
 */
@RunWith(AndroidJUnit4::class)
class ProfilesTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** The unit the app shows weights in for an account that has chosen none: that of where the phone is. */
    private val unit = WeightUnit.of(null, Locale.getDefault())

    private fun kcal(value: Int) = text(R.string.calorie_preview_kcal, NumberFormat.getNumberInstance(Locale.getDefault()).format(value))

    private fun type(label: String, value: String) = screen.onNodeWithText(label).performScrollTo().performTextInput(value)

    private fun press(label: String) = screen.onNodeWithText(label).performScrollTo().performClick()

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
        type(text(R.string.profile_weight, unit.code), "70")
        // What the figures come to is shown as they are typed.
        screen.waitForText(text(R.string.calorie_preview_daily_target))
        screen.keepScreenshot("profiles-first-body")
        press(text(R.string.common_next))

        screen.waitForText(text(R.string.profile_wizard_goal_lead))
        press(text(R.string.common_next))

        screen.waitForText(text(R.string.profile_wizard_food_lead))
        press(text(R.string.allergen_peanuts))
        screen.keepScreenshot("profiles-first-food")
        press(text(R.string.profile_create))

        screen.waitForText(text(R.string.profile_wizard_made, "Ala"))
        screen.keepScreenshot("profiles-first-made")
        val made = Instance.profiles(account).single()
        assertEquals("Ala", made.getString("name"))
        assertEquals(unit.toKilograms(70.0), made.getDouble("weightKg"), 0.0)
        assertEquals("0.5", made.getString("weeklyLossTarget"))
        assertEquals("peanuts", made.getJSONObject("preferences").getJSONArray("allergens").getString(0))

        // The dashboard shows the target the profile now has.
        val target = Instance.dailyTarget(account, made.getString("id"))
        screen.onNodeWithContentDescription(text(R.string.common_back)).performScrollTo().performClick()
        screen.waitForText("$target ${text(R.string.unit_kcal)}")
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
        press(text(R.string.profile_save_changes))
        screen.waitForText(text(R.string.profiles_add))

        val edited = Instance.profile(account, profileId)
        assertEquals(31, edited.getInt("age"))
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
    fun theCalculatorBeginsWithAProfileAndShowsTheTargetTheApiWorksOut() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        val target = Instance.dailyTarget(account, profileId)
        screen.signIn(account)

        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.calculator_title)).performClick()

        screen.waitForText(kcal(target))
        screen.keepScreenshot("calculator")
    }
}
