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

/**
 * Session account details, the confirmation mail sent again, the account's name, the language of its
 * mails and of the web, the unit weights are shown in, the server setting and sign-out.
 */
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
        /** A new name is on its way to the API. */
        val renaming: Boolean = false,
        /** The name typed last was kept. */
        val renamed: Boolean = false,
        val renameError: Throwable? = null,
        /** The language chosen and on its way to the API, shown at once; null while none is. */
        val choosingLanguage: String? = null,
        val languageError: Throwable? = null,
    ) {
        /** The unit weights are shown in as the account has it, or as it was just chosen: `kg`, `lb`, or null for where the phone is. */
        val weightUnit: String?
            get() = if (choosing != null) choosing.unit else user?.weightUnit

        /** The language of the account's mails and of the web as the account has it, or as it was just chosen. */
        val locale: String?
            get() = choosingLanguage ?: user?.locale
    }

    /** A unit chosen: `kg`, `lb`, or null to go by where the phone is. */
    data class UnitChoice(val unit: String?)

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

    /** Gives the account the name [typed], without the spaces around it; a name left empty, or as it is, is not sent. */
    fun rename(typed: String) {
        val state = _state.value
        val name = typed.trim()
        if (state.user == null || state.renaming || name.isEmpty() || name == state.user.displayName) return
        _state.update { it.copy(renaming = true, renamed = false, renameError = null) }
        viewModelScope.launch {
            attempt { users.rename(name) }
                .onSuccess { saved -> _state.update { it.copy(user = saved, renaming = false, renamed = true) } }
                .onFailure { e -> _state.update { it.copy(renaming = false, renameError = e) } }
        }
    }

    /** Chooses the language of the account's mails and of the web application; the app goes on in the phone's. */
    fun chooseLanguage(locale: String) {
        val state = _state.value
        if (state.user == null || state.choosingLanguage != null || locale == state.locale) return
        _state.update { it.copy(choosingLanguage = locale, languageError = null) }
        viewModelScope.launch {
            attempt { users.chooseLanguage(locale) }
                .onSuccess { saved -> _state.update { it.copy(user = saved, choosingLanguage = null) } }
                .onFailure { e -> _state.update { it.copy(choosingLanguage = null, languageError = e) } }
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
