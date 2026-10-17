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
import net.whiteravens.dietapp.data.repository.SecondFactorRepository
import net.whiteravens.dietapp.domain.SecondFactorSetup
import net.whiteravens.dietapp.domain.SecondFactorState
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * Two-step sign-in of the account, as the web's settings set it up: whether it is on, setting it up
 * with an authenticator app, the recovery codes, new ones, and switching it off again. The password
 * and the codes typed are kept here, not in the screen's saved state, which the system may write to
 * disk; they go once a step is done or left.
 */
@HiltViewModel
class SecondFactorViewModel @Inject constructor(private val secondFactor: SecondFactorRepository) : ViewModel() {

    /** Where the card stands: at rest, or part of the way through one of the things it does. */
    sealed interface Step {
        data object Rest : Step

        /** Asked for the password before a secret is handed out. */
        data object Password : Step

        /** The secret to take into an authenticator app, and its first code asked for. */
        data class Scan(val setup: SecondFactorSetup) : Step

        /** The recovery codes, shown this once; [fresh] for those of a second step just switched on. */
        data class Codes(val recoveryCodes: List<String>, val fresh: Boolean) : Step

        /** Asked for the password and a code, to switch off or for new recovery codes. */
        data class Proof(val purpose: Purpose) : Step
    }

    enum class Purpose { SWITCH_OFF, NEW_CODES }

    data class UiState(
        /** Whether two-step sign-in is on; null until it was read. */
        val secondFactor: SecondFactorState? = null,
        val readFailed: Throwable? = null,
        val step: Step = Step.Rest,
        val password: String = "",
        val code: String = "",
        /** A request of the card is on its way. */
        val busy: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reads again whether two-step sign-in is on. */
    fun load() {
        viewModelScope.launch { read() }
    }

    fun typePassword(typed: String) = _state.update { it.copy(password = typed) }

    fun typeCode(typed: String) = _state.update { it.copy(code = typed) }

    /** Begins setting two-step sign-in up: the password first. */
    fun setUp() = goTo(Step.Password)

    /** Asks for the password and a code, to switch two-step sign-in off or for new recovery codes. */
    fun prove(purpose: Purpose) = goTo(Step.Proof(purpose))

    /** Leaves what the card was doing, and forgets what was typed for it. */
    fun cancel() = goTo(Step.Rest)

    /** The recovery codes were saved: the card goes back to rest. */
    fun saved() = goTo(Step.Rest)

    /** Hands out a secret for the password typed. */
    fun continueWithPassword() {
        val state = _state.value
        if (state.step != Step.Password || state.password.isEmpty()) return
        ask { goTo(Step.Scan(secondFactor.propose(state.password))) }
    }

    /**
     * Switches two-step sign-in on with the app's first code. A wrong code, or one held up, is said
     * and can be typed again; a set-up that cannot be finished, one that ran out, begins again.
     */
    fun switchOn() {
        val state = _state.value
        val scan = state.step as? Step.Scan ?: return
        if (state.code.isBlank()) return
        ask(orElse = { e -> if (!(e is ApiException && (e.code == "INVALID_SECOND_FACTOR_CODE" || e.statusCode == 429))) goTo(Step.Rest) }) {
            val codes = secondFactor.begin(scan.setup.enrolment, state.code)
            goTo(Step.Codes(codes, fresh = true))
            read()
        }
    }

    /** Switches two-step sign-in off, or makes new recovery codes, for the password and the code typed. */
    fun submitProof() {
        val state = _state.value
        val proof = state.step as? Step.Proof ?: return
        if (state.password.isEmpty() || state.code.isBlank()) return
        ask {
            when (proof.purpose) {
                Purpose.SWITCH_OFF -> {
                    secondFactor.end(state.password, state.code)
                    goTo(Step.Rest)
                }
                Purpose.NEW_CODES -> goTo(Step.Codes(secondFactor.newRecoveryCodes(state.password, state.code), fresh = false))
            }
            read()
        }
    }

    private fun goTo(step: Step) = _state.update { it.copy(step = step, password = "", code = "", error = null) }

    /** One request of the card at a time; what went wrong is said, and [orElse] says where the card then stands. */
    private fun ask(orElse: (Throwable) -> Unit = {}, work: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            attempt { work() }.onFailure { e ->
                orElse(e)
                _state.update { it.copy(error = e) }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun read() {
        attempt { secondFactor.state() }
            .onSuccess { read -> _state.update { it.copy(secondFactor = read, readFailed = null) } }
            .onFailure { e -> _state.update { it.copy(readFailed = e) } }
    }
}
