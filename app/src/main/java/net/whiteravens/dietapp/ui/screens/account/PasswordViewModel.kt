// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
 * The account's password changed, as the web's settings change it: the one the account has, and the
 * new one. The API then ends every session of the account, this phone's too: the phone says that it
 * worked, and a moment later leaves the session for the sign-in. What is typed is kept here, not in
 * the screen's saved state, which the system may write to disk.
 */
@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val users: UserRepository,
    private val auth: AuthRepository,
    @ApplicationScope private val application: CoroutineScope,
) : ViewModel() {

    data class UiState(
        val current: String = "",
        val new: String = "",
        val changing: Boolean = false,
        /** The password was changed; the phone is about to leave the session. */
        val changed: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun typeCurrent(typed: String) = _state.update { it.copy(current = typed) }

    fun typeNew(typed: String) = _state.update { it.copy(new = typed) }

    /** Changes the password to the new one typed; a refusal is said and what was typed stays. */
    fun change() {
        val form = _state.value
        if (form.changing || form.changed || form.current.isEmpty() || form.new.isEmpty()) return
        _state.update { it.copy(changing = true, error = null) }
        viewModelScope.launch {
            attempt { users.changePassword(form.current, form.new) }
                .onSuccess {
                    _state.update { it.copy(current = "", new = "", changing = false, changed = true) }
                    // The session has ended on the server: what is left of it on the phone goes once the
                    // change was read, in the application's scope, which the sign-in screen outlives.
                    application.launch {
                        delay(LEAVING_AFTER_MILLIS)
                        auth.leave()
                    }
                }
                .onFailure { e -> _state.update { it.copy(changing = false, error = e) } }
        }
    }

    private companion object {
        /** How long the change is shown before the sign-in takes the screen's place. */
        const val LEAVING_AFTER_MILLIS = 1_500L
    }
}
