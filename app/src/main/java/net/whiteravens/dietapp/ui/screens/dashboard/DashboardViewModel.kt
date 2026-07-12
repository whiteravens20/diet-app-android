package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.Profile
import javax.inject.Inject

/** Profiles with their engine-computed calorie targets, fetched in parallel. */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val users: UserRepository,
) : ViewModel() {

    data class ProfileStats(val profile: Profile, val target: CalorieTarget?)

    data class UiState(
        val loading: Boolean = true,
        val profiles: List<ProfileStats> = emptyList(),
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
            runCatching {
                coroutineScope {
                    users.profiles()
                        .map { profile ->
                            async {
                                // A failed calorie fetch degrades one card, not the screen.
                                ProfileStats(profile, runCatching { users.calorieTarget(profile.id) }.getOrNull())
                            }
                        }
                        .map { it.await() }
                }
            }
                .onSuccess { _state.value = UiState(loading = false, profiles = it) }
                .onFailure { _state.value = UiState(loading = false, error = it) }
        }
    }
}
