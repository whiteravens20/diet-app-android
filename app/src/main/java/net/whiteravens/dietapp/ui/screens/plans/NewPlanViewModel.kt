// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.inGiveWayOrder
import net.whiteravens.dietapp.domain.PlanAsk
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.RelaxationNeeded
import net.whiteravens.dietapp.ui.common.attempt
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * A new plan for the profile of the route ([PROFILE_ID]), from the day the route names
 * ([START_DATE]) or from today: its days, what it is made for where that differs from the
 * profile, the preferences it applies and each day's own options. A plan that could only be
 * filled outside its limits is not made: what stands in the way is said, and the same plan is
 * asked for again once the user lets those limits give way.
 */
@HiltViewModel
class NewPlanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val users: UserRepository,
    private val favorites: FavoriteRepository,
    private val plans: MealPlanRepository,
    private val server: ServerRepository,
) : ViewModel() {

    /** A plan refused for its limits: what stands in the way, what was let give way already, and what was asked. */
    data class InTheWay(val needed: RelaxationNeeded, val allowed: List<String>, val ask: PlanAsk)

    data class UiState(
        val loading: Boolean = true,
        val profile: ProfileDetails? = null,
        /** The profile's own daily target, which an empty field of the form stands for. */
        val profileTarget: Int? = null,
        /** The recipes the profile marked and did not mark to avoid: what a meal can be locked to. */
        val liked: List<MarkedRecipe> = emptyList(),
        val sets: List<FavoriteSet> = emptyList(),
        val form: NewPlanForm = NewPlanForm(startDate = LocalDate.now()),
        val showProblems: Boolean = false,
        val generating: Boolean = false,
        val error: Throwable? = null,
        val inTheWay: InTheWay? = null,
        /** The plan made: the form is done. */
        val made: String? = null,
    ) {
        /** The diet and the number of meals the plan is made for: the form's, or the profile's. */
        val planDiet: String get() = form.dietType ?: profile?.figures?.dietType ?: "balanced"
        val planMealCount: Int get() = form.mealCount ?: profile?.figures?.mealCount ?: 3
    }

    private val profileId: String = checkNotNull(savedStateHandle[PROFILE_ID]) { "A new plan is made for a profile" }

    /** The day the plan begins on, where the route names one: the calendar's free day that was chosen. */
    private val startDate: LocalDate = savedStateHandle.get<String>(START_DATE)?.let(LocalDate::parse) ?: LocalDate.now()

    private val _state = MutableStateFlow(UiState(form = NewPlanForm(startDate = startDate)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** What the instance's catalogue holds and the most a recipe repeats where it sets one; null until it has said. */
    val rules: StateFlow<InstanceRules?> = server.rules

    init {
        load()
        if (server.rules.value == null) viewModelScope.launch { server.refresh() }
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val target = async { attempt { users.calorieTarget(profileId) }.getOrNull() }
            val marked = async { attempt { favorites.marked(profileId) }.getOrDefault(emptyList()) }
            val sets = async { attempt { favorites.sets(profileId) }.getOrDefault(emptyList()) }
            attempt { profiles.profile(profileId) }
                .onSuccess { profile ->
                    _state.update {
                        it.copy(
                            loading = false,
                            profile = profile,
                            profileTarget = target.await()?.dailyTarget?.roundToInt(),
                            // What a profile marked to avoid is nothing to put into a plan: the API would refuse it.
                            liked = marked.await().filter { recipe -> recipe.sentiment != "avoid" },
                            sets = sets.await(),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e) } }
        }
    }

    fun edit(change: (NewPlanForm) -> NewPlanForm) {
        _state.update { it.copy(form = change(it.form), error = null) }
    }

    fun editDay(date: LocalDate, change: (DayDraft) -> DayDraft) = edit { form -> form.copy(days = form.days + (date to change(form.day(date)))) }

    fun generate() {
        val current = _state.value
        if (current.generating || current.profile == null) return
        _state.update { it.copy(generating = true, error = null, inTheWay = null) }
        viewModelScope.launch {
            // The meals of a day are the instance's to say: read where they have not been yet.
            if (server.rules.value == null) server.refresh()
            val ask = current.form.ask(profileId, current.planMealCount, current.sets, server.rules.value?.mealSlots.orEmpty())
            if (ask == null) {
                _state.update { it.copy(generating = false, showProblems = true) }
                return@launch
            }
            send(ask)
        }
    }

    /** Asks for the same plan again, letting the limits that stood in the way give way too. */
    fun letGiveWay() {
        val inTheWay = _state.value.inTheWay ?: return
        val allowed = inGiveWayOrder(server.rules.value?.relaxations.orEmpty(), inTheWay.allowed + inTheWay.needed.needs)
        // The form says from here on what the plan was let do.
        edit { it.copy(mayGiveWay = allowed) }
        send(inTheWay.ask.copy(allowRelaxations = allowed))
    }

    fun dismissInTheWay() {
        _state.update { it.copy(inTheWay = null) }
    }

    private fun send(ask: PlanAsk) {
        _state.update { it.copy(generating = true, error = null, inTheWay = null) }
        viewModelScope.launch {
            attempt { plans.generate(ask) }
                .onSuccess { plan -> _state.update { it.copy(generating = false, made = plan.id) } }
                .onFailure { e ->
                    val needed = plans.relaxationNeeded(e)
                    _state.update {
                        if (needed != null) it.copy(generating = false, inTheWay = InTheWay(needed, ask.allowRelaxations, ask))
                        else it.copy(generating = false, error = e)
                    }
                }
        }
    }

    companion object {
        /** The route's argument naming the profile the plan is made for. */
        const val PROFILE_ID = "profileId"

        /** The route's argument naming the day the plan begins on, if it is not today. */
        const val START_DATE = "startDate"
    }
}
