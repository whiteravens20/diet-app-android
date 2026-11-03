// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * The window the words of a recipe a substitution made are corrected in, in the reader's language:
 * its title, description and steps, from what the recipe says now. They are written in that recipe,
 * so the plans that hold it read them at once; its ingredients and nutrition stay as they are.
 */
@HiltViewModel
class RecipeTextViewModel @Inject constructor(
    private val recipes: RecipeRepository,
) : ViewModel() {

    data class UiState(
        val recipeId: String? = null,
        val loading: Boolean = true,
        val loadError: Throwable? = null,
        val title: String = "",
        val description: String = "",
        val steps: List<String> = listOf(""),
        val saving: Boolean = false,
        /** Why the API did not take the words. */
        val refused: Throwable? = null,
        /** The words were written: the window has done its work. */
        val done: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Reads what [recipeId] says now; a window opened again on it keeps what was typed. */
    fun open(recipeId: String) {
        if (_state.value.recipeId == recipeId && _state.value.loadError == null) return
        _state.value = UiState(recipeId = recipeId)
        viewModelScope.launch {
            attempt { recipes.byId(recipeId) }
                .onSuccess { recipe ->
                    _state.update {
                        it.copy(loading = false, title = recipe.title, description = recipe.description, steps = recipe.steps.ifEmpty { listOf("") })
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e) } }
        }
    }

    fun typeTitle(typed: String) = _state.update { it.copy(title = typed, refused = null) }

    fun typeDescription(typed: String) = _state.update { it.copy(description = typed, refused = null) }

    fun typeStep(index: Int, typed: String) = _state.update {
        it.copy(steps = it.steps.mapIndexed { at, step -> if (at == index) typed else step }, refused = null)
    }

    fun addStep() = _state.update { it.copy(steps = it.steps + "") }

    fun removeStep(index: Int) = _state.update { now ->
        if (now.steps.size > 1) now.copy(steps = now.steps.filterIndexed { at, _ -> at != index }, refused = null) else now
    }

    /** Moves the step at [index] [by] one place up (-1) or down (1). */
    fun moveStep(index: Int, by: Int) = _state.update { now ->
        val to = index + by
        if (to !in now.steps.indices) return@update now
        now.copy(steps = now.steps.toMutableList().apply { add(to, removeAt(index)) })
    }

    fun save() {
        val current = _state.value
        val id = current.recipeId ?: return
        if (current.saving || current.loading) return
        _state.update { it.copy(saving = true, refused = null) }
        viewModelScope.launch {
            attempt { recipes.reword(id, current.title.trim(), current.description.trim(), current.steps.map { it.trim() }.filter { it.isNotEmpty() }) }
                .onSuccess { _state.update { it.copy(saving = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, refused = e) } }
        }
    }
}
