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
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/** Profiles with their engine-computed calorie targets, fetched in parallel. */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val users: UserRepository,
) : ViewModel() {

    /** A profile, and its calorie target or why there is none to show. */
    data class ProfileStats(val profile: Profile, val target: CalorieTarget?, val targetError: Throwable? = null)

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

    fun load() = read(showLoading = true)

    /**
     * Reads the profiles again when the screen comes back, from a profile made or changed elsewhere,
     * keeping what is shown until the answer is there; nothing while the first reading runs.
     */
    fun refresh() {
        if (!_state.value.loading) read(showLoading = false)
    }

    private fun read(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) _state.value = UiState(loading = true)
            attempt {
                coroutineScope {
                    users.profiles()
                        .map { profile ->
                            async {
                                // A failed calorie fetch degrades one card, not the screen, and the card says why.
                                val target = attempt { users.calorieTarget(profile.id) }
                                ProfileStats(profile, target.getOrNull(), target.exceptionOrNull())
                            }
                        }
                        .map { it.await() }
                }
            }
                .onSuccess { _state.value = UiState(loading = false, profiles = it) }
                .onFailure { e -> if (showLoading || _state.value.profiles.isEmpty()) _state.value = UiState(loading = false, error = e) }
        }
    }
}
