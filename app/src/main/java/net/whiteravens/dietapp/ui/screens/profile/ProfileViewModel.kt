package net.whiteravens.dietapp.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.SessionUser
import javax.inject.Inject

/** Session account details + sign-out. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UserRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val user: SessionUser? = null,
        val signingOut: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
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
