// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.di.ApplicationScope
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * The account deleted, with everything it holds, as the web's settings delete it: once its address
 * is typed again and its password given. The account's sessions go with it, the phone's too, which
 * then leaves it for the sign-in. What is typed is kept here, not in the screen's saved state.
 */
@HiltViewModel
class AccountDeletionViewModel @Inject constructor(
    private val users: UserRepository,
    private val auth: AuthRepository,
    @ApplicationScope private val application: CoroutineScope,
) : ViewModel() {

    data class UiState(
        /** The confirmation is open. */
        val confirming: Boolean = false,
        val email: String = "",
        val password: String = "",
        val deleting: Boolean = false,
        val error: Throwable? = null,
    ) {
        /** Whether what was typed is the account's address, [address], whatever its case, and a password was given. */
        fun confirms(address: String): Boolean = email.trim().equals(address, ignoreCase = true) && password.isNotEmpty()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun open() = _state.update { it.copy(confirming = true, error = null) }

    /** Closes the confirmation, and forgets what was typed in it. */
    fun cancel() = _state.update { UiState() }

    fun typeEmail(typed: String) = _state.update { it.copy(email = typed) }

    fun typePassword(typed: String) = _state.update { it.copy(password = typed) }

    /** Deletes the account whose address is [address], once that address was typed again; a refusal is said. */
    fun delete(address: String) {
        val form = _state.value
        if (form.deleting || !form.confirms(address)) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            attempt { users.deleteAccount(form.password) }
                // The session is gone with the account: what the phone keeps of it goes in the
                // application's scope, which the sign-in screen that takes this one's place outlives.
                .onSuccess { application.launch { auth.leave() } }
                .onFailure { e -> _state.update { it.copy(deleting = false, error = e) } }
        }
    }
}
