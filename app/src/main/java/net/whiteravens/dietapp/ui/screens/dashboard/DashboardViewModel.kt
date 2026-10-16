// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.WeightRepository
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.FavoriteSet
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.PlanOverview
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.WeightEntry
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * Profiles with their engine-computed calorie targets, what their plan of the moment comes to, their
 * weights and their favourite sets, fetched in parallel; a weight is logged and the last one taken back,
 * and a set made, changed and deleted, from its profile's card.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val users: UserRepository,
    private val plans: MealPlanRepository,
    private val favorites: FavoriteRepository,
    private val weights: WeightRepository,
) : ViewModel() {

    /**
     * A profile, its calorie target or why there is none to show, what its plan of the moment
     * comes to (null where it has no plan) or why that could not be read, its favourite sets
     * with the recipes it marked, which a set is made of, or why they could not be read, and the
     * weights it logged in the last ninety days, the newest first, or why they could not be read.
     */
    data class ProfileStats(
        val profile: Profile,
        val target: CalorieTarget?,
        val targetError: Throwable? = null,
        val overview: PlanOverview? = null,
        val overviewError: Throwable? = null,
        val sets: List<FavoriteSet>? = null,
        val marks: List<MarkedRecipe>? = null,
        val setsError: Throwable? = null,
        val weights: List<WeightEntry>? = null,
        val weightsError: Throwable? = null,
    )

    /** What is typed into a profile's weight log, what is on its way to the API, and what went wrong. */
    data class WeightLog(
        val typed: String = "",
        val logging: Boolean = false,
        /** The last weight is being taken back. */
        val removing: Boolean = false,
        val problem: WeightProblem? = null,
    )

    /** What went wrong in a profile's weight log. */
    sealed interface WeightProblem {
        /** What was typed is no weight within the bounds of the account's unit; nothing was sent. */
        data object OutOfBounds : WeightProblem

        /** The weight typed could not be logged. */
        data class LogFailed(val error: Throwable) : WeightProblem

        /** The last weight could not be taken back. */
        data class RemoveFailed(val error: Throwable) : WeightProblem
    }

    /** The window a set is made or changed in: the profile's, the set changed (null for a new one), and why saving it failed. */
    data class SetEdit(val profileId: String, val set: FavoriteSet?, val saving: Boolean = false, val error: Throwable? = null)

    /** A set that could not be deleted, the profile it is of, and why. */
    data class SetDeleteFailed(val profileId: String, val error: Throwable)

    data class UiState(
        val loading: Boolean = true,
        val profiles: List<ProfileStats> = emptyList(),
        val error: Throwable? = null,
        val editing: SetEdit? = null,
        /** The set being deleted. */
        val deletingSet: String? = null,
        val setDeleteFailed: SetDeleteFailed? = null,
        /** The unit weights are shown and typed in: the account's, and until it has chosen one, that of where the phone is. */
        val unit: WeightUnit = WeightUnit.of(null, Locale.getDefault()),
        /** Each profile's weight log, by the profile's id; one that is not here is empty. */
        val weightLogs: Map<String, WeightLog> = emptyMap(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = read(showLoading = true)

    /**
     * Reads the profiles again when the screen comes back, from a profile made or changed elsewhere,
     * keeping what is shown until the answer is there; nothing while the first reading runs.
     */
    fun refresh() {
        if (!_state.value.loading) read(showLoading = false)
    }

    /** Opens the window that makes a set of [profileId]. */
    fun newSet(profileId: String) = _state.update { it.copy(editing = SetEdit(profileId, null), setDeleteFailed = null) }

    /** Opens the window that changes [set] of [profileId]. */
    fun editSet(profileId: String, set: FavoriteSet) = _state.update { it.copy(editing = SetEdit(profileId, set), setDeleteFailed = null) }

    /** Closes the window of a set, unless it is being saved. */
    fun closeSet() = _state.update { if (it.editing?.saving == true) it else it.copy(editing = null) }

    /** Keeps the set of the window open, as [label] with the recipe [slots] names for each meal: a new one first among its profile's, a changed one where it was. */
    fun saveSet(label: String, slots: Map<String, String>) {
        val edit = _state.value.editing ?: return
        if (edit.saving) return
        _state.update { it.copy(editing = edit.copy(saving = true, error = null)) }
        viewModelScope.launch {
            attempt { if (edit.set == null) favorites.createSet(edit.profileId, label, slots) else favorites.updateSet(edit.set.id, label, slots) }
                .onSuccess { saved ->
                    _state.update { state ->
                        state.copy(
                            editing = null,
                            profiles = state.profiles.map { stats ->
                                if (stats.profile.id != edit.profileId) {
                                    stats
                                } else {
                                    val sets = stats.sets.orEmpty()
                                    stats.copy(sets = if (edit.set == null) listOf(saved) + sets else sets.map { if (it.id == saved.id) saved else it })
                                }
                            },
                        )
                    }
                }
                .onFailure { e -> _state.update { state -> state.copy(editing = state.editing?.copy(saving = false, error = e)) } }
        }
    }

    /** What is typed into [profileId]'s weight log. */
    fun typeWeight(profileId: String, typed: String) = changeLog(profileId) { it.copy(typed = typed) }

    /**
     * Logs what is typed for [profileId], read in the account's unit and sent in kilograms. A weight out
     * of the unit's bounds is said and not sent; one logged empties the field and is the newest.
     */
    fun logWeight(profileId: String) {
        val log = _state.value.weightLogs[profileId] ?: WeightLog()
        if (log.logging || log.typed.isBlank()) return
        val kg = _state.value.unit.typedKg(log.typed)
        if (kg == null) {
            changeLog(profileId) { it.copy(problem = WeightProblem.OutOfBounds) }
            return
        }
        changeLog(profileId) { it.copy(logging = true, problem = null) }
        viewModelScope.launch {
            attempt { weights.log(profileId, kg) }
                .onSuccess { logged ->
                    _state.update { state ->
                        state.copy(
                            weightLogs = state.weightLogs + (profileId to (state.weightLogs[profileId] ?: WeightLog()).copy(typed = "", logging = false)),
                            profiles = state.profiles.map { stats ->
                                if (stats.profile.id != profileId || stats.weights == null) stats
                                else stats.copy(weights = (stats.weights + logged).sortedByDescending { it.recordedAt })
                            },
                        )
                    }
                }
                .onFailure { e -> changeLog(profileId) { it.copy(logging = false, problem = WeightProblem.LogFailed(e)) } }
        }
    }

    /** Takes back the newest weight of [profileId]; asked again, the one before it. */
    fun removeLatestWeight(profileId: String) {
        val latest = _state.value.profiles.firstOrNull { it.profile.id == profileId }?.weights?.firstOrNull() ?: return
        if (_state.value.weightLogs[profileId]?.removing == true) return
        changeLog(profileId) { it.copy(removing = true, problem = null) }
        viewModelScope.launch {
            attempt { weights.delete(latest.id) }
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            weightLogs = state.weightLogs + (profileId to (state.weightLogs[profileId] ?: WeightLog()).copy(removing = false)),
                            profiles = state.profiles.map { stats ->
                                if (stats.profile.id == profileId) stats.copy(weights = stats.weights?.filterNot { it.id == latest.id }) else stats
                            },
                        )
                    }
                }
                .onFailure { e -> changeLog(profileId) { it.copy(removing = false, problem = WeightProblem.RemoveFailed(e)) } }
        }
    }

    private fun changeLog(profileId: String, change: (WeightLog) -> WeightLog) =
        _state.update { it.copy(weightLogs = it.weightLogs + (profileId to change(it.weightLogs[profileId] ?: WeightLog()))) }

    fun deleteSet(profileId: String, setId: String) {
        if (_state.value.deletingSet != null) return
        _state.update { it.copy(deletingSet = setId, setDeleteFailed = null) }
        viewModelScope.launch {
            attempt { favorites.deleteSet(setId) }
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            deletingSet = null,
                            profiles = state.profiles.map { stats ->
                                if (stats.profile.id == profileId) stats.copy(sets = stats.sets?.filterNot { it.id == setId }) else stats
                            },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(deletingSet = null, setDeleteFailed = SetDeleteFailed(profileId, e)) } }
        }
    }

    private fun read(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) _state.update { it.copy(loading = true, profiles = emptyList(), error = null) }
            // Weights are shown in the account's unit; while it cannot be read, in the one shown before.
            val unit = async { attempt { users.me() }.getOrNull()?.let { WeightUnit.of(it.weightUnit, Locale.getDefault()) } }
            val since = Instant.now().minus(WEIGHT_DAYS)
            attempt {
                coroutineScope {
                    users.profiles()
                        .map { profile ->
                            async {
                                // A failed read degrades one card, not the screen, and the card says why.
                                val target = async { attempt { users.calorieTarget(profile.id) } }
                                val overview = async { attempt { plans.overview(profile.id) } }
                                val sets = async { attempt { favorites.sets(profile.id) } }
                                val marks = async { attempt { favorites.marked(profile.id) } }
                                val logged = async { attempt { weights.entries(profile.id, since) } }
                                ProfileStats(
                                    profile,
                                    target.await().getOrNull(),
                                    target.await().exceptionOrNull(),
                                    overview.await().getOrNull(),
                                    overview.await().exceptionOrNull(),
                                    sets = sets.await().getOrNull(),
                                    marks = marks.await().getOrNull(),
                                    setsError = sets.await().exceptionOrNull() ?: marks.await().exceptionOrNull(),
                                    weights = logged.await().getOrNull(),
                                    weightsError = logged.await().exceptionOrNull(),
                                )
                            }
                        }
                        .map { it.await() }
                }
            }
                .onSuccess { found -> _state.update { it.copy(loading = false, profiles = found, error = null, unit = unit.await() ?: it.unit) } }
                .onFailure { e -> if (showLoading || _state.value.profiles.isEmpty()) _state.update { it.copy(loading = false, profiles = emptyList(), error = e) } }
        }
    }

    private companion object {
        /** How far back the weights of a card go, as the web's chart does. */
        val WEIGHT_DAYS: Duration = Duration.ofDays(90)
    }
}
