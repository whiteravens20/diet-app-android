package net.whiteravens.dietapp.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.repository.AuthRepository
import javax.inject.Inject

/**
 * Login / registration. On success [AuthRepository] persists the token pair,
 * which flips the root `isSignedIn` flow — no navigation call needed here.
 * Also exposes the server base URL: it must be editable before sign-in, and
 * this screen is all there is at that point.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val server: ServerConfigStore,
) : ViewModel() {

    data class UiState(val busy: Boolean = false, val error: Throwable? = null)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Effective API base URL; empty only until the first DataStore emission. */
    val serverUrl: StateFlow<String> = server.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun saveServerUrl(url: String) {
        viewModelScope.launch { server.save(url) }
    }

    fun signIn(email: String, password: String) = submit { auth.signIn(email.trim(), password) }

    fun register(email: String, password: String, displayName: String) =
        submit { auth.register(email.trim(), password, displayName.trim()) }

    private fun submit(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = UiState(busy = true)
            runCatching { block() }
                .onFailure { _state.value = UiState(error = it) }
            // Success needs no state change — the auth flow unmounts this screen.
        }
    }
}
