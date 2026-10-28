// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.domain.LineIngredient
import net.whiteravens.dietapp.domain.RecipePreview
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.navigation.RecipeFormRoute
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import javax.inject.Inject

/**
 * A recipe of one's own written by hand, or a new version of one edited, as the web's form has it:
 * ingredients found in the catalogue by a part of their name, what a serving holds asked for once
 * typing pauses, and the recipe saved. A field the API refuses says why, from what a serving was
 * last asked about and from saving; a recipe that would repeat one of the account's own says which.
 */
@HiltViewModel
class RecipeFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
) : ViewModel() {

    data class UiState(
        /** The recipe edited, by its id; null for a new one. */
        val editing: String? = null,
        /** The recipe edited is being read. */
        val loading: Boolean = false,
        val loadError: Throwable? = null,
        val form: RecipeForm = RecipeForm(),
        /** The words an ingredient is looked for by. */
        val search: String = "",
        /** What the catalogue found for them, as many as are offered. */
        val found: List<LineIngredient> = emptyList(),
        /** Whether the catalogue has answered the words as they are typed now. */
        val searched: Boolean = false,
        /** What a serving holds, as the API last said; kept while it is asked about again. */
        val preview: RecipePreview? = null,
        /** Why the API could not say what a serving holds, where it was not for a field. */
        val previewFailed: Throwable? = null,
        /** What the API refused of the fields a serving was last asked about with. */
        val previewRefusals: RecipeRefusals = RecipeRefusals(),
        /** Saving was asked for: the fields that hold no number where one is needed are marked. */
        val showProblems: Boolean = false,
        /** What the API refused of the recipe when it was last saved. */
        val saveRefusals: RecipeRefusals = RecipeRefusals(),
        val saving: Boolean = false,
        /** Why saving failed, where the fields do not say it all. */
        val error: Throwable? = null,
        /** The recipe of the account's own the one saved would repeat, by its id. */
        val yours: String? = null,
        /** The recipe as it was saved, by its id: the new version of one edited has an id of its own. */
        val saved: String? = null,
    ) {
        /** What the fields say is wrong with them: no number, once saving was asked for, and what the API refused. */
        val marks: RecipeMarks
            get() = RecipeMarks(
                notWhole = if (showProblems) form.notWhole() else emptySet(),
                notNumbers = if (showProblems) form.notNumbers() else emptySet(),
                refusals = previewRefusals.over(saveRefusals),
            )
    }

    // Read by its name: `toRoute` needs Android's own classes, which the tests on the JVM do not have.
    private val recipeId: String? = savedStateHandle.get<String>(RecipeFormRoute::recipeId.name)

    private val _state = MutableStateFlow(UiState(editing = recipeId))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searching: Job? = null

    init {
        load()
        previews()
    }

    /** Reads the recipe edited, to start the form on it; a new recipe starts empty. */
    fun load() {
        val id = recipeId ?: return
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            attempt { recipes.toEdit(id) }
                .onSuccess { kept -> _state.update { it.copy(loading = false, form = RecipeForm.of(kept)) } }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e) } }
        }
    }

    /** Changes the form as [change] does; what was refused of a field that changes is no longer said. */
    fun edit(change: (RecipeForm) -> RecipeForm) {
        _state.update {
            val now = change(it.form)
            it.copy(
                form = now,
                error = null,
                yours = null,
                previewRefusals = it.previewRefusals.after(it.form, now),
                saveRefusals = it.saveRefusals.after(it.form, now),
            )
        }
    }

    /** Looks for what is typed in the catalogue once two letters are typed and typing pauses, as the profile's pickers do. */
    fun search(text: String) {
        _state.update { it.copy(search = text, found = emptyList(), searched = false) }
        searching?.cancel()
        if (text.trim().length < ProfilesViewModel.SEARCH_FROM) return
        searching = viewModelScope.launch {
            delay(ProfilesViewModel.SEARCH_DELAY_MS)
            val found = attempt { recipes.ingredients(text.trim()) }.getOrDefault(emptyList())
            _state.update { if (it.search != text) it else it.copy(found = found.take(OFFERED), searched = true) }
        }
    }

    /** Puts [ingredient] on a line of its own after the others; the search is emptied. */
    fun add(ingredient: LineIngredient) {
        searching?.cancel()
        edit { it.withLine(ingredient) }
        _state.update { it.copy(search = "", found = emptyList(), searched = false) }
    }

    /**
     * Saves the recipe: a new one, or a new version of the one edited. A field that holds no number
     * where one is needed is marked and nothing is sent; a field the API refuses is marked, and what
     * the fields cannot say is said beside the button.
     */
    fun save() {
        val current = _state.value
        // A recipe edited that could not be read has no form to save over it.
        if (current.saving || current.loading || current.loadError != null) return
        val sent = current.form
        val recipe = sent.recipe()
        _state.update { it.copy(showProblems = true, error = null, yours = null) }
        if (recipe == null) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            attempt { if (recipeId == null) recipes.write(recipe) else recipes.edit(recipeId, recipe) }
                .onSuccess { saved -> _state.update { it.copy(saving = false, saved = saved.id) } }
                .onFailure { e ->
                    val bounds = (e as? ApiException)?.fieldRefusals.orEmpty()
                    val problems = recipes.problemsOf(e)
                    _state.update {
                        val refusals = when {
                            bounds.isNotEmpty() -> RecipeRefusals.ofBounds(bounds, sent)
                            problems != null -> RecipeRefusals.ofRules(problems, sent)
                            else -> RecipeRefusals()
                        }.after(sent, it.form)
                        it.copy(
                            saving = false,
                            saveRefusals = refusals,
                            // What the fields cannot say is said beside the button.
                            error = e.takeIf { !refusals.marked || refusals.elsewhere.isNotEmpty() },
                            yours = recipes.alreadyYours(e),
                        )
                    }
                }
        }
    }

    /**
     * Asks what a serving holds once typing pauses, whenever what it is asked with changes: an answer
     * to what has changed since is not shown. A refusal marks the fields it names.
     */
    @OptIn(FlowPreview::class)
    private fun previews() {
        viewModelScope.launch {
            state.map { it.form.previewAsk() }
                .distinctUntilChanged()
                .debounce(PREVIEW_DELAY_MS)
                .collectLatest { ask ->
                    if (ask == null) {
                        _state.update { it.copy(preview = null, previewFailed = null) }
                        return@collectLatest
                    }
                    val asked = _state.value.form
                    attempt { recipes.preview(ask.servings, ask.lines) }
                        .onSuccess { preview ->
                            _state.update { if (it.form.previewAsk() != ask) it else it.copy(preview = preview, previewFailed = null, previewRefusals = RecipeRefusals()) }
                        }
                        .onFailure { e ->
                            val refused = (e as? ApiException)?.fieldRefusals.orEmpty()
                            _state.update {
                                if (it.form.previewAsk() != ask) {
                                    it
                                } else {
                                    val refusals = RecipeRefusals.ofBounds(refused, asked).after(asked, it.form)
                                    it.copy(preview = null, previewFailed = e.takeIf { !refusals.marked }, previewRefusals = refusals)
                                }
                            }
                        }
                }
        }
    }

    companion object {
        /** How long typing pauses before a serving is asked about, as the web's form waits. */
        const val PREVIEW_DELAY_MS = 400L

        /** How many of the ingredients found are offered, as many as the web's form offers. */
        const val OFFERED = 12
    }
}
