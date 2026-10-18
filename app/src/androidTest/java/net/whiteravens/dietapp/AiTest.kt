// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.testing.Instance
import net.whiteravens.dietapp.testing.keepScreenshot
import net.whiteravens.dietapp.testing.openAccountSettings
import net.whiteravens.dietapp.testing.openTab
import net.whiteravens.dietapp.testing.putKeyboardAway
import net.whiteravens.dietapp.testing.savedStateHolds
import net.whiteravens.dietapp.testing.scrollToText
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

    @Test
    fun anOwnProviderIsTestedThenSavedAndOneWithAKeyRemovedOnceThatIsConfirmed() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)
        screen.scrollToText(text(R.string.account_ai_providers_none))

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
        screen.scrollToText(said.getString("error"), timeoutMillis = 40_000)
        screen.keepScreenshot("ai-provider-tested")

        screen.onNodeWithText(text(R.string.account_ai_save)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiProviders(account).size == 1 }
        val saved = Instance.aiProviders(account).single()
        assertEquals("ollama", saved.getString("provider"))
        assertEquals("llama3.1:8b", saved.getString("model"))
        assertEquals(INSTANCE_OLLAMA, saved.getString("baseUrl"))
        screen.scrollToText("Ollama · llama3.1:8b")
        screen.scrollToText(text(R.string.account_ai_no_key))

        // A provider's key is shown as dots while it is typed, kept out of the saved state, and never
        // shown again. Nothing asks the provider with it.
        val key = "sk-screens-not-a-key-${UUID.randomUUID()}"
        screen.onNodeWithText(text(R.string.account_ai_add)).performScrollTo().performClick()
        screen.onNodeWithText(text(R.string.account_ai_api_key)).performScrollTo().performTextInput(key)
        screen.onNodeWithText(text(R.string.account_ai_model)).performScrollTo().performTextInput("gpt-4o-mini")
        screen.putKeyboardAway()
        assertTrue(screen.onAllNodesWithText(key, substring = true).fetchSemanticsNodes().isEmpty())
        assertFalse("the key is in the saved state", screen.savedStateHolds(key))
        screen.keepScreenshot("ai-provider-key-typed")
        screen.onNodeWithText(text(R.string.account_ai_save)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiProviders(account).size == 2 }
        screen.scrollToText("OpenAI · gpt-4o-mini")
        screen.scrollToText(text(R.string.account_ai_key_set))
        assertTrue(screen.onAllNodesWithText(key, substring = true).fetchSemanticsNodes().isEmpty())
        screen.keepScreenshot("ai-providers")

        // Its removal, once confirmed, leaves the other.
        screen.onNodeWithContentDescription(text(R.string.account_ai_remove_named, "OpenAI")).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.account_ai_remove)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi { Instance.aiProviders(account).map { it.getString("provider") } == listOf("ollama") }
        screen.waitUntil(20_000) { screen.onAllNodesWithText("OpenAI · gpt-4o-mini").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theInstancesProviderIsChosenWithItsAllowanceAndTheAccountsOwnOnceItHasOne() {
        val account = Instance.newAccount()
        screen.startSignedIn(account)
        screen.openAccountSettings(account)

        // A new account has AI off, and its own providers' mode waits for a provider.
        screen.scrollToText(text(R.string.account_ai_mode_byok_unavailable))
        screen.keepScreenshot("ai-mode-off")
        val limit = Instance.aiQuota(account).getInt("limit")
        screen.onNodeWithText(text(R.string.account_ai_mode_admin)).performScrollTo().performClick()
        screen.waitForTheApi { Instance.aiMode(account) == "admin" }
        screen.scrollToText(text(R.string.account_ai_allowance_used, 0, limit))
        screen.scrollToText(text(R.string.account_ai_allowance_unused))
        screen.scrollToText(text(R.string.account_ai_disclaimer))
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
        screen.scrollToText(text(R.string.account_ai_mode_saved))
        assertTrue(screen.onAllNodesWithText(text(R.string.account_ai_allowance)).fetchSemanticsNodes().isEmpty())

        // Its last provider removed, the API gives the account the instance's provider, and the screen says so.
        screen.onNodeWithContentDescription(text(R.string.account_ai_remove_named, "Ollama")).performScrollTo().performClick()
        screen.onNode(hasText(text(R.string.account_ai_remove)) and hasAnyAncestor(isDialog())).performClick()
        screen.waitForTheApi { Instance.aiMode(account) == "admin" }
        screen.scrollToText(text(R.string.account_ai_allowance_used, 0, limit))

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
        screen.waitForText(account.email)
        screen.onNodeWithText(text(R.string.account_ai_mode_admin)).performScrollTo().performClick()
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
