package net.whiteravens.dietapp.ui.screens.profile

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
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.SessionUser
import javax.inject.Inject

/** Session account details, the server setting and sign-out. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UserRepository,
    private val auth: AuthRepository,
    private val server: ServerConfigStore,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val user: SessionUser? = null,
        val signingOut: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Effective API base URL; empty only until the first DataStore emission. */
    val serverUrl: StateFlow<String> = server.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        load()
    }

    /**
     * Points the app at a different server. Tokens and the offline cache
     * belong to the old instance, so this signs out first (revoking there),
     * then saves — the root auth switch drops back to the login screen.
     */
    fun changeServer(url: String) {
        if (url == serverUrl.value) return
        viewModelScope.launch {
            _state.update { it.copy(signingOut = true) }
            auth.signOut()
            server.save(url)
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            runCatching { users.me() }
                .onSuccess { _state.value = UiState(loading = false, user = it) }
                .onFailure { _state.value = UiState(loading = false, error = it) }
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
