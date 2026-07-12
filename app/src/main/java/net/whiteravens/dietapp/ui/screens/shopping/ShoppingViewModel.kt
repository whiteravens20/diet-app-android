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
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ShoppingList
import java.io.IOException
import javax.inject.Inject

/**
 * Shopping flow mirrors the web page: pick a profile → pick one of its plans
 * → the newest list for that plan (or generate one). Reads are offline-first;
 * item ticks are server-authoritative (the offline outbox is not built yet),
 * so edits are disabled while offline.
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
        val plans: List<MealPlan> = emptyList(),
        val selectedPlanId: String? = null,
        val list: ShoppingList? = null,
        val listLoading: Boolean = false,
        val generating: Boolean = false,
        val offline: Boolean = false,
        /** Ids of items with an update in flight. */
        val updating: Set<String> = emptySet(),
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
            runCatching { users.profiles() }
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
                list = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val cached = plans.cachedPlans(profileId)
            applyPlans(profileId, cached)
            runCatching { plans.refresh(profileId) }
                .onSuccess { fresh -> applyPlans(profileId, fresh) }
                .onFailure { e ->
                    if (e !is IOException) _state.update { it.copy(error = e) }
                    else if (cached.isEmpty()) _state.update { it.copy(offline = true) }
                }
        }
    }

    fun selectPlan(planId: String) {
        _state.update { it.copy(selectedPlanId = planId, list = null, error = null) }
        viewModelScope.launch {
            _state.update { it.copy(listLoading = true) }
            val cached = lists.cachedForPlan(planId).firstOrNull()
            if (cached != null) {
                _state.update { if (it.selectedPlanId == planId) it.copy(list = cached, listLoading = false) else it }
            }
            runCatching { lists.refreshForPlan(planId) }
                .onSuccess { fresh ->
                    _state.update {
                        if (it.selectedPlanId == planId) {
                            it.copy(list = fresh.firstOrNull(), listLoading = false, offline = false)
                        } else it
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

    fun generate() {
        val planId = _state.value.selectedPlanId ?: return
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            runCatching { lists.generate(planId) }
                .onSuccess { list -> _state.update { it.copy(generating = false, list = list) } }
                .onFailure { e -> _state.update { it.copy(generating = false, error = e) } }
        }
    }

    fun setChecked(itemId: String, checked: Boolean) {
        val listId = _state.value.list?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(updating = it.updating + itemId, error = null) }
            runCatching { lists.setChecked(listId, itemId, checked) }
                .onSuccess { updated -> _state.update { it.copy(list = updated, updating = it.updating - itemId) } }
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

    private fun applyPlans(profileId: String, planList: List<MealPlan>) {
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
}
