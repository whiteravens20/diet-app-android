// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.domain.RecipePage
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * The recipe library as the web's page has it: the shared recipes and the account's own, narrowed
 * by every filter the API takes, a page at a time; the words are looked for once typing pauses.
 * What a profile marked and favours narrows the library for the profile chosen.
 */
@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val recipes: RecipeRepository,
    private val users: UserRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    /** What the catalogue has for the words typed into the filter of ingredients. */
    data class IngredientSearch(val text: String = "", val found: List<IngredientChoice> = emptyList(), val searched: Boolean = false)

    data class UiState(
        val filter: RecipeFilter = RecipeFilter(),
        val loading: Boolean = true,
        val items: List<Recipe> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        /** The profiles whose marks and favourite ingredients can narrow the library. */
        val profiles: List<Profile> = emptyList(),
        /** Whether the account has a model to ask; null until that is known. */
        val aiOn: Boolean? = null,
        val ingredientSearch: IngredientSearch = IngredientSearch(),
        val error: Throwable? = null,
    )

    /** The words of the search field as typed; looked for once typing pauses. */
    val query = MutableStateFlow("")

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var ingredientJob: Job? = null

    init {
        viewModelScope.launch {
            val found = attempt { users.profiles() }.getOrDefault(emptyList())
            _state.update { it.copy(profiles = found, filter = it.filter.copy(profileId = it.filter.profileId ?: found.firstOrNull()?.id)) }
        }
        viewModelScope.launch {
            val aiOn = attempt { users.me() }.getOrNull()?.let { it.aiMode != "none" }
            _state.update { it.copy(aiOn = aiOn) }
        }
        load()
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            query.drop(1).debounce(350).distinctUntilChanged().collect { words ->
                _state.update { it.copy(filter = it.filter.copy(search = words)) }
                load()
            }
        }
    }

    fun retry() = load()

    /** Narrows the library as [filter] says, from its first page. */
    fun narrow(filter: RecipeFilter) {
        _state.update { it.copy(filter = filter.copy(search = it.filter.search)) }
        load()
    }

    fun clearFilters() = narrow(_state.value.filter.cleared())

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.loading || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        searchJob = viewModelScope.launch {
            attempt { page(s, s.page + 1) }
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

    /** What the catalogue has for [text]: asked once two letters are typed and typing pauses. */
    fun searchIngredients(text: String) {
        _state.update { it.copy(ingredientSearch = IngredientSearch(text = text)) }
        ingredientJob?.cancel()
        if (text.trim().length < SEARCH_FROM) return
        ingredientJob = viewModelScope.launch {
            delay(SEARCH_DELAY_MS)
            val found = attempt { profiles.ingredients(text.trim()) }.getOrDefault(emptyList())
            _state.update { if (it.ingredientSearch.text != text) it else it.copy(ingredientSearch = it.ingredientSearch.copy(found = found, searched = true)) }
        }
    }

    private fun load() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, items = emptyList(), page = 1, hasMore = false, error = null) }
            attempt { page(_state.value, 1) }
                .onSuccess { pageResult ->
                    _state.update {
                        it.copy(
                            loading = false,
                            items = pageResult.items,
                            page = pageResult.page,
                            hasMore = pageResult.page < pageResult.totalPages,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e) } }
        }
    }

    private suspend fun page(state: UiState, page: Int): RecipePage = recipes.search(state.filter, page)

    companion object {
        /** How many letters a search of the catalogue's ingredients starts at. */
        const val SEARCH_FROM = 2

        /** How long typing pauses before the catalogue is asked. */
        private const val SEARCH_DELAY_MS = 300L
    }
}
