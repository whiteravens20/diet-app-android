// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.ui.common.attempt
import java.util.Locale
import javax.inject.Inject

/** The account's profiles: the list, a profile deleted, and the first one's steps when there is none. */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val users: UserRepository,
    private val server: ServerRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val list: List<ProfileDetails> = emptyList(),
        val loadError: Throwable? = null,
        /** A change refused outside the form: a profile that could not be deleted. */
        val error: Throwable? = null,
        val deleting: Boolean = false,
        /** The profile the first profile's steps have just made: the screen says what comes next. */
        val made: Profile? = null,
        val unit: WeightUnit = WeightUnit.of(null, Locale.getDefault()),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** How many profiles the instance allows an account, and what its catalogue holds; null until it has said. */
    val rules: StateFlow<InstanceRules?> = server.rules

    /** Reads the profiles again: on opening, on coming back from a form, and on asking. */
    fun load() {
        viewModelScope.launch {
            if (server.rules.value == null) launch { server.refresh() }
            val unit = attempt { users.me() }.getOrNull()?.let { WeightUnit.of(it.weightUnit, Locale.getDefault()) }
            attempt { profiles.profiles() }
                .onSuccess { list ->
                    _state.update { it.copy(loading = false, list = list, loadError = null, unit = unit ?: it.unit) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e, unit = unit ?: it.unit) } }
        }
    }

    /** The first profile's steps made [profile]: the list is read again, and says what comes next. */
    fun made(profile: Profile) {
        _state.update { it.copy(made = profile) }
        load()
    }

    fun delete(profile: ProfileDetails) {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            attempt { profiles.delete(profile.id) }
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            deleting = false,
                            list = state.list.filterNot { it.id == profile.id },
                            made = state.made?.takeUnless { it.id == profile.id },
                        )
                    }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(deleting = false, error = e) } }
        }
    }
}
