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
import java.io.OutputStream
import java.time.LocalDate
import javax.inject.Inject

/**
 * Everything the account holds, as one file kept where the user chooses, as the web's settings hand
 * it on: its profiles and what was logged, kept, marked, planned and listed for them. A file that
 * did not arrive whole, or could not be written, is not left behind as if it were the account's.
 */
@HiltViewModel
class DataExportViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {

    data class UiState(
        val preparing: Boolean = false,
        /** The file was written where it was asked to be. */
        val saved: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Writes the account's file into what [open] opens, the place the user chose; [discard] takes the
     * file away again when the account's data did not all arrive, or could not all be written.
     */
    fun export(open: () -> OutputStream, discard: () -> Unit) {
        if (_state.value.preparing) return
        _state.update { UiState(preparing = true) }
        // Opening the place and taking it away are a moment's asking of its provider; the file itself is
        // read and written off the main thread, by the repository.
        viewModelScope.launch {
            attempt { open().use { users.export(it) } }
                .onSuccess { _state.update { UiState(saved = true) } }
                .onFailure { e ->
                    runCatching(discard)
                    _state.update { UiState(error = e) }
                }
        }
    }

    companion object {
        /** The name the file is offered under, as the web names it: the day it is on the phone. */
        fun fileName(today: LocalDate = LocalDate.now()): String = "diet-app-account-$today.json"
    }
}
