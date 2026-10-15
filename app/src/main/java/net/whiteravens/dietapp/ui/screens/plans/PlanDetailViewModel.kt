// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.SwapOffer
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.navigation.PlanDetailRoute
import java.io.IOException
import javax.inject.Inject

/**
 * One plan: cache-first load, day selection, and a meal swapped the way the web application
 * swaps one: a recipe is offered, another can be asked for, and nothing is written until the
 * offer is taken.
 */
@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: MealPlanRepository,
) : ViewModel() {

    // The route's arguments, read by their names: `toRoute` needs Android's own classes, which the
    // tests on the JVM do not have.
    private val planId: String = checkNotNull(savedStateHandle.get<String>(PlanDetailRoute::planId.name))

    /** The day the plan opens at, where the route names one (yyyy-MM-dd): the calendar's day that was chosen. */
    private val openAt: String? = savedStateHandle.get<String>(PlanDetailRoute::day.name)

    data class UiState(
        val loading: Boolean = true,
        val plan: MealPlan? = null,
        val selectedDay: Int = 0,
        val offline: Boolean = false,
        /** The meal an offer is being asked for, while it is asked. */
        val offering: String? = null,
        /** A recipe offered in the place of a meal, until it is taken or put away. */
        val offer: SwapOffer? = null,
        val taking: Boolean = false,
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
            if (cached != null) _state.value = UiState(loading = false, plan = cached, selectedDay = dayOf(cached))
            attempt { plans.refreshPlan(planId) }
                .onSuccess { fresh ->
                    _state.update { it.copy(loading = false, plan = fresh, selectedDay = if (it.plan == null) dayOf(fresh) else it.selectedDay) }
                }
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

    /** The index of the day the route names in [plan], or of its first day. */
    private fun dayOf(plan: MealPlan): Int = plan.days.indexOfFirst { it.date == openAt }.coerceAtLeast(0)

    fun selectDay(index: Int) {
        _state.update { it.copy(selectedDay = index) }
    }

    /** Asks for a recipe to put in the place of [mealId]. */
    fun offerSwap(mealId: String) = ask(mealId, seen = emptyList())

    /** Another recipe for the meal of the offer on screen, than those offered for it so far. */
    fun anotherOffer() {
        val offer = _state.value.offer ?: return
        ask(offer.mealId, offer.seen)
    }

    fun dismissOffer() {
        _state.update { it.copy(offer = null) }
    }

    fun takeOffer() {
        val offer = _state.value.offer ?: return
        if (_state.value.taking) return
        viewModelScope.launch {
            _state.update { it.copy(taking = true, error = null) }
            attempt { plans.takeSwap(offer) }
                .onSuccess { fresh -> _state.update { it.copy(plan = fresh, offer = null, taking = false) } }
                .onFailure { e ->
                    // The offer was for the plan as it was: it does not outlive a refusal.
                    _state.update { it.copy(offer = null, taking = false, error = e) }
                    if (e.isPlanChanged()) reload()
                }
        }
    }

    private fun ask(mealId: String, seen: List<String>) {
        if (_state.value.offering != null) return
        viewModelScope.launch {
            _state.update { it.copy(offering = mealId, error = null) }
            attempt { plans.offerSwap(planId, mealId, seen) }
                .onSuccess { offer -> _state.update { it.copy(offering = null, offer = offer) } }
                .onFailure { e ->
                    _state.update { it.copy(offering = null, offer = null, error = e) }
                    if (e.isPlanChanged()) reload()
                }
        }
    }

    /** Another request changed the plan first: the plan on screen is the server's again. */
    private fun reload() {
        viewModelScope.launch {
            attempt { plans.refreshPlan(planId) }
                .onSuccess { fresh -> _state.update { it.copy(plan = fresh) } }
        }
    }

    private fun Throwable.isPlanChanged() = this is ApiException && code == "PLAN_CHANGED"
}
