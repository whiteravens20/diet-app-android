// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ShoppingListRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.ui.common.attempt
import java.io.IOException
import javax.inject.Inject

/**
 * Shopping lists as the web page has them: a profile, one of its plans, and the plan's lists, the
 * newest first. A list is made for the whole plan or for some of its days, and replaces the lists
 * that share a day with it; a list the plan's menu changed under is made again for its days; a list
 * is deleted. Reads are offline-first; a row ticked waits for the server (changes made offline are
 * not kept yet), so what changes a list is off while offline.
 */
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val users: UserRepository,
    private val plans: MealPlanRepository,
    private val lists: ShoppingListRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val profiles: List<Profile> = emptyList(),
        val selectedProfileId: String? = null,
        val plans: List<MealPlanSummary> = emptyList(),
        val selectedPlanId: String? = null,
        /** The lists of the chosen plan, the newest first. */
        val lists: List<ShoppingList> = emptyList(),
        /** The list chosen among them; the newest is shown where none is. */
        val selectedListId: String? = null,
        val listLoading: Boolean = false,
        val generating: Boolean = false,
        val deleting: Boolean = false,
        val offline: Boolean = false,
        /** Ids of items with an update in flight. */
        val updating: Set<String> = emptySet(),
        val error: Throwable? = null,
    ) {
        val plan: MealPlanSummary? get() = plans.firstOrNull { it.id == selectedPlanId }

        /** The list shown: the one chosen, or the newest. */
        val list: ShoppingList? get() = lists.firstOrNull { it.id == selectedListId } ?: lists.firstOrNull()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            attempt { users.profiles() }
                .onSuccess { profiles ->
                    _state.value = UiState(loading = false, profiles = profiles)
                    profiles.firstOrNull()?.let { selectProfile(it.id) }
                }
                .onFailure { _state.value = UiState(loading = false, error = it) }
        }
    }

    fun selectProfile(profileId: String) {
        _state.update {
            it.copy(
                selectedProfileId = profileId,
                plans = emptyList(),
                selectedPlanId = null,
                lists = emptyList(),
                selectedListId = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val cached = plans.cachedPlans(profileId)?.plans.orEmpty()
            applyPlans(profileId, cached)
            attempt { plans.plans(profileId) }
                .onSuccess { fresh -> applyPlans(profileId, fresh.plans) }
                .onFailure { e ->
                    if (e !is IOException) _state.update { it.copy(error = e) }
                    else if (cached.isEmpty()) _state.update { it.copy(offline = true) }
                }
        }
    }

    fun selectPlan(planId: String) {
        _state.update { it.copy(selectedPlanId = planId, lists = emptyList(), selectedListId = null, error = null) }
        viewModelScope.launch {
            _state.update { it.copy(listLoading = true) }
            val cached = lists.cachedForPlan(planId).newestFirst()
            if (cached.isNotEmpty()) {
                _state.update { if (it.selectedPlanId == planId) it.copy(lists = cached, listLoading = false) else it }
            }
            attempt { lists.refreshForPlan(planId) }
                .onSuccess { fresh ->
                    _state.update {
                        if (it.selectedPlanId == planId) it.copy(lists = fresh.newestFirst(), listLoading = false, offline = false) else it
                    }
                }
                .onFailure { e ->
                    _state.update {
                        if (it.selectedPlanId == planId) {
                            it.copy(listLoading = false, offline = e is IOException, error = e.takeIf { _ -> e !is IOException })
                        } else it
                    }
                }
        }
    }

    fun selectList(listId: String) = _state.update { it.copy(selectedListId = listId) }

    /**
     * Makes the list of the chosen plan, or of the days from [fromDate] to [toDate] of it
     * (yyyy-MM-dd): it stands in the place of the lists that share a day with it, and is shown.
     */
    fun generate(fromDate: String? = null, toDate: String? = null) {
        val current = _state.value
        val planId = current.selectedPlanId ?: return
        if (current.generating || current.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            attempt { lists.generate(planId, fromDate, toDate) }
                .onSuccess { made ->
                    _state.update {
                        if (it.selectedPlanId != planId) return@update it.copy(generating = false)
                        val others = it.lists.filter { list -> list.id != made.id && !list.meets(made.fromDate, made.toDate) }
                        it.copy(generating = false, lists = (others + made).newestFirst(), selectedListId = made.id)
                    }
                }
                .onFailure { e -> _state.update { it.copy(generating = false, error = e) } }
        }
    }

    /** Makes a list that the plan's menu changed under again for its days: what was obtained for it is counted for the new one. */
    fun update(list: ShoppingList) = generate(list.fromDate, list.toDate)

    fun delete(listId: String) {
        val current = _state.value
        if (current.generating || current.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            attempt { lists.delete(listId) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            deleting = false,
                            lists = it.lists.filter { list -> list.id != listId },
                            selectedListId = it.selectedListId.takeIf { id -> id != listId },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(deleting = false, error = e) } }
        }
    }

    fun setChecked(itemId: String, checked: Boolean) {
        val listId = _state.value.list?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(updating = it.updating + itemId, error = null) }
            attempt { lists.setChecked(listId, itemId, checked) }
                .onSuccess { updated -> _state.update { it.copy(lists = it.lists.map { list -> if (list.id == updated.id) updated else list }, updating = it.updating - itemId) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            updating = it.updating - itemId,
                            offline = e is IOException,
                            error = e.takeIf { _ -> e !is IOException },
                        )
                    }
                }
        }
    }

    private fun applyPlans(profileId: String, planList: List<MealPlanSummary>) {
        _state.update {
            if (it.selectedProfileId != profileId) return@update it
            it.copy(plans = planList)
        }
        // Auto-select the newest plan once, so the screen is useful immediately.
        val current = _state.value
        if (current.selectedProfileId == profileId && current.selectedPlanId == null) {
            planList.firstOrNull()?.let { selectPlan(it.id) }
        }
    }

    private fun List<ShoppingList>.newestFirst(): List<ShoppingList> = sortedByDescending { it.createdAt }
}
