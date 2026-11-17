// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.scrollToText
import net.whiteravens.dietapp.testing.signIn
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForEnabled
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The custom diet's targets as an account holder sets them: in the profile's form the split in per cent and in
 * grams and protein per kilogram, each kept as it is made and a target past a limit said and not kept, and the
 * dashboard saying where a heavier body holds protein at a limit; and the form
 * of a new plan, which asks for the targets a profile does not have, says where its own calorie target holds protein
 * at a limit, and keeps the targets in the profile before the plan is made to them.
 */
@RunWith(AndroidJUnit4::class)
class OwnTargetsTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    private fun press(label: String) {
        screen.putKeyboardAway()
        screen.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun field(label: String) = hasText(label) and hasSetTextAction()

    private fun typeInto(label: String, value: String) {
        screen.onNode(field(label)).performScrollTo().performTextReplacement(value)
    }

    /** Leaves a field as the keyboard's key does: the form keeps what it holds. */
    private fun leave(label: String) {
        screen.onNode(field(label)).performImeAction()
    }

    /** The beginning of a text of the app's that holds figures, up to its first one, read as it is written. */
    private fun startOf(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id).substringBefore("%")

    @Test
    fun theCustomDietsTargetsAreSetAsASplitInPerCentOrGramsOrAsProteinPerKilogramAndKeptAsTheyAreMade() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account, name = "Ala")
        screen.startSignedIn(account)
        screen.openTab(text(R.string.nav_more))
        screen.onNodeWithText(text(R.string.profiles_title)).performClick()
        screen.waitForText("Ala")
        press(text(R.string.common_edit))
        screen.waitForText(text(R.string.profiles_editing, "Ala"))

        // The custom diet, chosen, is kept with the split the form begins with.
        press(text(R.string.profile_diet_type))
        screen.onNode(hasText(text(R.string.diet_custom)) and hasAnyAncestor(isPopup())).performClick()
        screen.waitForTheApi(20_000) { Instance.profile(account, profileId).getString("dietType") == "custom" }
        screen.onNodeWithText(text(R.string.profile_custom_split)).performScrollTo()
        screen.keepScreenshot("own-targets-split")

        // In grams, which begin at what the split comes to at the target, and are kept as the shares of their energy.
        press(text(R.string.profile_custom_unit_grams))
        typeInto(text(R.string.profile_split_grams_protein), "150")
        leave(text(R.string.profile_split_grams_protein))
        screen.waitForTheApi(20_000) { Instance.profile(account, profileId).getJSONObject("customMacroSplit").getInt("protein") != 25 }
        screen.putKeyboardAway()
        screen.waitForText(startOf(R.string.profile_grams_shares), substring = true)
        screen.onNodeWithText(text(R.string.profile_custom_unit)).performScrollTo()
        screen.keepScreenshot("own-targets-grams")

        // Protein per kilogram, kept the moment the way is chosen.
        press(text(R.string.profile_custom_way_per_kg))
        screen.waitForTheApi(20_000) { Instance.profile(account, profileId).optDouble("customProteinPerKg") == 1.6 }
        screen.waitForText(startOf(R.string.profile_per_kg_line), substring = true)
        screen.onNodeWithText(text(R.string.profile_custom_way)).performScrollTo()
        screen.keepScreenshot("own-targets-per-kg")

        // Fat at 70 % is kept with 1.6 g; 3 g a kilogram of 80 kg then leaves carbohydrate under its 5 %: said, with the
        // grams that fit, and not kept.
        typeInto(text(R.string.profile_per_kg_fat), "70")
        leave(text(R.string.profile_per_kg_fat))
        screen.waitForTheApi(20_000) { Instance.profile(account, profileId).getJSONObject("customMacroSplit").getInt("fat") == 70 }
        typeInto(text(R.string.profile_per_kg_protein), "3")
        leave(text(R.string.profile_per_kg_protein))
        screen.putKeyboardAway()
        screen.waitForText(startOf(R.string.profile_per_kg_fits), substring = true)
        screen.onNodeWithText(text(R.string.profile_custom_way)).performScrollTo()
        screen.keepScreenshot("own-targets-per-kg-held")
        assertEquals(1.6, Instance.profile(account, profileId).getDouble("customProteinPerKg"), 0.0)

        // The most that line says fits (1.7 g at 70 % of fat) is kept, and so is the fat typed beside it.
        typeInto(text(R.string.profile_per_kg_protein), "1,7")
        leave(text(R.string.profile_per_kg_protein))
        screen.waitForTheApi(20_000) { Instance.profile(account, profileId).optDouble("customProteinPerKg") == 1.7 }
        typeInto(text(R.string.profile_per_kg_fat), "30")
        leave(text(R.string.profile_per_kg_fat))
        screen.waitForTheApi(20_000) { Instance.profile(account, profileId).getJSONObject("customMacroSplit").getInt("fat") == 30 }
        press(text(R.string.profile_autosave_done))
        screen.waitForText(text(R.string.profiles_add))
        // The dashboard is reached from the bar, which the profiles' screen does not show.
        screen.onNodeWithContentDescription(text(R.string.common_back)).performScrollTo().performClick()

        // 3 g a kilogram fits at 80 kg; at 150 kg it goes past the 45 % of protein: the dashboard says it is held there.
        Instance.changeProfile(account, profileId, JSONObject().put("customProteinPerKg", 3))
        Instance.changeProfile(account, profileId, JSONObject().put("weightKg", 150))
        screen.openTab(text(R.string.tab_dashboard))
        screen.scrollToText(text(R.string.dashboard_macro_split))
        screen.waitForText(text(R.string.calorie_preview_split_held))
        screen.onNodeWithText(text(R.string.calorie_preview_split_held)).performScrollTo()
        screen.keepScreenshot("own-targets-dashboard-held")
    }

    @Test
    fun aPlanToTheCustomDietAsksForTheTargetsAProfileHasNotSaysWhereItsOwnTargetHoldsProteinAndKeepsThem() {
        val account = Instance.newAccount()
        val profileId = Instance.newProfile(account)
        screen.signIn(account)
        screen.openTab(text(R.string.tab_meal_plans))
        screen.waitForEnabled(text(R.string.plans_generate))
        screen.onNode(hasText(text(R.string.plans_generate)) and hasClickAction()).performClick()
        screen.waitForText(text(R.string.plans_start_date))

        // The custom diet is on offer to a profile without targets, and the form asks for them.
        press(text(R.string.plans_diet))
        screen.onNode(hasText(text(R.string.diet_custom)) and hasAnyAncestor(isPopup())).performClick()
        screen.waitForText(text(R.string.plans_own_targets_saved))
        screen.onNodeWithText(text(R.string.profile_custom_split)).performScrollTo()
        screen.keepScreenshot("own-targets-plan-asks")

        // 2.5 g a kilogram of 80 kg is 800 kcal: it fits the profile's target, and at a target of 1200 the plan holds it.
        press(text(R.string.profile_custom_way_per_kg))
        typeInto(text(R.string.profile_per_kg_protein), "2,5")
        typeInto(text(R.string.plans_calorie_target), "1200")
        screen.putKeyboardAway()
        screen.waitForText(startOf(R.string.plans_per_kg_held_at_plan), substring = true)
        screen.onNodeWithText(startOf(R.string.plans_per_kg_held_at_plan), substring = true).performScrollTo()
        screen.keepScreenshot("own-targets-plan-held")

        // Without a target of its own, the plan is made to the targets, which the profile keeps first.
        typeInto(text(R.string.plans_calorie_target), "")
        screen.putKeyboardAway()
        screen.onNode(hasText(text(R.string.plans_generate_button)) and hasClickAction()).performScrollTo().performClick()
        screen.waitForTheApi(60_000) { Instance.planIds(account, profileId).isNotEmpty() }
        val kept = Instance.profile(account, profileId)
        assertEquals(2.5, kept.getDouble("customProteinPerKg"), 0.0)
        assertEquals("balanced", kept.getString("dietType"))
    }
}
