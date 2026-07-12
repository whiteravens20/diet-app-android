package net.whiteravens.dietapp.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.domain.Recipe
import javax.inject.Inject

/** Server-paginated recipe search with a debounced query. */
@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val recipes: RecipeRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val items: List<Recipe> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        val error: Throwable? = null,
    )

    val query = MutableStateFlow("")

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        search(reset = true)
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            query.drop(1).debounce(350).distinctUntilChanged().collect { search(reset = true) }
        }
    }

    fun retry() = search(reset = true)

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.loading || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        searchJob = viewModelScope.launch {
            runCatching { recipes.search(query.value, page = s.page + 1) }
                .onSuccess { pageResult ->
                    _state.update {
                        it.copy(
                            items = it.items + pageResult.items,
                            page = pageResult.page,
                            hasMore = pageResult.page < pageResult.totalPages,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loadingMore = false, error = e) } }
        }
    }

    private fun search(reset: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (reset) _state.value = UiState(loading = true)
            runCatching { recipes.search(query.value, page = 1) }
                .onSuccess { pageResult ->
                    _state.value = UiState(
                        loading = false,
                        items = pageResult.items,
                        page = pageResult.page,
                        hasMore = pageResult.page < pageResult.totalPages,
                    )
                }
                .onFailure { e -> _state.value = UiState(loading = false, error = e) }
        }
    }
}
