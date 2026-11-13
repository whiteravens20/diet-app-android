// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Reroll
import net.whiteravens.dietapp.domain.RerollPreview
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * What making a plan again would do, as the web's window asks it: the API is asked about the choice
 * on the window's screen once the choice has paused, and the answer to an older choice is shown as
 * such until the newer one is there. Nothing is drawn or changed. It knows as well the diet the whole
 * plan made again is made for, so that the window says what wasting less costs on it.
 */
@HiltViewModel
class RerollViewModel @Inject constructor(
    private val plans: MealPlanRepository,
    private val server: ServerRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    /** A question: what [reroll] would do to the plan [planId] as it stood at [revision]. */
    data class Asked(val planId: String, val revision: Int, val reroll: Reroll)

    data class UiState(
        /** What was asked last. */
        val asked: Asked? = null,
        /** The last answer; null before the first. */
        val preview: RerollPreview? = null,
        /** The preview is the answer to [asked]; false while a newer question is on its way. */
        val current: Boolean = false,
        /** Why the last question could not be answered. */
        val error: Throwable? = null,
        /** The diet the whole plan made again is made for: the plan's own, or its profile's as it is now; null until known. */
        val wholeDiet: String? = null,
        /** The profile's own limits on repeats, which a plan that wastes nothing loosens; null until known. */
        val repeats: Repeats? = null,
    )

    /** What the instance says, its figures of a plan that wastes less among them. */
    val rules: StateFlow<InstanceRules?> = server.rules

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var asking: Job? = null

    /**
     * The window opened anew for [plan]: nothing of an earlier opening is shown, and the first question
     * goes at once. A plan that follows its profile's diet is made again on the profile's diet of now.
     */
    fun open(plan: MealPlan) {
        asking?.cancel()
        _state.value = UiState(wholeDiet = plan.ownDiet)
        if (server.rules.value == null) viewModelScope.launch { attempt { server.refresh() } }
        // The profile's diet of now, for a plan that follows it, and its own limits on repeats.
        viewModelScope.launch {
            attempt { profiles.profile(plan.profileId) }.onSuccess { profile ->
                _state.update { it.copy(wholeDiet = plan.ownDiet ?: profile.figures.dietType, repeats = Repeats.of(profile.preferences)) }
            }
        }
    }

    /**
     * Asks what [reroll] would do to the plan [planId] at [revision]: at once where nothing was asked
     * yet, once the choice has paused otherwise. A question answered, or on its way, is not asked again.
     */
    fun ask(planId: String, revision: Int, reroll: Reroll) {
        val asked = Asked(planId, revision, reroll)
        val before = _state.value
        if (before.asked == asked && (before.current || asking?.isActive == true)) return
        send(asked, pause = before.asked != null)
    }

    /** Asks again what could not be answered. */
    fun retry() {
        val asked = _state.value.asked ?: return
        send(asked, pause = false)
    }

    private fun send(asked: Asked, pause: Boolean) {
        asking?.cancel()
        _state.update { it.copy(asked = asked, current = false, error = null) }
        asking = viewModelScope.launch {
            if (pause) delay(PAUSE_MILLIS)
            attempt { plans.previewReroll(asked.planId, asked.reroll) }
                .onSuccess { preview -> _state.update { if (it.asked == asked) it.copy(preview = preview, current = true) else it } }
                .onFailure { e -> _state.update { if (it.asked == asked) it.copy(error = e) else it } }
        }
    }

    companion object {
        /** How long the choice has to pause before the API is asked. */
        const val PAUSE_MILLIS = 300L
    }
}
