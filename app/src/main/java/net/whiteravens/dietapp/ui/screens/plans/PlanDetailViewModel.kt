package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.ui.navigation.PlanDetailRoute
import java.io.IOException
import javax.inject.Inject

/** One plan: cache-first load, day selection, per-meal swap. */
@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: MealPlanRepository,
) : ViewModel() {

    private val planId: String = savedStateHandle.toRoute<PlanDetailRoute>().planId

    data class UiState(
        val loading: Boolean = true,
        val plan: MealPlan? = null,
        val selectedDay: Int = 0,
        val offline: Boolean = false,
        /** Ids of meals with a swap in flight. */
        val swapping: Set<String> = emptySet(),
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
            val cached = plans.cachedPlan(planId)
            if (cached != null) _state.value = UiState(loading = false, plan = cached)
            runCatching { plans.refreshPlan(planId) }
                .onSuccess { fresh -> _state.update { it.copy(loading = false, plan = fresh) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            offline = e is IOException && it.plan != null,
                            error = e.takeIf { _ -> it.plan == null },
                        )
                    }
                }
        }
    }

    fun selectDay(index: Int) {
        _state.update { it.copy(selectedDay = index) }
    }

    fun swapMeal(mealId: String) {
        viewModelScope.launch {
            _state.update { it.copy(swapping = it.swapping + mealId, error = null) }
            runCatching { plans.swapMeal(planId, mealId) }
                .onSuccess { fresh -> _state.update { it.copy(plan = fresh, swapping = it.swapping - mealId) } }
                .onFailure { e -> _state.update { it.copy(swapping = it.swapping - mealId, error = e) } }
        }
    }
}
