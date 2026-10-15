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
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.CustomMeal
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.PLAN_RELAXATIONS
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.Rebalanced
import net.whiteravens.dietapp.domain.RelaxationNeeded
import net.whiteravens.dietapp.domain.Reroll
import net.whiteravens.dietapp.domain.SwapAsk
import net.whiteravens.dietapp.domain.SwapOffer
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.navigation.PlanDetailRoute
import java.io.IOException
import javax.inject.Inject

/**
 * One plan: cache-first load, day selection, and a meal swapped the way the web application
 * swaps one: a recipe is offered (any of the plan's diet, a favourite picked by hand, one with the
 * profile's favourite ingredients, or a model's pick), another can be asked for, and nothing is
 * written until the offer is taken. A meal is marked eaten or not, a meal of one's own is added to
 * a day, and a change that balanced the day again says how, with the way back and the rest of the
 * week to balance as well. The plan, a part of it or a day is made again; a refusal for something
 * the user can allow offers to allow it. The plan can be deleted.
 */
@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plans: MealPlanRepository,
    private val favorites: FavoriteRepository,
    private val users: UserRepository,
) : ViewModel() {

    // The route's arguments, read by their names: `toRoute` needs Android's own classes, which the
    // tests on the JVM do not have.
    private val planId: String = checkNotNull(savedStateHandle.get<String>(PlanDetailRoute::planId.name))

    /** The day the plan opens at, where the route names one (yyyy-MM-dd): the calendar's day that was chosen. */
    private val openAt: String? = savedStateHandle.get<String>(PlanDetailRoute::day.name)

    /** A re-roll refused for something the user can allow: a lock that may no longer hold to drop ([DROP_LOCKS]), or days that have passed to make again too ([INCLUDE_PAST]). */
    data class Refused(val reroll: Reroll, val allow: String)

    /** A re-roll that could only be made outside the plan's limits: what stands in the way, what was let give way, and what was asked. */
    data class InTheWay(val needed: RelaxationNeeded, val allowed: List<String>, val reroll: Reroll)

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
        /** Offers put first what the pantry covers. */
        val swapWithPantry: Boolean = true,
        /** The recipes the profile marked and did not mark to avoid: the favourites a meal can be swapped to. */
        val liked: List<MarkedRecipe> = emptyList(),
        /** Whether the account has a model to ask; null until that is known. */
        val aiOn: Boolean? = null,
        /** An eaten mark, a meal of one's own, a rebalance or the plan's deletion is on its way. */
        val changing: Boolean = false,
        /** What the last change rebalanced, until it is put away. */
        val rebalance: Rebalance? = null,
        /** Why the engine stood in for a model in the last change, until it is put away. */
        val notice: String? = null,
        /** The plan is gone: the screen is done. */
        val deleted: Boolean = false,
        val refused: Refused? = null,
        val inTheWay: InTheWay? = null,
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
                    readSwapChoices(fresh.profileId)
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

    /**
     * The favourites a meal can be swapped to, and whether a model can be asked: what fails to come
     * leaves the picker empty and the model's ask off, nothing more.
     */
    private fun readSwapChoices(profileId: String) {
        viewModelScope.launch {
            val liked = attempt { favorites.marked(profileId) }.getOrDefault(emptyList()).filter { it.sentiment != "avoid" }
            val aiOn = attempt { users.me() }.getOrNull()?.let { it.aiMode != "none" }
            _state.update { it.copy(liked = liked, aiOn = aiOn) }
        }
    }

    /** Asks for a recipe to put in the place of [mealId], as [how] says. */
    fun offerSwap(mealId: String, how: SwapAsk = SwapAsk("random")) = ask(mealId, how, seen = emptyList())

    /** Another recipe for the meal of the offer on screen, than those offered for it so far. */
    fun anotherOffer() {
        val offer = _state.value.offer ?: return
        ask(offer.mealId, offer.ask, offer.seen)
    }

    fun setSwapWithPantry(on: Boolean) {
        _state.update { it.copy(swapWithPantry = on) }
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

    /** Adds [meal] of the user's own to the day of [date] (yyyy-MM-dd). */
    fun addCustomMeal(date: String, meal: CustomMeal) = change { plans.addCustomMeal(planId, date, meal) }

    /** Puts the meals the last change scaled back as they were. */
    fun undoRebalance() {
        val rebalance = _state.value.rebalance ?: return
        change(said = false) { plans.undo(planId, rebalance) }
    }

    /** Balances the rest of the week, from today, each day around its own meals. */
    fun rebalanceWeek() = change { plans.rebalanceWeek(planId) }

    fun dismissRebalance() {
        _state.update { it.copy(rebalance = null, notice = null) }
    }

    /** An ingredient substituted in its window: the plan it left, what it rebalanced, and why a model did not reword the text where it was asked to. */
    fun substituted(result: Rebalanced) {
        _state.update { it.copy(plan = result.plan, rebalance = result.rebalance.shown(), notice = result.fallbackReason) }
    }

    /** A day filled another way, taken in its window. */
    fun dayTaken(plan: MealPlan) {
        _state.update { it.copy(plan = plan) }
    }

    /** The plan read anew: another request may have changed it first. */
    fun readAgain() = reload()

    /** Makes the plan, a part of it or a day again, as the window asked. */
    fun reroll(asked: Reroll) {
        if (_state.value.changing) return
        viewModelScope.launch {
            _state.update { it.copy(changing = true, error = null, refused = null, inTheWay = null) }
            attempt { plans.reroll(planId, asked) }
                .onSuccess { plan -> _state.update { it.copy(plan = plan, changing = false) } }
                .onFailure { e ->
                    val allow = when ((e as? ApiException)?.code) {
                        "LOCKED_RECIPE_INELIGIBLE" -> DROP_LOCKS.takeUnless { asked.dropIneligibleLocks }
                        "NOTHING_TO_REGENERATE" -> INCLUDE_PAST.takeUnless { asked.includePast }
                        else -> null
                    }
                    val needed = plans.relaxationNeeded(e)
                    _state.update {
                        it.copy(
                            changing = false,
                            refused = allow?.let { what -> Refused(asked, what) },
                            // Where the window left them out, the limits the plan may already let give way.
                            inTheWay = needed?.let { n -> InTheWay(n, asked.allowRelaxations ?: it.plan?.mayGiveWay.orEmpty(), asked) },
                            error = e.takeIf { needed == null },
                        )
                    }
                }
        }
    }

    /** Asks for the refused re-roll again, with what it was refused for allowed. */
    fun allowRefused() {
        val refused = _state.value.refused ?: return
        reroll(if (refused.allow == INCLUDE_PAST) refused.reroll.copy(includePast = true) else refused.reroll.copy(dropIneligibleLocks = true))
    }

    /** Asks for the same re-roll again, letting the limits that stood in the way give way too, in the order they do. */
    fun letGiveWay() {
        val inTheWay = _state.value.inTheWay ?: return
        reroll(inTheWay.reroll.copy(allowRelaxations = PLAN_RELAXATIONS.filter { it in inTheWay.allowed || it in inTheWay.needed.needs }))
    }

    fun dismissInTheWay() {
        _state.update { it.copy(inTheWay = null) }
    }

    fun delete() {
        val plan = _state.value.plan ?: return
        if (_state.value.changing) return
        viewModelScope.launch {
            _state.update { it.copy(changing = true, error = null) }
            attempt { plans.delete(plan) }
                .onSuccess { _state.update { it.copy(changing = false, deleted = true) } }
                .onFailure { e -> _state.update { it.copy(changing = false, error = e) } }
        }
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

    private fun ask(mealId: String, how: SwapAsk, seen: List<String>) {
        if (_state.value.offering != null) return
        viewModelScope.launch {
            _state.update { it.copy(offering = mealId, error = null) }
            attempt { plans.offerSwap(planId, mealId, how, seen, _state.value.swapWithPantry) }
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

    companion object {
        /** What a re-roll refused for a lock that may no longer hold is asked again with. */
        const val DROP_LOCKS = "dropIneligibleLocks"

        /** What a re-roll refused because every day asked for has passed is asked again with. */
        const val INCLUDE_PAST = "includePast"
    }
}
