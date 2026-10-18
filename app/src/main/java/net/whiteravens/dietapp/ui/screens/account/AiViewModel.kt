// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.AiRepository
import net.whiteravens.dietapp.domain.AiProviderConfig
import net.whiteravens.dietapp.domain.ConnectionTest
import net.whiteravens.dietapp.domain.ProviderSetup
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * The AI providers the account brings of its own, as the web's settings show them: listed with
 * whether their keys can still be read, one set up in a form whose connection can be tested before
 * it is saved, and one removed. The key typed is kept here, not in the screen's saved state, which
 * the system may write to disk, and goes once the provider is saved or the form closed.
 */
@HiltViewModel
class AiViewModel @Inject constructor(private val ai: AiRepository) : ViewModel() {

    data class UiState(
        /** The providers the account set up; null until they were read. */
        val providers: List<AiProviderConfig>? = null,
        val readFailed: Throwable? = null,
        /** A provider being set up; null while the form is closed. */
        val form: ProviderForm? = null,
        val saving: Boolean = false,
        val saveError: Throwable? = null,
        val testing: Boolean = false,
        /** How the provider answered a test of what the form holds; null where it was not tested since. */
        val tested: ConnectionTest? = null,
        val testError: Throwable? = null,
        /** A provider's removal is on its way. */
        val removing: Boolean = false,
        val removeError: Throwable? = null,
    ) {
        /** The account's provider of the form's kind, which saving the form replaces; null where it has none. */
        val replaced: AiProviderConfig?
            get() = form?.let { form -> providers.orEmpty().firstOrNull { it.provider == form.provider } }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reads the account's providers again. */
    fun load() {
        viewModelScope.launch { readProviders() }
    }

    /** Opens the form for a provider to be set up. */
    fun open() = _state.update { it.copy(form = ProviderForm(), saveError = null, tested = null, testError = null) }

    /** Closes the form, and what was typed in it goes. */
    fun cancel() = _state.update { it.copy(form = null, saveError = null, tested = null, testError = null) }

    /** Changes what the form holds; a test of what it held before no longer says anything of it. */
    fun edit(change: (ProviderForm) -> ProviderForm) =
        _state.update { state -> state.form?.let { state.copy(form = change(it), tested = null, testError = null) } ?: state }

    /** Takes one of the models the provider listed when its connection was tested. */
    fun chooseModel(model: String) = _state.update { state -> state.form?.let { state.copy(form = it.copy(model = model)) } ?: state }

    /** Asks the provider whether it answers to what the form holds, and which models it has. */
    fun test() {
        val state = _state.value
        val form = state.form ?: return
        if (state.testing || !form.testable) return
        _state.update { it.copy(testing = true, tested = null, testError = null) }
        viewModelScope.launch {
            val answered = attempt { ai.test(form.asked()) }
            // An answer about what the form held before it was changed says nothing of what it holds.
            _state.update {
                if (it.form != form) it.copy(testing = false) else it.copy(testing = false, tested = answered.getOrNull(), testError = answered.exceptionOrNull())
            }
        }
    }

    /** Saves the provider the form holds; the form closes and the list is read again, or the refusal is said and the form stays. */
    fun save() {
        val state = _state.value
        val setup = state.form?.setup() ?: return
        if (state.saving) return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            attempt { ai.save(setup) }
                .onSuccess {
                    _state.update { it.copy(saving = false, form = null, tested = null, testError = null) }
                    readProviders()
                }
                .onFailure { e -> _state.update { it.copy(saving = false, saveError = e) } }
        }
    }

    /** Removes the provider [id]; the list is read again either way, so that one gone already goes from it too. */
    fun remove(id: String) {
        if (_state.value.removing) return
        _state.update { it.copy(removing = true, removeError = null) }
        viewModelScope.launch {
            val removed = attempt { ai.remove(id) }
            _state.update { it.copy(removing = false, removeError = removed.exceptionOrNull()) }
            readProviders()
        }
    }

    private suspend fun readProviders() {
        attempt { ai.providers() }
            .onSuccess { providers -> _state.update { it.copy(providers = providers, readFailed = null) } }
            .onFailure { e -> _state.update { it.copy(readFailed = e) } }
    }
}

/**
 * A provider of the account's own as its form holds it: which, the model to ask, its key or its
 * Ollama server's address, and its place in the order providers are asked in, as typed.
 */
data class ProviderForm(
    val provider: String = PROVIDERS.first(),
    val model: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val priority: String = "0",
) {
    /** Ollama is reached at an address and takes no key; every other provider takes a key. */
    val ollama: Boolean
        get() = provider == "ollama"

    /** The place in the order as a number the API takes, from 0 to 100; null for anything else. */
    val place: Int?
        get() = priority.toIntOrNull()?.takeIf { it in 0..MOST_PRIORITY }

    /** Whether the address typed is one the API takes for an Ollama server: plain or secure HTTP. */
    val addressValid: Boolean
        get() = ADDRESS.matches(baseUrl.trim()) && baseUrl.trim().length <= MOST_CHARACTERS

    /** Whether there is something to test: a key, or an Ollama server's address. */
    val testable: Boolean
        get() = if (ollama) addressValid else apiKey.isNotBlank()

    /** The provider as it is saved; null while the model, the key or the address is missing or one the API would refuse. */
    fun setup(): ProviderSetup? {
        val chosen = model.trim()
        val place = place ?: return null
        if (chosen.isEmpty() || chosen.length > MOST_MODEL_CHARACTERS || !testable || apiKey.trim().length > MOST_CHARACTERS) return null
        return asked().copy(model = chosen, priority = place)
    }

    /** What a test sends: the key or the address, and the model where one is typed, which a provider that lists none needs. */
    fun asked() = ProviderSetup(
        provider = provider,
        model = model.trim(),
        apiKey = apiKey.trim().takeUnless { ollama },
        baseUrl = baseUrl.trim().takeIf { ollama },
        priority = place ?: 0,
    )

    companion object {
        /** The providers an account can bring of its own, in the web's order. */
        val PROVIDERS = listOf("openai", "anthropic", "openrouter", "ollama")

        /** The highest place a provider takes in the order. */
        const val MOST_PRIORITY = 100

        /** The longest key or address the API keeps. */
        const val MOST_CHARACTERS = 500

        /** The longest name of a model the API keeps. */
        const val MOST_MODEL_CHARACTERS = 200

        private val ADDRESS = Regex("https?://\\S+")
    }
}
