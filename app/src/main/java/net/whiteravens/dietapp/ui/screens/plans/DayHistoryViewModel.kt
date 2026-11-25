// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.domain.DayHistory
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * The history of a day of a plan, as the web's window shows it: read from the API each time the window
 * opens, and what can be done of the day from it (its last change taken back, the day sized again, its
 * portions kept as they are), each followed by the history read again. It needs the network.
 */
@HiltViewModel
class DayHistoryViewModel @Inject constructor(private val plans: MealPlanRepository) : ViewModel() {

    data class UiState(
        val history: DayHistory? = null,
        val loading: Boolean = true,
        /** A change asked from the window is on its way. */
        val acting: Boolean = false,
        /** The last change asked from the window was refused or did not reach the server. */
        val failed: Boolean = false,
        val readError: Throwable? = null,
        /** How many changes the window made: each tells the screen under it to read the plan again. */
        val changes: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var planId = ""
    private var dayId = ""

    /** The history of the day [dayId] of the plan [planId], read anew. */
    fun open(planId: String, dayId: String) {
        this.planId = planId
        this.dayId = dayId
        _state.value = UiState()
        read()
    }

    private fun read() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.history == null, readError = null) }
            attempt { plans.dayHistory(planId, dayId) }
                .onSuccess { history -> _state.update { it.copy(history = history, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, readError = e) } }
        }
    }

    /** The day's last change taken back: its swap by the swap's own undo, else its portions put back. */
    fun undo() {
        val history = _state.value.history ?: return
        val undo = history.undo ?: return
        act { if (undo.swap) plans.undoSwap(history.planId, history.revision) else plans.restorePortions(history.planId, undo.restore) }
    }

    /** The day sized again ("Dopasuj porcje dnia"), which ends the user's choice of its portions. */
    fun resize() {
        val history = _state.value.history ?: return
        act { plans.resizeDay(history.planId, history.date) }
    }

    /** "Zostaw porcje jak są": the day's portions become the user's choice, from the plan as the history read it. */
    fun keep() {
        val history = _state.value.history ?: return
        act { plans.keepDay(history.planId, history.dayId, history.revision) }
    }

    private fun act(block: suspend () -> Any) {
        if (_state.value.acting) return
        viewModelScope.launch {
            _state.update { it.copy(acting = true, failed = false) }
            attempt { block() }
                .onSuccess { _state.update { it.copy(acting = false, changes = it.changes + 1) } }
                .onFailure { _state.update { it.copy(acting = false, failed = true) } }
            read()
        }
    }
}
