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
import net.whiteravens.dietapp.data.repository.SessionRepository
import net.whiteravens.dietapp.domain.AccountSession
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * Where the account is signed in, as the web's settings list it: every open session, this phone's
 * named, any of them signed out, or all at once. Signing out this phone's session, alone or with
 * the others, ends it here, and the screen gives way to the sign-in.
 */
@HiltViewModel
class SessionsViewModel @Inject constructor(private val sessions: SessionRepository) : ViewModel() {

    data class UiState(
        /** The open sessions; null until they were read. */
        val sessions: List<AccountSession>? = null,
        val readFailed: Throwable? = null,
        /** A sign-out of one session or of all is on its way. */
        val ending: Boolean = false,
        val endFailed: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reads the open sessions again. */
    fun load() {
        viewModelScope.launch { read() }
    }

    /**
     * Signs out the session [id]. The list is read again after another's, and after a refusal, so
     * that a session gone already goes from it too; after the phone's own there is no list to read.
     */
    fun end(id: String) {
        val state = _state.value
        if (state.ending) return
        val mine = state.sessions.orEmpty().any { it.id == id && it.current }
        _state.update { it.copy(ending = true, endFailed = null) }
        viewModelScope.launch {
            val ended = attempt { sessions.end(id) }
            _state.update { it.copy(ending = false, endFailed = ended.exceptionOrNull()) }
            if (ended.isFailure || !mine) read()
        }
    }

    /** Signs out every session, this phone's too. */
    fun endAll() {
        if (_state.value.ending) return
        _state.update { it.copy(ending = true, endFailed = null) }
        viewModelScope.launch {
            attempt { sessions.endAll() }
                .onFailure { e -> _state.update { it.copy(ending = false, endFailed = e) } }
        }
    }

    private suspend fun read() {
        attempt { sessions.sessions() }
            .onSuccess { open -> _state.update { it.copy(sessions = open, readFailed = null) } }
            .onFailure { e -> _state.update { it.copy(readFailed = e) } }
    }
}
