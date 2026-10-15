// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.PreferencesChange
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.ProfilePreferences
import net.whiteravens.dietapp.ui.common.attempt
import java.util.Locale
import javax.inject.Inject

/**
 * The account's profiles: the list, a profile deleted, the first one's steps when there is none,
 * and each profile's preferences changed in place. A change of preferences sends the part that
 * changed alone, and is shown from the moment it was chosen; it is taken back when it is refused.
 */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val users: UserRepository,
    private val server: ServerRepository,
) : ViewModel() {

    /** A list of ingredients a profile keeps among its preferences. */
    enum class IngredientList { FAVOURITE, DISLIKED, AVOIDED }

    /** What a picker of ingredients has typed, and what the catalogue found for it. */
    data class Search(val text: String = "", val found: List<IngredientChoice> = emptyList(), val searched: Boolean = false)

    data class UiState(
        val loading: Boolean = true,
        val list: List<ProfileDetails> = emptyList(),
        val loadError: Throwable? = null,
        /** A change refused outside the form: a preference, or a profile that could not be deleted. */
        val error: Throwable? = null,
        val deleting: Boolean = false,
        /** A change of preferences on its way to the API, and the profile it is for. */
        val sending: Pair<String, PreferencesChange>? = null,
        /** The names of the ingredients the profiles keep, by their ids. */
        val names: Map<String, String> = emptyMap(),
        val searches: Map<Pair<String, IngredientList>, Search> = emptyMap(),
        /** The profile the first profile's steps have just made: the screen says what comes next. */
        val made: Profile? = null,
        val unit: WeightUnit = WeightUnit.of(null, Locale.getDefault()),
    ) {
        /** The preferences of [profile] as the screen shows them: what is kept, with what is being sent on top. */
        fun preferencesOf(profile: ProfileDetails): Shown {
            val change = sending?.takeIf { it.first == profile.id }?.second
            val kept = profile.preferences
            return Shown(
                allergens = change?.allergens ?: profile.figures.allergens,
                favourite = change?.favoriteIngredientIds ?: kept.favoriteIngredientIds,
                disliked = change?.dislikedIngredientIds ?: kept.dislikedIngredientIds,
                avoided = change?.excludedIngredientIds ?: kept.excludedIngredientIds,
                cuisines = change?.preferredCuisines ?: kept.preferredCuisines,
            )
        }
    }

    /** A profile's preferences as they are shown. */
    data class Shown(
        val allergens: List<String>,
        val favourite: List<String>,
        val disliked: List<String>,
        val avoided: List<String>,
        val cuisines: List<String>,
    ) {
        fun ids(list: IngredientList): List<String> = when (list) {
            IngredientList.FAVOURITE -> favourite
            IngredientList.DISLIKED -> disliked
            IngredientList.AVOIDED -> avoided
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** How many profiles the instance allows an account, and what its catalogue holds; null until it has said. */
    val rules: StateFlow<InstanceRules?> = server.rules

    private val searching = mutableMapOf<Pair<String, IngredientList>, Job>()

    /** Reads the profiles again: on opening, on coming back from a form, and on asking. */
    fun load() {
        viewModelScope.launch {
            if (server.rules.value == null) launch { server.refresh() }
            val unit = attempt { users.me() }.getOrNull()?.let { WeightUnit.of(it.weightUnit, Locale.getDefault()) }
            attempt { profiles.profiles() }
                .onSuccess { list ->
                    _state.update { it.copy(loading = false, list = list, loadError = null, unit = unit ?: it.unit) }
                    nameIngredientsOf(list)
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

    fun changeAllergens(profile: ProfileDetails, allergens: List<String>) =
        change(profile, PreferencesChange(allergens = ProfileBounds.ALLERGENS.filter { it in allergens }))

    /** The cuisines in the order the application lists them, whatever order they were ticked in. */
    fun changeCuisines(profile: ProfileDetails, cuisines: List<String>) =
        change(profile, PreferencesChange(preferredCuisines = ProfileBounds.CUISINES.filter { it in cuisines }))

    /** Adds [ingredient] to one of [profile]'s lists, and empties that list's search. */
    fun add(profile: ProfileDetails, list: IngredientList, ingredient: IngredientChoice) {
        val ids = _state.value.preferencesOf(profile).ids(list)
        if (_state.value.sending != null || ingredient.id in ids) return
        _state.update { it.copy(names = it.names + (ingredient.id to ingredient.name)) }
        search(profile.id, list, "")
        change(profile, list.change(ids + ingredient.id))
    }

    fun remove(profile: ProfileDetails, list: IngredientList, id: String) {
        change(profile, list.change(_state.value.preferencesOf(profile).ids(list) - id))
    }

    private fun IngredientList.change(ids: List<String>) = when (this) {
        IngredientList.FAVOURITE -> PreferencesChange(favoriteIngredientIds = ids)
        IngredientList.DISLIKED -> PreferencesChange(dislikedIngredientIds = ids)
        IngredientList.AVOIDED -> PreferencesChange(excludedIngredientIds = ids)
    }

    private fun change(profile: ProfileDetails, change: PreferencesChange) {
        if (_state.value.sending != null) return
        _state.update { it.copy(sending = profile.id to change, error = null) }
        viewModelScope.launch {
            attempt { profiles.updatePreferences(profile.id, change) }
                // The answer is the profile as it is now: shown in place of the one kept, so that nothing flickers back.
                .onSuccess { saved -> _state.update { state -> state.copy(sending = null, list = state.list.map { if (it.id == saved.id) saved else it }) } }
                .onFailure { e -> _state.update { it.copy(sending = null, error = e) } }
        }
    }

    /** What the catalogue has for [text], typed into one of a profile's pickers: asked once two letters are typed and typing pauses. */
    fun search(profileId: String, list: IngredientList, text: String) {
        val key = profileId to list
        _state.update { it.copy(searches = it.searches + (key to Search(text = text))) }
        searching.remove(key)?.cancel()
        if (text.trim().length < SEARCH_FROM) return
        searching[key] = viewModelScope.launch {
            delay(SEARCH_DELAY_MS)
            val found = attempt { profiles.ingredients(text.trim()) }.getOrDefault(emptyList())
            _state.update { state ->
                val now = state.searches[key]
                if (now?.text != text) state else state.copy(searches = state.searches + (key to now.copy(found = found, searched = true)))
            }
        }
    }

    /** Asks for the names of the ingredients the profiles keep that the screen has no name for. */
    private suspend fun nameIngredientsOf(list: List<ProfileDetails>) {
        val known = _state.value.names
        val ids = list.flatMap { it.preferences.ingredientIds() }.distinct().filterNot { it in known }
        if (ids.isEmpty()) return
        attempt { profiles.ingredientsOf(ids) }
            .onSuccess { found -> _state.update { state -> state.copy(names = state.names + found.associate { it.id to it.name }) } }
    }

    private fun ProfilePreferences.ingredientIds() = favoriteIngredientIds + dislikedIngredientIds + excludedIngredientIds

    companion object {
        /** How many letters a search of the catalogue starts at. */
        const val SEARCH_FROM = 2

        /** How long typing pauses before the catalogue is searched. */
        const val SEARCH_DELAY_MS = 300L
    }
}
