// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.AiRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.AiProviderConfig
import net.whiteravens.dietapp.domain.AiStatus
import net.whiteravens.dietapp.domain.ConnectionTest
import net.whiteravens.dietapp.domain.ProviderSetup
import net.whiteravens.dietapp.domain.SessionUser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AiViewModelTest {
    private val ollama = AiProviderConfig("ollama-id", "ollama", "mock-queue", 0, true, hasKey = false, keyUnreadable = false, baseUrl = "http://mockai:11434")
    private val openai = AiProviderConfig("openai-id", "openai", "gpt-4o-mini", 1, true, hasKey = true, keyUnreadable = false, baseUrl = null)
    private val listed = ConnectionTest(true, listOf("gpt-4o", "gpt-4o-mini"), null)
    private val off = AiStatus("none", 40, 0, null, null, instanceLimitReached = false, adminProviderConfigured = true)
    private val ai = mockk<AiRepository>()
    private val users = mockk<UserRepository>()

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { ai.status() } returns off
    }

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `the providers are read on opening, and a read that failed is said and made again on asking`() = runTest {
        val offline = IOException("offline")
        coEvery { ai.providers() } throws offline andThen listOf(ollama)
        val model = AiViewModel(ai, users)
        assertSame(offline, model.state.value.readFailed)
        assertNull(model.state.value.providers)

        model.load()

        assertEquals(listOf(ollama), model.state.value.providers)
        assertNull(model.state.value.readFailed)
    }

    @Test
    fun `a provider is saved as the form holds it, and the form closes with the key typed and the list is read again`() = runTest {
        coEvery { ai.providers() } returnsMany listOf(emptyList(), listOf(openai))
        coEvery { ai.save(any()) } returns openai
        val model = AiViewModel(ai, users)

        model.open()
        model.edit { it.copy(model = " gpt-4o-mini ", apiKey = " sk-typed ", priority = "1") }
        model.save()

        coVerify { ai.save(ProviderSetup("openai", "gpt-4o-mini", "sk-typed", null, 1)) }
        assertNull(model.state.value.form)
        assertEquals(listOf(openai), model.state.value.providers)
    }

    @Test
    fun `an Ollama server is saved with its address and without a key typed for another provider`() = runTest {
        coEvery { ai.providers() } returns emptyList()
        coEvery { ai.save(any()) } returns ollama
        val model = AiViewModel(ai, users)

        model.open()
        model.edit { it.copy(apiKey = "sk-typed-first", model = "mock-queue") }
        model.edit { it.copy(provider = "ollama", baseUrl = "http://mockai:11434") }
        model.save()

        coVerify { ai.save(ProviderSetup("ollama", "mock-queue", null, "http://mockai:11434", 0)) }
    }

    @Test
    fun `a form without a model, a key or a server's address the API takes, or with a place out of its bounds, is not sent`() = runTest {
        coEvery { ai.providers() } returns emptyList()
        val model = AiViewModel(ai, users)
        model.open()
        val whole = ProviderForm(provider = "openai", model = "gpt-4o-mini", apiKey = "sk-typed", priority = "0")

        for (form in listOf(
            whole.copy(model = " "),
            whole.copy(apiKey = "  "),
            whole.copy(priority = "101"),
            whole.copy(priority = ""),
            whole.copy(provider = "ollama", baseUrl = "mockai:11434"),
            whole.copy(provider = "ollama", baseUrl = ""),
        )) {
            model.edit { form }
            model.save()
        }

        coVerify(exactly = 0) { ai.save(any()) }
    }

    @Test
    fun `a refusal is said and the form stays as it was typed`() = runTest {
        val refused = ApiException(400, "OLLAMA_HOST_NOT_ALLOWED", "Ollama host is not allowed.")
        coEvery { ai.providers() } returns emptyList()
        coEvery { ai.save(any()) } throws refused
        val model = AiViewModel(ai, users)
        val typed = ProviderForm(provider = "ollama", model = "llama3.1:8b", baseUrl = "http://192.0.2.10:11434")

        model.open()
        model.edit { typed }
        model.save()

        assertSame(refused, model.state.value.saveError)
        assertEquals(typed, model.state.value.form)
        assertFalse(model.state.value.saving)
    }

    @Test
    fun `a test lists the provider's models to choose one of, and a change of the form puts its answer away`() = runTest {
        coEvery { ai.providers() } returns emptyList()
        coEvery { ai.test(any()) } returns listed
        val model = AiViewModel(ai, users)

        model.open()
        model.edit { it.copy(apiKey = "sk-typed") }
        model.test()
        model.chooseModel("gpt-4o")

        coVerify { ai.test(ProviderSetup("openai", "", "sk-typed", null, 0)) }
        assertEquals(listed, model.state.value.tested)
        assertEquals("gpt-4o", model.state.value.form?.model)

        model.edit { it.copy(apiKey = "sk-typed-again") }

        assertNull(model.state.value.tested)
    }

    @Test
    fun `an answer about what the form held before it was changed is not taken for an answer about what it holds`() = runTest {
        val answer = CompletableDeferred<ConnectionTest>()
        coEvery { ai.providers() } returns emptyList()
        coEvery { ai.test(any()) } coAnswers { answer.await() }
        val model = AiViewModel(ai, users)
        model.open()
        model.edit { it.copy(apiKey = "sk-typed") }

        model.test()
        model.edit { it.copy(apiKey = "sk-typed-again") }
        answer.complete(listed)

        assertNull(model.state.value.tested)
        assertFalse(model.state.value.testing)
    }

    @Test
    fun `the provider of the kind the form holds is named as the one saving replaces`() = runTest {
        coEvery { ai.providers() } returns listOf(ollama, openai)
        val model = AiViewModel(ai, users)

        model.open()
        assertEquals(openai, model.state.value.replaced)

        model.edit { it.copy(provider = "anthropic") }
        assertNull(model.state.value.replaced)
    }

    @Test
    fun `a provider is removed and the list read again, and a removal refused is said with the list read again`() = runTest {
        val gone = ApiException(404, "AI_CONFIG_NOT_FOUND", "Provider config not found.")
        coEvery { ai.providers() } returnsMany listOf(listOf(ollama, openai), listOf(ollama), emptyList())
        coEvery { ai.remove("openai-id") } returns Unit
        coEvery { ai.remove("ollama-id") } throws gone
        val model = AiViewModel(ai, users)

        model.remove("openai-id")
        assertEquals(listOf(ollama), model.state.value.providers)
        assertNull(model.state.value.removeError)

        model.remove("ollama-id")
        assertSame(gone, model.state.value.removeError)
        assertEquals(emptyList<AiProviderConfig>(), model.state.value.providers)
    }

    private fun account(aiMode: String) = SessionUser("account", "ada@example.test", "Ada", emailVerified = true, locale = "en", aiMode = aiMode)

    @Test
    fun `the modes are offered as far as the instance and the account's providers allow`() = runTest {
        coEvery { ai.providers() } returns emptyList()
        val shared = AiViewModel(ai, users).state.value

        assertEquals(listOf("none", "admin", "byok"), shared.modes)
        assertTrue(shared.canChoose("admin"))
        assertFalse(shared.canChoose("byok"))
        assertTrue(shared.copy(providers = listOf(openai)).canChoose("byok"))
        assertFalse(shared.copy(providers = listOf(openai.copy(enabled = false))).canChoose("byok"))

        // Where the operator shares no provider, its mode is offered only to an account that has it.
        val notShared = shared.copy(status = off.copy(limit = 0))
        assertEquals(listOf("none", "byok"), notShared.modes)
        val stillHad = shared.copy(status = off.copy(mode = "admin", adminProviderConfigured = false))
        assertEquals(listOf("none", "admin", "byok"), stillHad.modes)
        assertFalse(stillHad.canChoose("admin"))
    }

    @Test
    fun `a mode chosen is shown at once and kept as the API answers, and the allowance is read again for it`() = runTest {
        val kept = CompletableDeferred<SessionUser>()
        val shared = off.copy(mode = "admin", remaining = 40)
        coEvery { ai.providers() } returns emptyList()
        coEvery { ai.status() } returnsMany listOf(off, shared)
        coEvery { users.chooseAiMode("admin") } coAnswers { kept.await() }
        val model = AiViewModel(ai, users)

        model.chooseMode("admin")
        assertEquals("admin", model.state.value.mode)
        assertEquals("admin", model.state.value.choosing)

        kept.complete(account("admin"))

        assertEquals(shared, model.state.value.status)
        assertNull(model.state.value.choosing)
        assertTrue(model.state.value.modeSaved)
    }

    @Test
    fun `a mode refused is said, and the mode stays as the account has it`() = runTest {
        val refused = ApiException(400, "BYOK_NO_PROVIDER", "Add at least one provider key before switching to bring-your-own.")
        coEvery { ai.providers() } returns listOf(openai)
        coEvery { users.chooseAiMode("byok") } throws refused
        val model = AiViewModel(ai, users)

        model.chooseMode("byok")

        assertSame(refused, model.state.value.modeError)
        assertEquals("none", model.state.value.mode)
        assertFalse(model.state.value.modeSaved)
    }

    @Test
    fun `the account's own mode is not asked for while it has no provider, nor the instance's where the operator shares none`() = runTest {
        coEvery { ai.providers() } returns emptyList()
        coEvery { ai.status() } returns off.copy(limit = 0)
        val model = AiViewModel(ai, users)

        model.chooseMode("byok")
        model.chooseMode("admin")

        coVerify(exactly = 0) { users.chooseAiMode(any()) }
    }

    @Test
    fun `once the last provider is removed, the mode is read again, as the API gives the account the instance's provider`() = runTest {
        coEvery { ai.providers() } returnsMany listOf(listOf(openai), emptyList())
        coEvery { ai.status() } returnsMany listOf(off.copy(mode = "byok"), off.copy(mode = "admin", remaining = 40))
        coEvery { ai.remove("openai-id") } returns Unit
        val model = AiViewModel(ai, users)
        assertEquals("byok", model.state.value.mode)

        model.remove("openai-id")

        assertEquals("admin", model.state.value.mode)
        assertEquals(emptyList<AiProviderConfig>(), model.state.value.providers)
    }
}
