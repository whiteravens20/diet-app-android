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
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.DayWays
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * Other ways to fill a day of a plan, as the web's window offers them: the API makes them and
 * counts all that is said of them, a model weighs them where the user asks, and the one taken
 * becomes the day. Nothing changes until one is taken.
 */
@HiltViewModel
class DayWaysViewModel @Inject constructor(
    private val plans: MealPlanRepository,
    private val users: UserRepository,
) : ViewModel() {

    data class UiState(
        val planId: String = "",
        val dayId: String = "",
        val ways: DayWays? = null,
        val loading: Boolean = true,
        val weighing: Boolean = false,
        val taking: Boolean = false,
        /** Whether the account has a model to ask; null until that is known. */
        val aiOn: Boolean? = null,
        val error: Throwable? = null,
        /** The plan with the way taken: the window is done. */
        val taken: MealPlan? = null,
        /** A way was refused: the plan may have changed under the offer, and is to be read anew. */
        val stale: Boolean = false,
    ) {
        val busy: Boolean get() = weighing || taking
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Ways for the day drawn anew: an offer is good for the plan as it stands, and for a sitting. */
    fun open(planId: String, dayId: String) {
        _state.value = UiState(planId = planId, dayId = dayId)
        viewModelScope.launch {
            val aiOn = attempt { users.me() }.getOrNull()?.let { it.aiMode != "none" }
            _state.update { if (it.dayId == dayId) it.copy(aiOn = aiOn) else it }
        }
        viewModelScope.launch {
            attempt { plans.dayWays(planId, dayId) }
                .onSuccess { ways -> _state.update { if (it.dayId == dayId) it.copy(ways = ways, loading = false) else it } }
                .onFailure { e -> _state.update { if (it.dayId == dayId) it.copy(loading = false, error = e) else it } }
        }
    }

    /** Asks a model to put the ways in order and say a sentence of each: they stand in the place of the ways as they came first. */
    fun weigh() {
        val current = _state.value
        if (current.busy || current.aiOn != true) return
        viewModelScope.launch {
            _state.update { it.copy(weighing = true, error = null) }
            attempt { plans.dayWays(current.planId, current.dayId, weigh = true) }
                .onSuccess { ways -> _state.update { it.copy(ways = ways, weighing = false) } }
                .onFailure { e -> _state.update { it.copy(weighing = false, error = e) } }
        }
    }

    fun take(offer: String) {
        val current = _state.value
        if (current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(taking = true, error = null) }
            attempt { plans.takeDayWay(current.planId, current.dayId, offer) }
                .onSuccess { plan -> _state.update { it.copy(taking = false, taken = plan) } }
                // The offer was for the plan as it was: where it changed, the plan is read anew.
                .onFailure { e -> _state.update { it.copy(taking = false, error = e, stale = true) } }
        }
    }
}
