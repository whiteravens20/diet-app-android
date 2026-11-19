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
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.data.repository.AiRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.AiDataPolicies
import net.whiteravens.dietapp.domain.AiModes
import net.whiteravens.dietapp.domain.AiProviderConfig
import net.whiteravens.dietapp.domain.AiStatus
import net.whiteravens.dietapp.domain.ConnectionTest
import net.whiteravens.dietapp.domain.ProviderSetup
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * What AI the account has, as the web's settings show it: the mode it chose, offered as far as the
 * instance and the account's providers allow, with what is left of the instance's allowance; and the
 * providers it brings of its own, listed with whether their keys can still be read, one set up in a
 * form whose connection can be tested before it is saved, one changed in the same form, its key kept
 * unless another is typed, and one removed. The key typed is kept here, not in the screen's saved
 * state, which the system may write to disk, and goes once the provider is saved or the form closed.
 */
@HiltViewModel
class AiViewModel @Inject constructor(private val ai: AiRepository, private val users: UserRepository) : ViewModel() {

    data class UiState(
        /** The mode the account chose and the instance's allowance; null until they were read. */
        val status: AiStatus? = null,
        val statusFailed: Throwable? = null,
        /** The mode chosen and on its way to the API, shown at once; null while none is. */
        val choosing: String? = null,
        /** The mode chosen last was kept. */
        val modeSaved: Boolean = false,
        val modeError: Throwable? = null,
        /** The providers the account set up; null until they were read. */
        val providers: List<AiProviderConfig>? = null,
        val readFailed: Throwable? = null,
        /** A provider being set up; null while the form is closed. */
        val form: ProviderForm? = null,
        val saving: Boolean = false,
        val saveError: Throwable? = null,
        /** The fields of the form the API refused, by the name it gives them, until the form changes. */
        val refused: Map<String, FieldRefusal> = emptyMap(),
        val testing: Boolean = false,
        /** How the provider answered a test of what the form holds; null where it was not tested since. */
        val tested: ConnectionTest? = null,
        val testError: Throwable? = null,
        /** A provider's removal is on its way. */
        val removing: Boolean = false,
        val removeError: Throwable? = null,
    ) {
        /** The mode as the account has it, or as it was just chosen; null until it was read. */
        val mode: String?
            get() = choosing ?: status?.mode

        /**
         * The modes offered, as the web offers them: the instance's provider only where the operator
         * shares one, or while the account has it, so that what it has is never hidden.
         */
        val modes: List<String>
            get() = when {
                status == null -> emptyList()
                status.shared || status.mode == AiModes.INSTANCE -> listOf(AiModes.NONE, AiModes.INSTANCE, AiModes.OWN)
                else -> listOf(AiModes.NONE, AiModes.OWN)
            }

        /** Whether the account has a provider of its own that is on, which its own mode asks. */
        val ownProvider: Boolean
            get() = providers.orEmpty().any { it.enabled }

        /**
         * Whether [mode] can be chosen now: the instance's where it is shared and the operator lets
         * the account use it, the account's own once it has a provider.
         */
        fun canChoose(mode: String): Boolean = choosing == null && status != null && when (mode) {
            AiModes.INSTANCE -> status.shared && status.granted
            AiModes.OWN -> ownProvider
            else -> true
        }

        /**
         * The account's provider of the form's kind, which saving the form replaces; null where it has
         * none, or where the form was opened from it to change it.
         */
        val replaced: AiProviderConfig?
            get() = form?.takeUnless { it.fixed }?.let { form -> providers.orEmpty().firstOrNull { it.provider == form.provider } }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reads what AI the account has and its providers again. */
    fun load() {
        viewModelScope.launch {
            readStatus()
            readProviders()
        }
    }

    /**
     * Gives the account the mode [mode]: shown at once, and kept as the API answers; the allowance is
     * read again, as what is left of it is the mode's. A refusal is said and the mode stays as it was.
     */
    fun chooseMode(mode: String) {
        val state = _state.value
        if (mode == state.mode || !state.canChoose(mode)) return
        _state.update { it.copy(choosing = mode, modeSaved = false, modeError = null) }
        viewModelScope.launch {
            attempt { users.chooseAiMode(mode) }
                .onSuccess { saved ->
                    _state.update { it.copy(status = it.status?.copy(mode = saved.aiMode), choosing = null, modeSaved = true) }
                    readStatus()
                }
                .onFailure { e -> _state.update { it.copy(choosing = null, modeError = e) } }
        }
    }

    /**
     * Opens the form for a provider to be set up, with the choice of OpenRouter's endpoints the
     * account's OpenRouter configuration has, so that saving it again does not change that choice.
     */
    fun open() = _state.update { state ->
        val chosen = state.providers.orEmpty().firstOrNull { it.provider == "openrouter" }?.dataPolicy
        val form = ProviderForm(dataPolicy = chosen ?: AiDataPolicies.NO_TRAINING)
        state.copy(
            form = form.copy(keyKept = keyKept(state.providers, form.provider)),
            saveError = null,
            tested = null,
            testError = null,
            refused = emptyMap(),
        )
    }

    /**
     * Opens the form for the saved [provider] to be changed: from its model, place, choice of
     * OpenRouter's endpoints and Ollama server's address, the provider fixed, and its key kept unless
     * another is typed, where it can still be read.
     */
    fun change(provider: AiProviderConfig) = _state.update { state ->
        state.copy(
            form = ProviderForm(
                provider = provider.provider,
                model = provider.model,
                baseUrl = provider.baseUrl.orEmpty(),
                priority = provider.priority.toString(),
                dataPolicy = provider.dataPolicy ?: AiDataPolicies.NO_TRAINING,
                fixed = true,
                keyKept = keyKept(state.providers, provider.provider),
            ),
            saveError = null,
            tested = null,
            testError = null,
            refused = emptyMap(),
        )
    }

    /** Closes the form, and what was typed in it goes. */
    fun cancel() = _state.update { it.copy(form = null, saveError = null, tested = null, testError = null, refused = emptyMap()) }

    /**
     * Changes what the form holds, its provider kept where the form changes a saved one; a test of what
     * it held before no longer says anything of it.
     */
    fun edit(change: (ProviderForm) -> ProviderForm) = _state.update { state ->
        state.form?.let { held ->
            val changed = change(held).let { if (held.fixed) it.copy(provider = held.provider) else it }
            state.copy(
                form = changed.copy(keyKept = keyKept(state.providers, changed.provider)),
                tested = null,
                testError = null,
                refused = emptyMap(),
            )
        } ?: state
    }

    /** Takes one of the models the provider listed when its connection was tested. */
    fun chooseModel(model: String) = _state.update { state -> state.form?.let { state.copy(form = it.copy(model = model)) } ?: state }

    /** Takes a choice of OpenRouter's endpoints, which a test of the connection does not ask about, so its answer stays. */
    fun choosePolicy(policy: String) = _state.update { state -> state.form?.let { state.copy(form = it.copy(dataPolicy = policy)) } ?: state }

    /** Asks the provider whether it answers to what the form holds, and which models it has. */
    fun test() {
        val state = _state.value
        val form = state.form ?: return
        if (state.testing || !form.testable) return
        _state.update { it.copy(testing = true, tested = null, testError = null) }
        viewModelScope.launch {
            val answered = attempt { ai.test(form.asked()) }
            // An answer about what the form held before it was changed says nothing of what it holds;
            // the choice of OpenRouter's endpoints is no part of what a test asks.
            _state.update {
                val refused = (answered.exceptionOrNull() as? ApiException)?.fieldRefusals.orEmpty()
                if (it.form?.copy(dataPolicy = form.dataPolicy) != form) {
                    it.copy(testing = false)
                } else {
                    it.copy(testing = false, tested = answered.getOrNull(), testError = answered.exceptionOrNull().takeIf { refused.isEmpty() }, refused = refused)
                }
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
                .onFailure { e ->
                    // The fields say why where the API named them.
                    val refused = (e as? ApiException)?.fieldRefusals.orEmpty()
                    _state.update { it.copy(saving = false, saveError = e.takeIf { refused.isEmpty() }, refused = refused) }
                }
        }
    }

    /**
     * Removes the provider [id]; the list is read again either way, so that one gone already goes
     * from it too, and so is the mode, which the API turns to the instance's provider once the
     * account's own mode is left without one.
     */
    fun remove(id: String) {
        if (_state.value.removing) return
        _state.update { it.copy(removing = true, removeError = null) }
        viewModelScope.launch {
            val removed = attempt { ai.remove(id) }
            _state.update { it.copy(removing = false, removeError = removed.exceptionOrNull()) }
            readProviders()
            readStatus()
        }
    }

    /** Whether the account keeps a key for [provider] that can still be read: a form of it may leave its key empty. */
    private fun keyKept(providers: List<AiProviderConfig>?, provider: String): Boolean =
        provider != "ollama" && providers.orEmpty().any { it.provider == provider && it.hasKey && !it.keyUnreadable }

    private suspend fun readStatus() {
        attempt { ai.status() }
            .onSuccess { status -> _state.update { it.copy(status = status, statusFailed = null) } }
            .onFailure { e -> _state.update { it.copy(statusFailed = e) } }
    }

    private suspend fun readProviders() {
        attempt { ai.providers() }
            .onSuccess { providers -> _state.update { it.copy(providers = providers, readFailed = null) } }
            .onFailure { e -> _state.update { it.copy(readFailed = e) } }
    }
}

/**
 * A provider of the account's own as its form holds it: which, the model to ask, its key or its
 * Ollama server's address, its place in the order providers are asked in, as typed, and which of
 * OpenRouter's endpoints may serve its requests. [fixed] where it changes a saved provider, which it
 * keeps; [keyKept] where the account keeps a key for the provider that can still be read, which an
 * empty field leaves as it is.
 */
data class ProviderForm(
    val provider: String = PROVIDERS.first(),
    val model: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val priority: String = "0",
    val dataPolicy: String = AiDataPolicies.NO_TRAINING,
    val fixed: Boolean = false,
    val keyKept: Boolean = false,
) {
    /** Ollama is reached at an address and takes no key; every other provider takes a key. */
    val ollama: Boolean
        get() = provider == "ollama"

    /** OpenRouter alone has the choice of its endpoints. */
    val openRouter: Boolean
        get() = provider == "openrouter"

    /** The place in the order as typed, a whole number; null for anything else. What the API takes of it is its to say. */
    val place: Int?
        get() = priority.trim().toIntOrNull()

    /** Whether there is something to test: a key typed or kept, or an Ollama server's address. */
    val testable: Boolean
        get() = if (ollama) baseUrl.isNotBlank() else apiKey.isNotBlank() || keyKept

    /** The provider as it is saved; null while the model, the key or the address is missing, or the place is no whole number. */
    fun setup(): ProviderSetup? {
        val chosen = model.trim()
        val place = place ?: return null
        if (chosen.isEmpty() || !testable) return null
        return asked().copy(model = chosen, priority = place)
    }

    /**
     * What a test sends: the key typed, none where it is kept, or the address, and the model where one
     * is typed, which a provider that lists none needs.
     */
    fun asked() = ProviderSetup(
        provider = provider,
        model = model.trim(),
        apiKey = apiKey.trim().takeUnless { ollama || it.isEmpty() },
        baseUrl = baseUrl.trim().takeIf { ollama },
        priority = place ?: 0,
        dataPolicy = dataPolicy.takeIf { openRouter },
    )

    companion object {
        /** The providers an account can bring of its own, in the web's order. */
        val PROVIDERS = listOf("openai", "anthropic", "openrouter", "ollama")
    }
}
