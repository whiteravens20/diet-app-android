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
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Profile
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

/**
 * Plans per profile, offline-first: the Room cache renders immediately, a
 * network refresh replaces it. A failed refresh keeps the cache on screen
 * and flags [UiState.offline] instead of erroring the whole screen.
 */
@HiltViewModel
class MealPlansViewModel @Inject constructor(
    private val users: UserRepository,
    private val plans: MealPlanRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val profiles: List<Profile> = emptyList(),
        val selectedProfileId: String? = null,
        val plans: List<MealPlan> = emptyList(),
        val refreshing: Boolean = false,
        val offline: Boolean = false,
        val generating: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val selectedProfile: Profile?
        get() = _state.value.let { s -> s.profiles.find { it.id == s.selectedProfileId } }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            runCatching { users.profiles() }
                .onSuccess { profiles ->
                    _state.value = UiState(loading = false, profiles = profiles)
                    profiles.firstOrNull()?.let { selectProfile(it.id) }
                }
                .onFailure { _state.value = UiState(loading = false, error = it) }
        }
    }

    fun selectProfile(profileId: String) {
        _state.update { it.copy(selectedProfileId = profileId, plans = emptyList(), error = null) }
        viewModelScope.launch {
            val cached = plans.cachedPlans(profileId)
            _state.update { it.copy(plans = cached, refreshing = true, offline = false) }
            runCatching { plans.refresh(profileId) }
                .onSuccess { fresh ->
                    ifStillSelected(profileId) { it.copy(plans = fresh, refreshing = false) }
                }
                .onFailure { e ->
                    ifStillSelected(profileId) {
                        it.copy(
                            refreshing = false,
                            offline = e is IOException,
                            error = e.takeIf { _ -> e !is IOException },
                        )
                    }
                }
        }
    }

    fun generate(durationDays: Int, mealCount: Int) {
        val profileId = _state.value.selectedProfileId ?: return
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            runCatching {
                plans.generate(
                    profileId = profileId,
                    startDate = LocalDate.now().toString(),
                    durationDays = durationDays,
                    mealCount = mealCount,
                )
            }
                .onSuccess { plan ->
                    ifStillSelected(profileId) {
                        it.copy(generating = false, plans = listOf(plan) + it.plans.filter { p -> p.id != plan.id })
                    }
                }
                .onFailure { e -> _state.update { it.copy(generating = false, error = e) } }
        }
    }

    /** Guards against a profile switch racing an in-flight load. */
    private fun ifStillSelected(profileId: String, transform: (UiState) -> UiState) {
        _state.update { if (it.selectedProfileId == profileId) transform(it) else it }
    }
}
