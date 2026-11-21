// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.HELD_DOT
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSection
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.plural
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.savedStateHolds
import net.whiteravens.dietapp.testing.startSignedIn
import net.whiteravens.dietapp.testing.text
import net.whiteravens.dietapp.testing.waitForText
import net.whiteravens.dietapp.testing.waitForTheApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AiTest {
    @get:Rule
    val screen = createAndroidComposeRule<MainActivity>()

    /** Picks [option] in the list a field of choices opened. */
    private fun pick(option: String) = screen.onNode(hasText(option) and hasAnyAncestor(isPopup())).performClick()

    /**
     * Waits for [shown] on the account's screen and scrolls it into view. That screen is a column the
     * app composes whole, which the screens' helper for a list composed as it scrolls cannot move.
     */
    private fun show(shown: String, timeoutMillis: Long = 20_000) {
        screen.waitForText(shown, timeoutMillis)
        screen.onNodeWithText(shown).performScrollTo()
    }

    @Test
    fun anOwnProviderIsTestedThenSavedAndOneWithAKeyRemovedOnceThatIsConfirmed() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSection(account, R.string.account_ai)
        show(text(R.string.account_ai_providers_none))

        // The instance's own Ollama server, the one address a user's may name where the operator lists
        // no other; nothing answers there on CI, and the screen says what the API says of it.
        screen.onNodeWithText(text(R.string.account_ai_add)).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.account_ai_provider)) and hasText("OpenAI")).performScrollTo().performClick()
        pick("Ollama")
        screen.onNodeWithText(text(R.string.account_ai_ollama_url)).performScrollTo().performTextInput(INSTANCE_OLLAMA)
        screen.onNodeWithText(text(R.string.account_ai_model)).performScrollTo().performTextInput("llama3.1:8b")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.account_ai_test)).performScrollTo().performClick()
        val said = Instance.testProvider(account, JSONObject().put("provider", "ollama").put("baseUrl", INSTANCE_OLLAMA).put("model", "llama3.1:8b"))
        assertFalse(said.getBoolean("ok"))
        show(said.getString("error"), timeoutMillis = 40_000)
        screen.keepScreenshot("ai-provider-tested")

        screen.onNodeWithText(text(R.string.account_ai_save)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiProviders(account).size == 1 }
        val saved = Instance.aiProviders(account).single()
        assertEquals("ollama", saved.getString("provider"))
        assertEquals("llama3.1:8b", saved.getString("model"))
        assertEquals(INSTANCE_OLLAMA, saved.getString("baseUrl"))
        show("Ollama${HELD_DOT}llama3.1:8b")
        show(text(R.string.account_ai_no_key))

        // A provider's key is shown as dots while it is typed, kept out of the saved state, and never
        // shown again. Nothing asks the provider with it.
        val key = "sk-screens-not-a-key-${UUID.randomUUID()}"
        screen.onNodeWithText(text(R.string.account_ai_add)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.account_ai_api_key)).performScrollTo().performTextInput(key)
        screen.onNodeWithText(text(R.string.account_ai_model)).performScrollTo().performTextInput("gpt-4o-mini")
        screen.putKeyboardAway()
        // What the field shows, and what a screen reader is given with its mark of a password, is dots.
        val typed = screen.onNode(hasText(text(R.string.account_ai_api_key)) and hasSetTextAction()).fetchSemanticsNode().config
        assertTrue("the key's field is not a password's", SemanticsProperties.Password in typed)
        assertFalse("the key is shown", typed[SemanticsProperties.EditableText].text.contains(key))
        assertFalse("the key is in the saved state", screen.savedStateHolds(key))
        // Its eye shows the key as it was typed, the mark of a password gone. The saved state holds it shown too, and
        // the stop and the return that its check takes the app through leave it dots again.
        val keyField = screen.onNode(hasText(text(R.string.account_ai_api_key)) and hasSetTextAction())
        screen.onNodeWithContentDescription(text(R.string.common_show_key)).performScrollTo().performClick()
        val shownKey = keyField.fetchSemanticsNode().config
        assertFalse("the shown key keeps the mark of a password", SemanticsProperties.Password in shownKey)
        assertEquals(key, shownKey[SemanticsProperties.EditableText].text)
        screen.keepScreenshot("ai-provider-key-shown")
        assertFalse("the shown key is in the saved state", screen.savedStateHolds(key))
        val back = keyField.fetchSemanticsNode().config
        assertTrue("the key is shown after the app came back", SemanticsProperties.Password in back)
        assertFalse(back[SemanticsProperties.EditableText].text.contains(key))
        screen.onNodeWithContentDescription(text(R.string.common_show_key)).assertExists()
        screen.keepScreenshot("ai-provider-key-typed")
        screen.onNodeWithText(text(R.string.account_ai_save)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiProviders(account).size == 2 }
        show("OpenAI${HELD_DOT}gpt-4o-mini")
        show(text(R.string.account_ai_key_set))
        assertTrue(screen.onAllNodesWithText(key, substring = true).fetchSemanticsNodes().isEmpty())
        screen.keepScreenshot("ai-providers")

        // Its removal, once confirmed, leaves the other.
        screen.onNodeWithContentDescription(text(R.string.account_ai_remove_named, "OpenAI")).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.account_ai_remove)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi { Instance.aiProviders(account).map { it.getString("provider") } == listOf("ollama") }
        screen.waitUntil(20_000) { screen.onAllNodesWithText("OpenAI${HELD_DOT}gpt-4o-mini").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun anOpenRouterKeyIsSavedWithTheEndpointsChosenWhichItsRowNamesAndItsFormStartsFrom() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSection(account, R.string.account_ai)
        show(text(R.string.account_ai_providers_none))

        // OpenRouter alone has the choice of its endpoints, which starts at no training on the data.
        screen.onNodeWithText(text(R.string.account_ai_add)).performScrollTo().performClick()
        assertTrue(screen.onAllNodesWithText(text(R.string.account_ai_data_policy)).fetchSemanticsNodes().isEmpty())
        screen.onNode(hasText(text(R.string.account_ai_provider)) and hasText("OpenAI")).performScrollTo().performClick()
        pick("OpenRouter")
        show(text(R.string.account_ai_data_policy))
        screen.onNode(hasText(text(R.string.account_ai_data_policy_no_training)) and isSelectable()).assertIsSelected()
        screen.onNodeWithText(text(R.string.account_ai_api_key)).performScrollTo().performTextInput("sk-or-screens-not-a-key-${UUID.randomUUID()}")
        screen.onNodeWithText(text(R.string.account_ai_model)).performScrollTo().performTextInput("openai/gpt-6-luna")
        screen.putKeyboardAway()
        val noRetention = hasText(text(R.string.account_ai_data_policy_no_retention)) and isSelectable()
        screen.onNode(noRetention).performScrollTo().performClick()
        screen.onNode(noRetention).assertIsSelected()
        screen.onNodeWithText(text(R.string.account_ai_data_policy_refused)).performScrollTo()
        screen.keepScreenshot("ai-provider-data-policy")

        screen.onNodeWithText(text(R.string.account_ai_save)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiProviders(account).size == 1 }
        assertEquals("no_retention", Instance.aiProviders(account).single().getString("dataPolicy"))
        show("OpenRouter${HELD_DOT}openai/gpt-6-luna")
        show(text(R.string.account_ai_data_policy_no_retention))
        screen.keepScreenshot("ai-provider-data-policy-saved")

        // The form of the key starts from the choice it has, so that saving it again keeps that.
        screen.onNodeWithText(text(R.string.account_ai_add)).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.account_ai_provider)) and hasText("OpenAI")).performScrollTo().performClick()
        pick("OpenRouter")
        show(text(R.string.account_ai_data_policy))
        screen.onNode(noRetention).performScrollTo().assertIsSelected()
        screen.onNode(hasText(text(R.string.common_cancel)) and hasClickAction()).performScrollTo().performClick()
    }

    @Test
    fun theInstancesProviderIsChosenWithItsAllowanceAndTheAccountsOwnOnceItHasOne() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSection(account, R.string.account_ai)

        // A new account has AI off, and its own providers' mode waits for a provider.
        show(text(R.string.account_ai_mode_byok_unavailable))
        screen.keepScreenshot("ai-mode-off")
        val limit = Instance.aiQuota(account).getInt("limit")
        screen.onNodeWithText(text(R.string.account_ai_mode_admin)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiMode(account) == "admin" }
        show(plural(R.plurals.account_ai_allowance_used, limit, 0, limit))
        show(text(R.string.account_ai_allowance_unused))
        show(text(R.string.account_ai_disclaimer))
        screen.keepScreenshot("ai-mode-instance")

        // With a provider of its own, the account's own mode can be chosen.
        screen.onNodeWithText(text(R.string.account_ai_add)).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.account_ai_provider)) and hasText("OpenAI")).performScrollTo().performClick()
        pick("Ollama")
        screen.onNodeWithText(text(R.string.account_ai_ollama_url)).performScrollTo().performTextInput(INSTANCE_OLLAMA)
        screen.onNodeWithText(text(R.string.account_ai_model)).performScrollTo().performTextInput("llama3.1:8b")
        screen.putKeyboardAway()
        screen.onNodeWithText(text(R.string.account_ai_save)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiProviders(account).size == 1 }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.account_ai_mode_byok_unavailable)).fetchSemanticsNodes().isEmpty() }
        screen.onNodeWithText(text(R.string.account_ai_mode_byok)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiMode(account) == "byok" }
        show(text(R.string.account_ai_mode_saved))
        assertTrue(screen.onAllNodesWithText(text(R.string.account_ai_allowance)).fetchSemanticsNodes().isEmpty())

        // Its last provider removed, the API gives the account the instance's provider, and the screen says so.
        screen.onNodeWithContentDescription(text(R.string.account_ai_remove_named, "Ollama")).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.account_ai_remove)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi { Instance.aiMode(account) == "admin" }
        show(plural(R.plurals.account_ai_allowance_used, limit, 0, limit))

        // And off again.
        screen.onNodeWithText(text(R.string.account_ai_mode_none)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiMode(account) == "none" }
        screen.waitUntil(20_000) { screen.onAllNodesWithText(text(R.string.account_ai_disclaimer)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theNoteThatAiIsOffLeadsToTheAccountsSettingsAndWhatAsksAModelWaitsNoLongerOnceItIsOn() {
        val account = Instance.newAccount()
        Instance.newProfile(account)
        screen.startSignedIn(account)
        screen.openTab(text(R.string.nav_recipes))

        // A new account has AI off: the draft by a model is off, and the note says where AI is turned on.
        val note = text(R.string.ai_off_note, text(R.string.ai_off_turn_on))
        val draft = hasText(text(R.string.recipes_ai_draft)) and hasClickAction()
        screen.waitForText(note)
        screen.onNode(draft).assertIsNotEnabled()
        screen.keepScreenshot("ai-off-note")

        screen.onNodeWithText(note).performFirstLinkClick()
        // The link opens the AI section of the settings at once, not their list; back from it leads straight back.
        show(text(R.string.account_ai_mode_admin))
        screen.onAllNodesWithText(text(R.string.account_group_general)).assertCountEquals(0)
        screen.onNodeWithText(text(R.string.account_ai_mode_admin)).performClick()
        screen.waitForTheApi { Instance.aiMode(account) == "admin" }
        screen.onNodeWithContentDescription(text(R.string.common_back)).performScrollTo().performClick()

        // Back on the recipes, the draft waits no longer, and nothing says AI is off.
        screen.waitUntil(20_000) { screen.onAllNodes(draft and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(screen.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty())
        screen.keepScreenshot("ai-on-the-draft-waits-no-longer")
    }

    private companion object {
        /** The instance's own Ollama server, as the settings template CI installs from names it. */
        const val INSTANCE_OLLAMA = "http://ollama:11434"
    }
}
