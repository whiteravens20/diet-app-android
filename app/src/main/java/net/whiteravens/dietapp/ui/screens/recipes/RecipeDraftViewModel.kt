// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.AiModes
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeDraftAsk
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * A recipe of one's own drafted by a model from choices, as the web's window asks for it: the
 * profiles it can be for, with what each favours, excludes and is allergic to; whether the
 * instance's model writes it, which puts it before the curators as well; the draft asked for
 * once; and the recipe the model wrote, which the window opens.
 */
@HiltViewModel
class RecipeDraftViewModel @Inject constructor(
    private val recipes: RecipeRepository,
    private val profiles: ProfileRepository,
    private val users: UserRepository,
) : ViewModel() {

    data class UiState(
        val profiles: List<ProfileDetails> = emptyList(),
        val loading: Boolean = true,
        /** The instance's model writes the recipe, and it goes before the curators as well: the window says so. */
        val toCurators: Boolean = false,
        val drafting: Boolean = false,
        /** The recipe the model wrote: the window is done. */
        val drafted: Recipe? = null,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            attempt { profiles.profiles() }
                .onSuccess { found -> _state.update { it.copy(profiles = found, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e) } }
        }
        viewModelScope.launch {
            attempt { users.me() }.onSuccess { me -> _state.update { it.copy(toCurators = me.aiMode == AiModes.INSTANCE) } }
        }
    }

    fun draft(ask: RecipeDraftAsk) {
        if (_state.value.drafting) return
        viewModelScope.launch {
            _state.update { it.copy(drafting = true, error = null) }
            attempt { recipes.draft(ask) }
                .onSuccess { recipe -> _state.update { it.copy(drafting = false, drafted = recipe) } }
                .onFailure { e -> _state.update { it.copy(drafting = false, error = e) } }
        }
    }
}
