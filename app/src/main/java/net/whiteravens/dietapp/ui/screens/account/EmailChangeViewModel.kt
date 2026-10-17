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
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * Another address for the account, as the web's settings ask for one: the new address and the
 * password, after which a mail with a link goes to the new address, and the change is made once the
 * link is followed, in the web application. What is typed is kept here, not in the screen's saved
 * state, which the system may write to disk.
 */
@HiltViewModel
class EmailChangeViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {

    data class UiState(
        /** The form is open. */
        val editing: Boolean = false,
        val newEmail: String = "",
        val password: String = "",
        val sending: Boolean = false,
        /** The mail with the link went to the new address. */
        val sent: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun open() = _state.update { it.copy(editing = true, error = null) }

    /** Closes the form, and forgets what was typed in it. */
    fun cancel() = _state.update { UiState(sent = it.sent) }

    fun typeEmail(typed: String) = _state.update { it.copy(newEmail = typed) }

    fun typePassword(typed: String) = _state.update { it.copy(password = typed) }

    /** Asks for the new address typed; a refusal is said and what was typed stays. */
    fun send() {
        val form = _state.value
        val address = form.newEmail.trim()
        if (form.sending || address.isEmpty() || form.password.isEmpty()) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            attempt { users.changeEmail(address, form.password) }
                .onSuccess { _state.update { UiState(sent = true) } }
                .onFailure { e -> _state.update { it.copy(sending = false, error = e) } }
        }
    }
}
