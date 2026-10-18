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
import net.whiteravens.dietapp.domain.AiModes
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.domain.RecipePage
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * The recipe library as the web's page has it: the shared recipes and the account's own, narrowed
 * by every filter the API takes, a page at a time; the words are looked for once typing pauses. The
 * account's own recipes are a tab of their own, each to be suggested for the shared library or
 * deleted. What a profile marked and favours narrows the library for the profile chosen.
 */
@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val recipes: RecipeRepository,
    private val users: UserRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    enum class Tab { LIBRARY, MINE }

    /** What the catalogue has for the words typed into the filter of ingredients. */
    data class IngredientSearch(val text: String = "", val found: List<IngredientChoice> = emptyList(), val searched: Boolean = false)

    data class UiState(
        val tab: Tab = Tab.LIBRARY,
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
        /** The recipe of one's own being suggested for the shared library or deleted. */
        val changing: String? = null,
        /** What came of suggesting a recipe for the shared library: sent, or why not. */
        val submitted: Result<Unit>? = null,
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
            val aiOn = attempt { users.me() }.getOrNull()?.let { it.aiMode != AiModes.NONE }
            _state.update { it.copy(aiOn = aiOn) }
        }
        followAiMode()
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

    /** AI turned on or off in the account's settings shows here as soon as it is. */
    private fun followAiMode() {
        viewModelScope.launch { users.aiModeChanges.collect { mode -> _state.update { it.copy(aiOn = mode != AiModes.NONE) } } }
    }

    fun selectTab(tab: Tab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab, submitted = null) }
        load()
    }

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

    /** Puts a recipe of one's own before the curators for the library everyone shares. */
    fun submit(id: String) = change(id) {
        val sent = attempt { recipes.submit(id) }
        _state.update { it.copy(changing = null, submitted = sent.map { }) }
    }

    /** Deletes a recipe of one's own; the plans that hold it keep it. */
    fun delete(id: String) = change(id) {
        attempt { recipes.delete(id) }
            .onSuccess { _state.update { it.copy(changing = null, items = it.items.filterNot { recipe -> recipe.id == id }) } }
            .onFailure { e -> _state.update { it.copy(changing = null, error = e) } }
    }

    private fun change(id: String, block: suspend () -> Unit) {
        if (_state.value.changing != null) return
        _state.update { it.copy(changing = id, submitted = null, error = null) }
        viewModelScope.launch { block() }
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

    private suspend fun page(state: UiState, page: Int): RecipePage =
        if (state.tab == Tab.MINE) recipes.mine(page) else recipes.search(state.filter, page)

    companion object {
        /** How many letters a search of the catalogue's ingredients starts at. */
        const val SEARCH_FROM = 2

        /** How long typing pauses before the catalogue is asked. */
        private const val SEARCH_DELAY_MS = 300L
    }
}
