// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

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
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.IngredientChoice
import net.whiteravens.dietapp.domain.Rebalanced
import net.whiteravens.dietapp.domain.Substitution
import net.whiteravens.dietapp.domain.SubstitutionPreview
import net.whiteravens.dietapp.domain.SuggestedSubstitute
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * One ingredient of a planned meal put in the place of another, as the web's window walks through
 * it: the line that leaves, the ingredient that comes (the catalogue's suggestion, one found by a
 * search, or a model's pick), what that does to the meal before anything is written, and the
 * substitution made, which gives the meal a recipe of its own.
 */
@HiltViewModel
class SubstituteViewModel @Inject constructor(
    private val plans: MealPlanRepository,
    private val catalogue: ProfileRepository,
    private val users: UserRepository,
) : ViewModel() {

    data class UiState(
        val planId: String = "",
        val mealId: String = "",
        /** What the catalogue's own rules name in the place of each ingredient of the meal, by its id. */
        val suggestions: Map<String, IngredientChoice> = emptyMap(),
        /** Whether the account has a model to ask; null until that is known. */
        val aiOn: Boolean? = null,
        /** The ingredient that leaves. */
        val fromId: String? = null,
        val search: String = "",
        val found: List<IngredientChoice> = emptyList(),
        /** What a model picked for the line that leaves, with its note, or why the engine did. */
        val suggested: SuggestedSubstitute? = null,
        val suggesting: Boolean = false,
        /** The ingredient that comes in its place. */
        val to: IngredientChoice? = null,
        val preview: SubstitutionPreview? = null,
        val previewing: Boolean = false,
        /** Made although it moves what a serving holds by more than a tenth. */
        val acceptChange: Boolean = false,
        /** A model rewords the recipe's text around the substitute. */
        val rewriteWithAi: Boolean = false,
        val applying: Boolean = false,
        val error: Throwable? = null,
        /** The plan the substitution left: the window is done. */
        val done: Rebalanced? = null,
    ) {
        /** What is offered in the place of the line: what was suggested first, then what the search found, never the line itself. */
        val candidates: List<IngredientChoice>
            get() {
                val picks = listOfNotNull(suggestions[fromId], suggested?.ingredient).filter { it.id != fromId }.distinctBy { it.id }
                return picks + found.filter { found -> found.id != fromId && picks.none { it.id == found.id } }.take(MAX_FOUND)
            }

        /** The substitution can be made: nothing stands in its way, and a change of a serving's figures is taken where there is one. */
        val canApply: Boolean
            get() = preview?.valid == true && (preview.moved.isEmpty() || acceptChange) && !applying && to != null
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searching: Job? = null
    private var previewing: Job? = null

    /** Begins anew for [mealId] of [planId]: what the catalogue suggests, and whether a model can be asked. */
    fun open(planId: String, mealId: String) {
        searching?.cancel()
        previewing?.cancel()
        _state.value = UiState(planId = planId, mealId = mealId)
        viewModelScope.launch {
            val suggestions = attempt { plans.substitutes(planId, mealId) }.getOrDefault(emptyMap())
            val aiOn = attempt { users.me() }.getOrNull()?.let { it.aiMode != "none" }
            _state.update { if (it.mealId == mealId) it.copy(suggestions = suggestions, aiOn = aiOn) else it }
        }
    }

    /** The line that leaves: what was chosen for another line goes. */
    fun pickLine(ingredientId: String) {
        searching?.cancel()
        previewing?.cancel()
        _state.update {
            it.copy(fromId = ingredientId, search = "", found = emptyList(), suggested = null, to = null, preview = null, acceptChange = false, error = null)
        }
    }

    /** Looks for ingredients once [text] has two letters, when typing pauses. */
    fun search(text: String) {
        _state.update { it.copy(search = text) }
        searching?.cancel()
        if (text.trim().length < MIN_SEARCH) {
            _state.update { it.copy(found = emptyList()) }
            return
        }
        searching = viewModelScope.launch {
            delay(SEARCH_PAUSE_MILLIS)
            attempt { catalogue.ingredients(text.trim()) }
                .onSuccess { found -> _state.update { if (it.search == text) it.copy(found = found) else it } }
                .onFailure { e -> _state.update { it.copy(error = e) } }
        }
    }

    /** Asks a model which ingredient of the engine's pool to take for the line; the pick is chosen at once. */
    fun suggest() {
        val current = _state.value
        val fromId = current.fromId ?: return
        if (current.suggesting || current.aiOn != true) return
        viewModelScope.launch {
            _state.update { it.copy(suggesting = true, suggested = null, error = null) }
            attempt { plans.suggestSubstitute(current.planId, current.mealId, fromId) }
                .onSuccess { suggested ->
                    _state.update { if (it.fromId == fromId) it.copy(suggesting = false, suggested = suggested) else it.copy(suggesting = false) }
                    if (_state.value.fromId == fromId) pickSubstitute(suggested.ingredient)
                }
                .onFailure { e -> _state.update { it.copy(suggesting = false, error = e) } }
        }
    }

    /** The ingredient that comes in the place of the line: what that does to the meal is asked for at once. */
    fun pickSubstitute(ingredient: IngredientChoice) {
        val current = _state.value
        val fromId = current.fromId ?: return
        previewing?.cancel()
        _state.update { it.copy(to = ingredient, preview = null, previewing = true, acceptChange = false, error = null) }
        previewing = viewModelScope.launch {
            attempt { plans.previewSubstitution(Substitution(current.planId, current.mealId, fromId, ingredient.id)) }
                .onSuccess { preview -> _state.update { if (it.to?.id == ingredient.id) it.copy(preview = preview, previewing = false) else it } }
                .onFailure { e -> _state.update { if (it.to?.id == ingredient.id) it.copy(previewing = false, error = e) else it } }
        }
    }

    fun setAcceptChange(on: Boolean) {
        _state.update { it.copy(acceptChange = on) }
    }

    fun setRewriteWithAi(on: Boolean) {
        _state.update { it.copy(rewriteWithAi = on) }
    }

    fun apply() {
        val current = _state.value
        val fromId = current.fromId ?: return
        val to = current.to ?: return
        if (!current.canApply) return
        // A model's note goes with the substitution it was a pick for, and with no other.
        val pick = current.suggested?.takeIf { it.ingredient.id == to.id }?.pick
        val substitution = Substitution(
            planId = current.planId,
            mealId = current.mealId,
            fromIngredientId = fromId,
            toIngredientId = to.id,
            rewriteWithAi = current.aiOn == true && current.rewriteWithAi,
            acceptChange = current.acceptChange,
            pick = pick,
        )
        viewModelScope.launch {
            _state.update { it.copy(applying = true, error = null) }
            attempt { plans.substitute(substitution) }
                .onSuccess { done -> _state.update { it.copy(applying = false, done = done) } }
                .onFailure { e -> _state.update { it.copy(applying = false, error = e) } }
        }
    }

    private companion object {
        const val MIN_SEARCH = 2
        const val SEARCH_PAUSE_MILLIS = 300L

        /** As many found ingredients as the web's window lists. */
        const val MAX_FOUND = 20
    }
}
