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
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.domain.PreferencesChange
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.ui.common.ReadingAnew
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.navigation.RecipeDetailRoute
import java.io.IOException
import javax.inject.Inject

/**
 * One recipe, fetched by id (network-only, like the catalogue), and what the account's profiles say
 * of it, as the web's view has it: each profile puts it among its favourites or takes its mark off,
 * and the ingredients of its lines are marked as a profile's favourites or as ones it avoids.
 */
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val profiles: ProfileRepository,
    private val favorites: FavoriteRepository,
) : ViewModel() {

    // Read by its name: `toRoute` needs Android's own classes, which the tests on the JVM do not have.
    private val recipeId: String = checkNotNull(savedStateHandle.get<String>(RecipeDetailRoute::recipeId.name))

    /** How the profile [profileId], called [name], has marked the recipe (`favorite`, `often`, `avoid`); null where it has not. */
    data class ProfileMark(val profileId: String, val name: String, val sentiment: String?)

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
        /** The account's profiles, with the ingredients each favours and avoids. */
        val profiles: List<ProfileDetails> = emptyList(),
        /** The profile the lines' ingredients are marked for, where one was chosen; else the first. */
        val prefsProfileId: String? = null,
        /** A change of the profile's ingredients on its way to the API. */
        val savingPrefs: Boolean = false,
        val prefsFailed: Throwable? = null,
        /** A read asked for by pulling the screen down is under way. */
        val pulled: Boolean = false,
        /** When what is shown was read, in the phone's milliseconds; null before the first answer. */
        val readAt: Long? = null,
        /** Reading it anew failed for want of a network: what is shown is as it was at [readAt]. */
        val offline: Boolean = false,
    ) {
        /** The profile the lines' ingredients are marked for. */
        val prefsProfile: ProfileDetails? get() = profiles.firstOrNull { it.id == prefsProfileId } ?: profiles.firstOrNull()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val again = ReadingAnew()

    init {
        load()
        readProfiles()
    }

    fun load() {
        again.begun()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, recipe = null, error = null) }
            attempt { recipes.byId(recipeId) }
                .onSuccess { recipe -> _state.update { it.copy(loading = false, recipe = recipe, readAt = System.currentTimeMillis(), offline = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e) } }
        }
    }

    /**
     * Reads the recipe and the profiles' marks again when the screen is shown again; on coming back to
     * the front only as often as [ReadingAnew] lets it.
     */
    fun shownAgain(cameToTheFront: Boolean) {
        if (!_state.value.loading && again.due(cameToTheFront)) readAnew()
    }

    /** Reads the recipe and the profiles' marks again at once, the screen pulled down. */
    fun pull() {
        if (_state.value.loading || _state.value.pulled) return
        _state.update { it.copy(pulled = true) }
        readAnew()
    }

    /** Reads what the screen shows again, keeping it until the answers are there; a recipe that could not be read is asked for whole. */
    private fun readAnew() {
        if (_state.value.recipe == null) {
            _state.update { it.copy(pulled = false) }
            load()
            readProfiles()
            return
        }
        again.begun()
        readProfiles()
        viewModelScope.launch {
            attempt { recipes.byId(recipeId) }
                .onSuccess { recipe -> _state.update { it.copy(recipe = recipe, pulled = false, readAt = System.currentTimeMillis(), offline = false) } }
                .onFailure { e -> _state.update { it.copy(pulled = false, offline = e is IOException) } }
        }
    }

    /**
     * Puts the recipe among [profileId]'s favourites, or takes the profile's mark off it, however it
     * was marked. A mark that was gone already is off, which is what was asked.
     */
    fun toggleMark(profileId: String) {
        val mark = _state.value.marks.firstOrNull { it.profileId == profileId } ?: return
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

    /** Shows the lines' ingredients as [profileId] marked them. */
    fun pickPrefsProfile(profileId: String) {
        if (_state.value.savingPrefs) return
        _state.update { it.copy(prefsProfileId = profileId, prefsFailed = null) }
    }

    /**
     * Marks [ingredientId] as one of the profile's favourites, or as one it avoids where [avoid], or
     * takes that mark off where it is there: an ingredient put on one list leaves the other.
     */
    fun toggleIngredient(ingredientId: String, avoid: Boolean) {
        val profile = _state.value.prefsProfile ?: return
        if (_state.value.savingPrefs) return
        val favourites = profile.preferences.favoriteIngredientIds.toMutableList()
        val avoided = profile.preferences.excludedIngredientIds.toMutableList()
        val (on, other) = if (avoid) avoided to favourites else favourites to avoided
        if (ingredientId in on) {
            on.remove(ingredientId)
        } else {
            on.add(ingredientId)
            other.remove(ingredientId)
        }
        _state.update { it.copy(savingPrefs = true, prefsFailed = null) }
        viewModelScope.launch {
            attempt { profiles.updatePreferences(profile.id, PreferencesChange(favoriteIngredientIds = favourites, excludedIngredientIds = avoided)) }
                // The answer is the profile as it is now: its lists stand in place of those shown.
                .onSuccess { saved -> _state.update { state -> state.copy(savingPrefs = false, profiles = state.profiles.map { if (it.id == saved.id) saved else it }) } }
                .onFailure { e -> _state.update { it.copy(savingPrefs = false, prefsFailed = e) } }
        }
    }

    private fun marked(profileId: String, sentiment: String?) {
        _state.update { state ->
            state.copy(marking = null, marks = state.marks.map { if (it.profileId == profileId) it.copy(sentiment = sentiment) else it })
        }
    }

    /**
     * The account's profiles, and how each has marked the recipe: a profile whose marks cannot be
     * read is not offered, so that no mark is written over unseen.
     */
    private fun readProfiles() {
        viewModelScope.launch {
            val before = _state.value.marks
            val kept = _state.value.profiles
            val found = attempt { profiles.profiles() }.getOrNull() ?: return@launch
            // An ingredient favoured or avoided while this was read was answered after it: the profiles shown are newer.
            _state.update { if (it.profiles === kept) it.copy(profiles = found) else it }
            val marks = coroutineScope {
                found.map { profile ->
                    async {
                        attempt { favorites.marked(profile.id) }.getOrNull()?.let { marked ->
                            ProfileMark(profile.id, profile.figures.name, marked.firstOrNull { it.recipeId == recipeId }?.sentiment)
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            // A mark put on or taken off while these were read was answered after them: the marks shown are newer.
            _state.update { if (it.marks === before) it.copy(marks = marks) else it }
        }
    }

    private companion object {
        /** How a recipe is marked where nothing else is said of it. */
        const val FAVORITE = "favorite"
    }
}
