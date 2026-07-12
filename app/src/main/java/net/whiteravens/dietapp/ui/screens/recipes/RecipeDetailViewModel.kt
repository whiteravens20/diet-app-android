package net.whiteravens.dietapp.ui.screens.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.ui.navigation.RecipeDetailRoute
import javax.inject.Inject

/** One recipe, fetched by id (network-only, like the catalogue). */
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
) : ViewModel() {

    private val recipeId: String = savedStateHandle.toRoute<RecipeDetailRoute>().recipeId

    data class UiState(
        val loading: Boolean = true,
        val recipe: Recipe? = null,
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
            runCatching { recipes.byId(recipeId) }
                .onSuccess { _state.value = UiState(loading = false, recipe = it) }
                .onFailure { _state.value = UiState(loading = false, error = it) }
        }
    }
}
