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
import net.whiteravens.dietapp.ui.common.ReadingAnew
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/** Session account details, the confirmation mail sent again, the unit weights are shown in, the server setting and sign-out. */
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
        /** The unit chosen and on its way to the API, shown at once; null while none is. */
        val choosing: UnitChoice? = null,
        val unitError: Throwable? = null,
        /** A read asked for by pulling the screen down is under way. */
        val pulled: Boolean = false,
    ) {
        /** The unit weights are shown in as the account has it, or as it was just chosen: `kg`, `lb`, or null for where the phone is. */
        val weightUnit: String?
            get() = if (choosing != null) choosing.unit else user?.weightUnit
    }

    /** A unit chosen: `kg`, `lb`, or null to go by where the phone is. */
    data class UnitChoice(val unit: String?)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Whether the server sends mail; null until it has said. A confirmation mail needs it. */
    val mailsSent: StateFlow<Boolean?> = instance.rules
        .map { it?.mailsSent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), instance.rules.value?.mailsSent)

    /** How to reach whoever runs the instance, where they published it: whom to ask for the use of its AI. */
    val operatorContact: StateFlow<String?> = instance.rules
        .map { it?.operatorContact }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), instance.rules.value?.operatorContact)

    /** Effective API base URL; empty only until the first DataStore emission. */
    val serverUrl: StateFlow<String> = server.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val again = ReadingAnew()

    init {
        load()
    }

    /**
     * Reads the account again when the screen is shown again, on coming back to the front only as often
     * as [ReadingAnew] lets it; says whether it did, so that the screen reads its other cards with it.
     */
    fun shownAgain(cameToTheFront: Boolean): Boolean {
        if (_state.value.loading || !again.due(cameToTheFront)) return false
        readAnew()
        return true
    }

    /** Reads the account again at once, the screen pulled down. */
    fun pull() {
        if (_state.value.loading || _state.value.pulled) return
        _state.update { it.copy(pulled = true) }
        readAnew()
    }

    /** Reads the account again, keeping what is shown until the answer is there; one that could not be read is asked for whole. */
    private fun readAnew() {
        if (_state.value.user == null) {
            _state.update { it.copy(pulled = false) }
            return load()
        }
        again.begun()
        viewModelScope.launch {
            attempt { users.me() }
                .onSuccess { me -> _state.update { it.copy(user = me, pulled = false) } }
                .onFailure { _state.update { it.copy(pulled = false) } }
        }
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
        again.begun()
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

    /** Chooses the unit the account's weights are shown and typed in; null gives the choice back to where the phone is. */
    fun chooseWeightUnit(unit: String?) {
        val state = _state.value
        if (state.user == null || state.choosing != null || unit == state.weightUnit) return
        _state.update { it.copy(choosing = UnitChoice(unit), unitError = null) }
        viewModelScope.launch {
            attempt { users.chooseWeightUnit(unit) }
                .onSuccess { saved -> _state.update { it.copy(user = saved, choosing = null) } }
                .onFailure { e -> _state.update { it.copy(choosing = null, unitError = e) } }
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
