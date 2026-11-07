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
import net.whiteravens.dietapp.data.repository.HouseholdListRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.HouseholdList
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.RowChange
import net.whiteravens.dietapp.domain.ShoppingItem
import net.whiteravens.dietapp.ui.common.attempt
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

/**
 * The household lists of the shopping screen, as the web's section has them: the account's lists,
 * read from the copies kept and then from the server; one opened, its rows changed as a plan's list's
 * are, through the queue without a network; one made of a plan of each profile for some days, made
 * again with the same plans and days where it is out of date, and deleted. Making and deleting are
 * off while offline.
 */
@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val households: HouseholdListRepository,
    private val plans: MealPlanRepository,
    private val queue: ChangeQueue,
) : ViewModel() {

    /**
     * The form of a new household list: its days, the plans of each profile that have a day of them
     * (the one that begins last first; null while they are read), and the plan chosen of each, the
     * first offered until another is chosen, null for none.
     */
    data class Form(
        val from: LocalDate,
        val to: LocalDate,
        val profiles: List<Profile>,
        val plans: Map<String, List<MealPlanSummary>?> = emptyMap(),
        val chosen: Map<String, String?> = emptyMap(),
    ) {
        /** The plans of [profileId] that have a day of the form's days; null while they are read. */
        fun offered(profileId: String): List<MealPlanSummary>? = plans[profileId]?.filter { plan ->
            val first = LocalDate.parse(plan.startDate)
            !first.isAfter(to) && !first.plusDays(plan.durationDays - 1L).isBefore(from)
        }

        /** The plan chosen of [profileId] among those offered: the first until another is chosen, none where none is offered. */
        fun choiceOf(profileId: String): String? {
            val offered = offered(profileId).orEmpty()
            if (!chosen.containsKey(profileId)) return offered.firstOrNull()?.id
            return chosen[profileId]?.takeIf { picked -> offered.any { it.id == picked } }
        }

        /** The plans the household list would be made of, one of each profile. */
        val planIds: List<String> get() = profiles.mapNotNull { choiceOf(it.id) }
    }

    data class UiState(
        /** The account's household lists, the newest first. */
        val lists: List<HouseholdList> = emptyList(),
        /** The household list opened, if any. */
        val openedId: String? = null,
        /** The form of a new household list, while it is open. */
        val form: Form? = null,
        val making: Boolean = false,
        val deleting: Boolean = false,
        val offline: Boolean = false,
        /** Ids of rows with a change on its way. */
        val updating: Set<String> = emptySet(),
        /** The changes of household lists' rows that wait to be sent, the oldest first. */
        val waitingRows: List<QueuedChange.HouseholdRow> = emptyList(),
        val error: Throwable? = null,
        /** How many household lists were made or deleted here: the plans' own lists of their days changed with them. */
        val changed: Int = 0,
    ) {
        val opened: HouseholdList? get() = lists.firstOrNull { it.id == openedId }

        /** The list opened with the changes of its rows that wait to be sent over what was read. */
        val shown: HouseholdList? get() = opened?.withWaiting(waitingRows)

        /** The rows of the list opened with a change that waits to be sent. */
        val waitingItems: Set<String> get() = openedId?.let { id -> waitingRows.filter { it.householdId == id }.mapTo(mutableSetOf()) { it.itemId } }.orEmpty()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
        followWaiting()
    }

    /** The lists as kept, then as the server has them. */
    fun load() {
        viewModelScope.launch {
            val kept = attempt { households.cached() }.getOrDefault(emptyList())
            if (kept.isNotEmpty()) _state.update { it.copy(lists = kept) }
            attempt { households.refresh() }
                .onSuccess { read -> _state.update { it.copy(lists = read, offline = false, openedId = it.openedId?.takeIf { id -> read.any { list -> list.id == id } }) } }
                .onFailure { e -> _state.update { it.copy(offline = e is IOException, error = e.takeIf { _ -> e !is IOException }) } }
        }
    }

    /** Opens [id], or closes the list opened where it is that one. */
    fun open(id: String?) = _state.update { it.copy(openedId = id?.takeIf { opened -> opened != it.openedId }, error = null) }

    /** Opens [id] whatever is opened, as a plan's part asks. */
    fun show(id: String) = _state.update { it.copy(openedId = id, error = null) }

    /** Opens the form for [profiles], from today through the six days after it, and reads each profile's plans. */
    fun startForm(profiles: List<Profile>) {
        val today = LocalDate.now()
        _state.update { it.copy(form = Form(today, today.plusDays(6), profiles), error = null) }
        profiles.forEach { profile ->
            viewModelScope.launch {
                val read = attempt { plans.plans(profile.id).plans }.getOrDefault(emptyList())
                    .sortedByDescending { it.startDate }
                _state.update { it.copy(form = it.form?.let { form -> form.copy(plans = form.plans + (profile.id to read)) }) }
            }
        }
    }

    fun closeForm() = _state.update { it.copy(form = null) }

    /** The form's first day; a last day before it follows it. */
    fun from(day: LocalDate) = _state.update { state ->
        state.copy(form = state.form?.let { it.copy(from = day, to = if (it.to.isBefore(day)) day else it.to) })
    }

    /** The form's last day; a first day after it follows it. */
    fun to(day: LocalDate) = _state.update { state ->
        state.copy(form = state.form?.let { it.copy(to = day, from = if (it.from.isAfter(day)) day else it.from) })
    }

    /** Chooses [planId] of [profileId], or none. */
    fun choose(profileId: String, planId: String?) = _state.update { state ->
        state.copy(form = state.form?.let { it.copy(chosen = it.chosen + (profileId to planId)) })
    }

    /** Makes the household list the form holds; it is opened. */
    fun make() {
        val form = _state.value.form ?: return
        if (form.planIds.size < 2) return
        made(form.planIds, form.from.toString(), form.to.toString())
    }

    /** Makes [list] again with the same plans and days: it replaces it, and counts what was obtained for it. */
    fun update(list: HouseholdList) = made(list.parts.map { it.planId }, list.fromDate, list.toDate)

    private fun made(planIds: List<String>, from: String, to: String) {
        if (_state.value.making) return
        viewModelScope.launch {
            _state.update { it.copy(making = true, error = null) }
            attempt { households.make(planIds, from, to) }
                .onSuccess { made ->
                    val kept = attempt { households.cached() }.getOrDefault(emptyList())
                    _state.update { it.copy(lists = listOf(made) + kept.filter { other -> other.id != made.id }, openedId = made.id, form = null, making = false, changed = it.changed + 1) }
                }
                .onFailure { e -> _state.update { it.copy(making = false, offline = e is IOException, error = e) } }
        }
    }

    fun delete(list: HouseholdList) {
        if (_state.value.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            attempt { households.delete(list) }
                .onSuccess { _state.update { it.copy(lists = it.lists - list, openedId = null, deleting = false, changed = it.changed + 1) } }
                .onFailure { e -> _state.update { it.copy(deleting = false, offline = e is IOException, error = e) } }
        }
    }

    /** Changes a row of the list opened: at once on the screen, and sent or kept to be sent in its turn. */
    fun change(itemId: String, change: RowChange) {
        val id = _state.value.openedId ?: return
        if (change.isEmpty || itemId in _state.value.updating) return
        viewModelScope.launch {
            _state.update { it.copy(updating = it.updating + itemId, error = null) }
            attempt { households.change(id, itemId, change) }
                .onSuccess { submitted ->
                    _state.update {
                        when (submitted) {
                            is Submitted.Sent -> it.copy(lists = it.lists.map { list -> if (list.id == submitted.answer.id) submitted.answer else list }, updating = it.updating - itemId)
                            is Submitted.Kept -> it.copy(updating = it.updating - itemId, offline = it.offline || submitted.offline)
                        }
                    }
                }
                .onFailure { e -> _state.update { it.copy(updating = it.updating - itemId, error = e) } }
        }
    }

    /** Follows the changes of household rows that wait; once one is sent, the lists are read again as the server answered. */
    private fun followWaiting() {
        viewModelScope.launch {
            queue.waiting.map { waiting -> waiting.mapNotNull { it.change as? QueuedChange.HouseholdRow } }
                .distinctUntilChanged()
                .collect { rows ->
                    val sent = _state.value.waitingRows.size > rows.size
                    _state.update { it.copy(waitingRows = rows, offline = it.offline && !sent) }
                    if (sent) attempt { households.cached() }.getOrNull()?.let { kept -> _state.update { it.copy(lists = kept) } }
                }
        }
    }
}

/** The list with the changes of its rows that wait to be sent, each over what was read, in the order they were made. */
internal fun HouseholdList.withWaiting(rows: List<QueuedChange.HouseholdRow>): HouseholdList {
    val mine = rows.filter { it.householdId == id }
    if (mine.isEmpty()) return this
    return copy(
        groups = groups.map { group ->
            group.copy(items = group.items.map { item -> mine.filter { it.itemId == item.id }.fold(item) { row, change -> row.with(change) } })
        },
    )
}

/** The row with what [change] sets of it: everything bought cleared by a nought, as the server clears it. */
private fun ShoppingItem.with(change: QueuedChange.HouseholdRow): ShoppingItem = copy(
    checked = change.checked ?: checked,
    atHome = change.atHome ?: atHome,
    purchasedQuantity = when (val have = change.have) {
        null -> purchasedQuantity
        else -> have.takeIf { it > 0 }
    },
)
