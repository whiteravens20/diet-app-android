// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.SignInResult
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * Login / registration. On success [AuthRepository] persists the token pair,
 * which flips the root `isSignedIn` flow — no navigation call needed here.
 * Where the server asks for the anti-abuse check, a sign-in or a sign-up waits
 * for its answer ([UiState.awaitingCheck]); an account with two-step sign-in
 * is asked for a code ([UiState.secondStep]). Also exposes the server base URL:
 * it must be editable before sign-in, and this screen is all there is then.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val server: ServerConfigStore,
    private val instance: ServerRepository,
) : ViewModel() {

    /** A sign-in or a sign-up, as it was asked for, waiting for the check's answer. */
    sealed interface Submission {
        data class SignIn(val email: String, val password: String) : Submission
        data class Register(val email: String, val password: String, val displayName: String) : Submission
    }

    data class UiState(
        val busy: Boolean = false,
        val error: Throwable? = null,
        /** The challenge of a sign-in's second step, while a code is asked for. */
        val secondStep: String? = null,
        /** A submission waiting for the anti-abuse check, while the check is shown. */
        val awaitingCheck: Submission? = null,
        /** The check could not be solved. */
        val checkFailed: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // The password and the second step's code as typed. They are held here and not in the screen's
    // saved state, which the system may write to disk, and are forgotten once they have been sent.
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _secondStepCode = MutableStateFlow("")
    val secondStepCode: StateFlow<String> = _secondStepCode.asStateFlow()

    fun typePassword(value: String) {
        _password.value = value
    }

    fun typeSecondStepCode(value: String) {
        _secondStepCode.value = value
    }

    /** Effective API base URL: null until it is read, empty while no server is chosen. */
    val serverUrl: StateFlow<String?> = server.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The site key of the anti-abuse check the server asks for, or null. */
    val checkSiteKey: StateFlow<String?> = instance.checkSiteKey

    fun saveServerUrl(url: String) {
        viewModelScope.launch { server.save(url) }
    }

    fun signIn(email: String, password: String) = submit(Submission.SignIn(email.trim(), password))

    fun register(email: String, password: String, displayName: String) =
        submit(Submission.Register(email.trim(), password, displayName.trim()))

    /** The check's answer: the submission that waited for it goes on with it. */
    fun checkSolved(token: String) {
        val waiting = _state.value.awaitingCheck ?: return
        _state.update { it.copy(awaitingCheck = null) }
        send(waiting, token)
    }

    fun checkFailed() {
        _state.update { it.copy(awaitingCheck = null, checkFailed = true) }
    }

    fun checkDismissed() {
        _state.update { it.copy(awaitingCheck = null) }
    }

    /** Answers the second step with a code from the authenticator app or a recovery code. */
    fun answerSecondStep(code: String) {
        val challenge = _state.value.secondStep ?: return
        busyWhile {
            auth.answerSecondStep(challenge, code)
            _secondStepCode.value = ""
        }
    }

    /** Back to the password, from a second step not taken. */
    fun leaveSecondStep() {
        _secondStepCode.value = ""
        _state.update { it.copy(secondStep = null, error = null) }
    }

    private fun submit(submission: Submission) {
        if (_state.value.busy) return
        if (checkSiteKey.value != null) {
            _state.update { it.copy(awaitingCheck = submission, error = null, checkFailed = false) }
        } else {
            send(submission, checkToken = null)
        }
    }

    private fun send(submission: Submission, checkToken: String?) = busyWhile {
        when (submission) {
            is Submission.SignIn -> when (val result = auth.signIn(submission.email, submission.password, checkToken)) {
                SignInResult.SignedIn -> Unit
                is SignInResult.SecondStep -> _state.update { it.copy(secondStep = result.challenge) }
            }
            is Submission.Register -> auth.register(submission.email, submission.password, submission.displayName, checkToken)
        }
        // The password was taken: a second step, if any, asks for a code instead.
        _password.value = ""
    }

    /** Runs [block] with the screen busy, and puts what it throws on the screen. */
    private fun busyWhile(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, checkFailed = false) }
            attempt { block() }
                .onSuccess { _state.update { it.copy(busy = false) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e) } }
            // A finished sign-in needs no state change — the auth flow unmounts this screen.
        }
    }
}
