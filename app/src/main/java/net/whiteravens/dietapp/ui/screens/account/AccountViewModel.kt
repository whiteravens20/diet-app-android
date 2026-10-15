// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.di.ApplicationScope
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/** Session account details, the confirmation mail sent again, the server setting and sign-out. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val users: UserRepository,
    private val auth: AuthRepository,
    private val server: ServerConfigStore,
    instance: ServerRepository,
    @ApplicationScope private val application: CoroutineScope,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val user: SessionUser? = null,
        val signingOut: Boolean = false,
        val error: Throwable? = null,
        val resending: Boolean = false,
        /** The mail that confirms the address was sent again. */
        val resent: Boolean = false,
        val resendError: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Whether the server sends mail; null until it has said. A confirmation mail needs it. */
    val mailsSent: StateFlow<Boolean?> = instance.rules
        .map { it?.mailsSent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), instance.rules.value?.mailsSent)

    /** Effective API base URL; empty only until the first DataStore emission. */
    val serverUrl: StateFlow<String> = server.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        load()
    }

    /**
     * Points the app at a different server. Tokens and the offline cache
     * belong to the old instance, so this signs out first (revoking there),
     * then saves — the root auth switch drops back to the login screen. The
     * sign-out takes this screen away, so the change runs in the application's
     * scope, where it is finished.
     */
    fun changeServer(url: String) {
        if (url == serverUrl.value) return
        _state.update { it.copy(signingOut = true) }
        application.launch {
            auth.signOut()
            server.save(url)
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            attempt { users.me() }
                .onSuccess { _state.value = UiState(loading = false, user = it) }
                .onFailure { _state.value = UiState(loading = false, error = it) }
        }
    }

    /** Sends the mail that confirms the account's address again. */
    fun resendVerification() {
        if (_state.value.resending) return
        viewModelScope.launch {
            _state.update { it.copy(resending = true, resendError = null) }
            attempt { users.resendVerification() }
                .onSuccess { _state.update { it.copy(resending = false, resent = true) } }
                .onFailure { e -> _state.update { it.copy(resending = false, resendError = e) } }
        }
    }

    /** Clears tokens + cache; the root auth switch does the "navigation". */
    fun signOut() {
        if (_state.value.signingOut) return
        viewModelScope.launch {
            _state.update { it.copy(signingOut = true) }
            auth.signOut()
        }
    }
}
