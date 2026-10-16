// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.navigation.RecipeDetailRoute
import javax.inject.Inject

/**
 * One recipe, fetched by id (network-only, like the catalogue), and how each profile of the account
 * has marked it: a profile puts it among its favourites, or takes its mark off, as the web's view does.
 */
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val users: UserRepository,
    private val favorites: FavoriteRepository,
) : ViewModel() {

    // Read by its name: `toRoute` needs Android's own classes, which the tests on the JVM do not have.
    private val recipeId: String = checkNotNull(savedStateHandle.get<String>(RecipeDetailRoute::recipeId.name))

    /** How [profile] has marked the recipe (`favorite`, `often`, `avoid`); null where it has not. */
    data class ProfileMark(val profile: Profile, val sentiment: String?)

    /** A mark that could not be put on, or taken off where [removing], and why. */
    data class MarkFailed(val removing: Boolean, val error: Throwable)

    data class UiState(
        val loading: Boolean = true,
        val recipe: Recipe? = null,
        val error: Throwable? = null,
        /** The profiles whose marks could be read, and how each has marked the recipe. */
        val marks: List<ProfileMark> = emptyList(),
        /** The profile whose mark is on its way to the API. */
        val marking: String? = null,
        val markFailed: MarkFailed? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
        readMarks()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, recipe = null, error = null) }
            attempt { recipes.byId(recipeId) }
                .onSuccess { recipe -> _state.update { it.copy(loading = false, recipe = recipe) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e) } }
        }
    }

    /**
     * Puts the recipe among [profileId]'s favourites, or takes the profile's mark off it, however it
     * was marked. A mark that was gone already is off, which is what was asked.
     */
    fun toggleMark(profileId: String) {
        val mark = _state.value.marks.firstOrNull { it.profile.id == profileId } ?: return
        if (_state.value.marking != null) return
        val removing = mark.sentiment != null
        _state.update { it.copy(marking = profileId, markFailed = null) }
        viewModelScope.launch {
            attempt { if (removing) favorites.unmark(profileId, recipeId) else favorites.mark(profileId, recipeId, FAVORITE, emptyList()) }
                .onSuccess { marked(profileId, if (removing) null else FAVORITE) }
                .onFailure { e ->
                    if (removing && e is ApiException && e.code == "FAVORITE_NOT_FOUND") {
                        marked(profileId, null)
                    } else {
                        _state.update { it.copy(marking = null, markFailed = MarkFailed(removing, e)) }
                    }
                }
        }
    }

    private fun marked(profileId: String, sentiment: String?) {
        _state.update { state ->
            state.copy(marking = null, marks = state.marks.map { if (it.profile.id == profileId) it.copy(sentiment = sentiment) else it })
        }
    }

    /** How each profile has marked the recipe: a profile whose marks cannot be read is not offered, so that no mark is written over unseen. */
    private fun readMarks() {
        viewModelScope.launch {
            val profiles = attempt { users.profiles() }.getOrNull() ?: return@launch
            val marks = coroutineScope {
                profiles.map { profile ->
                    async {
                        attempt { favorites.marked(profile.id) }.getOrNull()
                            ?.let { marked -> ProfileMark(profile, marked.firstOrNull { it.recipeId == recipeId }?.sentiment) }
                    }
                }.awaitAll().filterNotNull()
            }
            _state.update { it.copy(marks = marks) }
        }
    }

    private companion object {
        /** How a recipe is marked where nothing else is said of it. */
        const val FAVORITE = "favorite"
    }
}
