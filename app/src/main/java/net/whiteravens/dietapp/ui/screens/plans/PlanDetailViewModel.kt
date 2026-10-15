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
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.Rebalanced
import net.whiteravens.dietapp.domain.SwapOffer
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.navigation.PlanDetailRoute
import java.io.IOException
import javax.inject.Inject

/**
 * One plan: cache-first load, day selection, and a meal swapped the way the web application
 * swaps one: a recipe is offered, another can be asked for, and nothing is written until the
 * offer is taken. A meal is marked eaten or not, and a
 * change that balanced the day again says how, with the way back and the rest of the week to
 * balance as well.
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
        /** An eaten mark, a meal of one's own, a rebalance or the plan's deletion is on its way. */
        val changing: Boolean = false,
        /** What the last change rebalanced, until it is put away. */
        val rebalance: Rebalance? = null,
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
                .onSuccess { taken -> _state.update { it.copy(plan = taken.plan, offer = null, taking = false, rebalance = taken.rebalance.shown()) } }
                .onFailure { e ->
                    // The offer was for the plan as it was: it does not outlive a refusal.
                    _state.update { it.copy(offer = null, taking = false, error = e) }
                    if (e.isPlanChanged()) reload()
                }
        }
    }

    fun setEaten(mealId: String, eaten: Boolean) = change { plans.setEaten(planId, mealId, eaten) }

    /** Puts the meals the last change scaled back as they were. */
    fun undoRebalance() {
        val rebalance = _state.value.rebalance ?: return
        change(said = false) { plans.undo(planId, rebalance) }
    }

    /** Balances the rest of the week, from today, each day around its own meals. */
    fun rebalanceWeek() = change { plans.rebalanceWeek(planId) }

    fun dismissRebalance() {
        _state.update { it.copy(rebalance = null) }
    }

    /**
     * A change of the plan, one at a time: the plan it leaves is shown, and what it rebalanced is
     * said where [said]. The way back says nothing of itself.
     */
    private fun change(said: Boolean = true, block: suspend () -> Rebalanced) {
        if (_state.value.changing) return
        viewModelScope.launch {
            _state.update { it.copy(changing = true, error = null) }
            attempt { block() }
                .onSuccess { changed ->
                    _state.update { it.copy(plan = changed.plan, changing = false, rebalance = if (said) changed.rebalance.shown() else null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(changing = false, error = e) }
                    if (e.isPlanChanged()) reload()
                }
        }
    }

    /** A rebalance is said where it scaled a meal. */
    private fun Rebalance?.shown(): Rebalance? = this?.takeIf { it.changes.isNotEmpty() }

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
