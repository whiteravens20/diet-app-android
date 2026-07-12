package net.whiteravens.dietapp.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.AuthRepository
import javax.inject.Inject

/**
 * Login / registration. On success [AuthRepository] persists the token pair,
 * which flips the root `isSignedIn` flow — no navigation call needed here.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    data class UiState(val busy: Boolean = false, val error: Throwable? = null)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

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
