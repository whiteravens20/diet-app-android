// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.ShoppingListRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.AiModes
import net.whiteravens.dietapp.domain.ListProposals
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.domain.ShoppingList
import net.whiteravens.dietapp.ui.common.ReadingAnew
import net.whiteravens.dietapp.ui.common.attempt
import java.io.IOException
import javax.inject.Inject

/**
 * Shopping lists as the web page has them: a profile, one of its plans, and the plan's lists, the
 * newest first. A list is made for the whole plan or for some of its days, and replaces the lists
 * that share a day with it; a list the plan's menu changed under is made again for its days; a list
 * is deleted. What a list could do without is asked of it, weighed by a model on request. Reads are
 * offline-first. A row's change is made without a network as well: it waits to be sent, shown over
 * the list as read, and the list is read again from the copy kept as the server answered once the
 * change is sent; making, changing or deleting a list is off while offline.
 */
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val users: UserRepository,
    private val plans: MealPlanRepository,
    private val lists: ShoppingListRepository,
    private val queue: ChangeQueue,
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
        /** A read asked for by pulling the screen down is under way. */
        val pulled: Boolean = false,
        /** When the lists shown were read: from the server, or into the copies shown before its answer. */
        val readAt: Long? = null,
        /** Ids of items with an update in flight. */
        val updating: Set<String> = emptySet(),
        /** The changes of rows that wait to be sent, the oldest first. */
        val waitingRows: List<QueuedChange.ShoppingRow> = emptyList(),
        /** What a list could do without, as it was asked of the list (its id and when it was made). */
        val proposals: Pair<String, ListProposals>? = null,
        val askingProposals: Boolean = false,
        /** Whether the account has a model to ask; null until that is known. */
        val aiOn: Boolean? = null,
        /** Why the profiles, the plans or the lists the screen shows could not be read: read again on asking. */
        val readError: Throwable? = null,
        /** Why a list could not be made, deleted, changed or asked what it could do without. */
        val error: Throwable? = null,
    ) {
        val plan: MealPlanSummary? get() = plans.firstOrNull { it.id == selectedPlanId }

        /** The list shown: the one chosen, or the newest. */
        val list: ShoppingList? get() = lists.firstOrNull { it.id == selectedListId } ?: lists.firstOrNull()

        /** The list shown with the changes of its rows that wait to be sent over what was read. */
        val shown: ShoppingList? get() = list?.withWaiting(waitingRows)

        /** The rows of the list shown with a change that waits to be sent. */
        val waitingItems: Set<String> get() = list?.let { shown -> waitingRows.filter { it.listId == shown.id }.mapTo(mutableSetOf()) { it.itemId } }.orEmpty()

        /** What the list shown could do without, where it was asked of that list as it is: another list, or the list made anew, begins without. */
        val shownProposals: ListProposals? get() = proposals?.takeIf { (asked, _) -> list?.let(::keyOf) == asked }?.second
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val again = ReadingAnew()

    init {
        load()
        followAiMode()
        followWaiting()
    }

    /**
     * Follows the changes of rows that wait to be sent. Once one is sent, the server has answered:
     * the screen is no longer offline, and the list shown is read again as the server answered it.
     */
    private fun followWaiting() {
        viewModelScope.launch {
            queue.waiting.map { waiting -> waiting.mapNotNull { it.change as? QueuedChange.ShoppingRow } }
                .distinctUntilChanged()
                .collect { rows ->
                    val sent = _state.value.waitingRows.size > rows.size
                    _state.update { it.copy(waitingRows = rows, offline = it.offline && !sent) }
                    if (sent) readSent()
                }
        }
    }

    /** The list shown as the copy kept of it now holds it: as the server answered the changes sent. */
    private fun readSent() {
        val id = _state.value.list?.id ?: return
        viewModelScope.launch {
            val kept = attempt { lists.cached(id) }.getOrNull() ?: return@launch
            _state.update { it.copy(lists = it.lists.map { list -> if (list.id == kept.id) kept else list }) }
        }
    }

    fun load() {
        again.begun()
        viewModelScope.launch {
            _state.update { UiState(loading = true, aiOn = it.aiOn) }
            // Without a network, those kept on the phone: their plans' lists open from their copies.
            attempt { users.profilesOrKept() }
                .onSuccess { profiles ->
                    _state.update { UiState(loading = false, profiles = profiles, aiOn = it.aiOn) }
                    profiles.firstOrNull()?.let { selectProfile(it.id) }
                }
                .onFailure { e -> _state.update { UiState(loading = false, readError = e, aiOn = it.aiOn) } }
        }
        viewModelScope.launch {
            val aiOn = attempt { users.me() }.getOrNull()?.let { it.aiMode != AiModes.NONE }
            _state.update { it.copy(aiOn = aiOn) }
        }
    }

    /**
     * Reads the profile's plans and the chosen plan's lists again when the screen is shown again, from a
     * plan or a list made, changed or deleted elsewhere; on coming back to the front only as often as
     * [ReadingAnew] lets it.
     */
    fun shownAgain(cameToTheFront: Boolean) {
        if (again.due(cameToTheFront)) readAnew()
    }

    /** Reads again what could not be read: the profiles where they were not, else the profile's plans and the chosen plan's lists. */
    fun retry() = readAnew()

    /** Reads the profile's plans and the chosen plan's lists again at once, the screen pulled down. */
    fun pull() {
        if (_state.value.loading || _state.value.pulled) return
        _state.update { it.copy(pulled = true) }
        readAnew()
    }

    /**
     * Reads what the screen shows again, keeping it until the answers are there. A plan chosen that the
     * profile no longer has gives way to its newest; a list changed while it was read keeps the change.
     */
    private fun readAnew() {
        val current = _state.value
        if (current.loading) return _state.update { it.copy(pulled = false) }
        val profileId = current.selectedProfileId ?: return load().also { _state.update { it.copy(pulled = false) } }
        again.begun()
        viewModelScope.launch {
            attempt { plans.plans(profileId) }.onSuccess { fresh ->
                // The plans could be read: a failure said before goes, and the lists below say their own.
                _state.update { if (it.selectedProfileId == profileId) it.copy(readError = null) else it }
                applyPlans(profileId, fresh.plans)
                val chosen = _state.value.selectedPlanId
                if (_state.value.selectedProfileId == profileId && chosen != null && fresh.plans.none { it.id == chosen }) {
                    _state.update { it.copy(pulled = false) }
                    fresh.plans.firstOrNull()?.let { selectPlan(it.id) }
                        ?: _state.update { it.copy(selectedPlanId = null, lists = emptyList(), selectedListId = null) }
                    return@launch
                }
            }
            val planId = _state.value.selectedPlanId ?: return@launch _state.update { it.copy(pulled = false) }
            val before = _state.value.lists
            attempt { lists.refreshForPlan(planId) }
                .onSuccess { fresh ->
                    _state.update {
                        if (it.selectedPlanId == planId && it.lists === before) {
                            it.copy(lists = fresh.newestFirst(), offline = false, pulled = false, readAt = System.currentTimeMillis(), readError = null)
                        } else {
                            it.copy(pulled = false)
                        }
                    }
                }
                .onFailure { e ->
                    val gone = e is ApiException && e.code == "PLAN_NOT_FOUND"
                    _state.update {
                        if (it.selectedPlanId != planId) return@update it.copy(pulled = false)
                        it.copy(
                            lists = if (gone) emptyList() else it.lists,
                            offline = e is IOException,
                            readError = e.takeIf { _ -> e !is IOException },
                            pulled = false,
                        )
                    }
                }
        }
    }

    /** AI turned on or off in the account's settings shows here as soon as it is. */
    private fun followAiMode() {
        viewModelScope.launch { users.aiModeChanges.collect { mode -> _state.update { it.copy(aiOn = mode != AiModes.NONE) } } }
    }

    fun selectProfile(profileId: String) {
        _state.update {
            it.copy(
                selectedProfileId = profileId,
                plans = emptyList(),
                selectedPlanId = null,
                lists = emptyList(),
                selectedListId = null,
                readError = null,
                error = null,
            )
        }
        viewModelScope.launch {
            val cached = plans.cachedPlans(profileId)?.plans.orEmpty()
            applyPlans(profileId, cached)
            attempt { plans.plans(profileId) }
                .onSuccess { fresh -> applyPlans(profileId, fresh.plans) }
                .onFailure { e ->
                    if (e !is IOException) _state.update { it.copy(readError = e) }
                    else if (cached.isEmpty()) _state.update { it.copy(offline = true) }
                }
        }
    }

    fun selectPlan(planId: String) {
        _state.update { it.copy(selectedPlanId = planId, lists = emptyList(), selectedListId = null, readError = null, error = null) }
        viewModelScope.launch {
            _state.update { it.copy(listLoading = true) }
            val cached = lists.cachedForPlan(planId).newestFirst()
            if (cached.isNotEmpty()) {
                _state.update { if (it.selectedPlanId == planId) it.copy(lists = cached, listLoading = false, readAt = cached.mapNotNull { list -> list.syncedAt }.maxOrNull()) else it }
            }
            attempt { lists.refreshForPlan(planId) }
                .onSuccess { fresh ->
                    _state.update {
                        if (it.selectedPlanId == planId) it.copy(lists = fresh.newestFirst(), listLoading = false, offline = false, readAt = System.currentTimeMillis()) else it
                    }
                }
                .onFailure { e ->
                    // A plan the API says is not there was deleted elsewhere, and its lists with it.
                    val gone = e is ApiException && e.code == "PLAN_NOT_FOUND"
                    _state.update {
                        if (it.selectedPlanId == planId) {
                            it.copy(
                                lists = if (gone) emptyList() else it.lists,
                                listLoading = false,
                                offline = e is IOException,
                                readError = e.takeIf { _ -> e !is IOException },
                            )
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

    /** Asks what the list shown could do without, put in order by a model where [weigh] and the account has one. */
    fun findProposals(weigh: Boolean = false) {
        val current = _state.value
        val list = current.list ?: return
        if (current.askingProposals) return
        viewModelScope.launch {
            _state.update { it.copy(askingProposals = true, error = null) }
            attempt { lists.proposals(list.id, weigh = weigh && current.aiOn == true) }
                .onSuccess { found -> _state.update { it.copy(askingProposals = false, proposals = keyOf(list) to found) } }
                .onFailure { e -> _state.update { it.copy(askingProposals = false, error = e) } }
        }
    }

    /** Changes a row of the list shown: its tick, what is at home, what was bought. */
    fun change(itemId: String, change: RowChange) {
        val listId = _state.value.list?.id ?: return
        if (change.isEmpty) return
        viewModelScope.launch {
            _state.update { it.copy(updating = it.updating + itemId, error = null) }
            attempt { lists.change(listId, itemId, change) }
                .onSuccess { submitted ->
                    _state.update {
                        when (submitted) {
                            is Submitted.Sent -> it.copy(lists = it.lists.map { list -> if (list.id == submitted.answer.id) submitted.answer else list }, updating = it.updating - itemId)
                            // Kept to be sent: shown over the list as one of the changes waiting.
                            is Submitted.Kept -> it.copy(updating = it.updating - itemId, offline = it.offline || submitted.offline)
                        }
                    }
                }
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

/** What tells a list apart from another, and from itself made anew. */
private fun keyOf(list: ShoppingList): String = "${list.id}@${list.createdAt}"

/** The list with the changes of its rows that wait to be sent, each over what was read, in the order they were made. */
internal fun ShoppingList.withWaiting(rows: List<QueuedChange.ShoppingRow>): ShoppingList {
    val mine = rows.filter { it.listId == id }
    if (mine.isEmpty()) return this
    return copy(
        groups = groups.map { group ->
            group.copy(items = group.items.map { item -> mine.filter { it.itemId == item.id }.fold(item) { row, change -> row.with(change) } })
        },
    )
}

/** The row with what [change] sets of it: everything bought cleared by a nought, as the server clears it. */
private fun ShoppingItem.with(change: QueuedChange.ShoppingRow): ShoppingItem = copy(
    checked = change.checked ?: checked,
    atHome = change.atHome ?: atHome,
    purchasedQuantity = when (val have = change.have) {
        null -> purchasedQuantity
        else -> have.takeIf { it > 0 }
    },
)
