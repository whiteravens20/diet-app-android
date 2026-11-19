// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.whiteravens.dietapp.data.repository.PantryRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.data.sync.Waiting
import net.whiteravens.dietapp.di.ApplicationScope
import net.whiteravens.dietapp.domain.PantryChange
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.StockableIngredient
import net.whiteravens.dietapp.domain.frozenUntil
import net.whiteravens.dietapp.domain.portionCount
import net.whiteravens.dietapp.domain.portionsOf
import net.whiteravens.dietapp.ui.common.GONE_WITHIN_MILLIS
import net.whiteravens.dietapp.ui.common.LaterRemoval
import net.whiteravens.dietapp.ui.common.ReadingAnew
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import java.io.IOException
import java.text.Collator
import java.time.LocalDate
import javax.inject.Inject

/**
 * A profile's pantry, as the web's page keeps it: its rows, each changed or taken out where it is
 * shown, and some of an ingredient of the catalogue put in, found by a part of its name. What the
 * API answers to a change is shown at once, and the pantry is read again after it, as a shopping
 * list may have changed it in the meantime. Without a network a change waits to be sent, shown over
 * the rows read; something put in that waits is taken out of the queue when it is taken out.
 */
@HiltViewModel
class PantryViewModel @Inject constructor(
    private val pantry: PantryRepository,
    private val users: UserRepository,
    private val queue: ChangeQueue,
    @ApplicationScope private val application: CoroutineScope,
) : ViewModel() {

    /** What could not be done: something put in, a row changed, a row taken out, a row put into the freezer. */
    enum class Failure { ADD, CHANGE, REMOVE, FREEZE }

    /** What went in last: [amount] of [unit] (a piece by its name) of [name], into the freezer in [portions] or fresh. */
    data class Added(val name: String, val amount: Double, val unit: String, val frozen: Boolean, val portions: Int)

    /**
     * What is being put into the pantry: the ingredient looked for and picked, how much of it in which unit, until when,
     * and whether into the freezer, in how many portions there.
     */
    data class AddForm(
        val search: String = "",
        val found: List<StockableIngredient> = emptyList(),
        /** Whether the catalogue has answered the words as they are typed now. */
        val searched: Boolean = false,
        val picked: StockableIngredient? = null,
        val quantity: String = "",
        val unit: String = "g",
        val bestBefore: LocalDate? = null,
        val frozen: Boolean = false,
        /** The day the freezer's box wrote itself, taken away with the box while nobody changed it. */
        val boxDay: LocalDate? = null,
        /** How many packages what goes into the freezer is split into, as typed. */
        val portions: String = "1",
    ) {
        /** The amount typed, where it is more than nothing. */
        val amount: Double? get() = amountOf(quantity)?.takeIf { it > 0 }

        /** How many packages it goes into the freezer in, where the number typed is one the freezer takes; one for what is fresh. */
        val portionCount: Int? get() = if (frozen) portionCount(portions) else 1

        /** What each package holds, where the amount goes into them in at least one of its unit each; null where it does not. */
        val parts: List<Double>? get() {
            val count = portionCount ?: return null
            return portionsOf(amount ?: return null, count)
        }
    }

    data class UiState(
        val loadingProfiles: Boolean = true,
        /** A read asked for by pulling the screen down is under way. */
        val pulled: Boolean = false,
        /** When what is shown was read, in the phone's milliseconds; null before the first answer. */
        val readAt: Long? = null,
        /** Reading it anew failed for want of a network: what is shown is as it was at [readAt]. */
        val offline: Boolean = false,
        val profiles: List<Profile> = emptyList(),
        val profilesError: Throwable? = null,
        /** The profile whose pantry is shown. */
        val profileId: String? = null,
        val loading: Boolean = false,
        val rows: List<PantryRow> = emptyList(),
        val error: Throwable? = null,
        val form: AddForm = AddForm(),
        /** Whether the window things are put in with is open: it stays open for the next until it is closed. */
        val addWindow: Boolean = false,
        /** What went in last while the window was open, said in it until the next goes. */
        val lastAdded: Added? = null,
        /** Whether something is on its way into the pantry. */
        val adding: Boolean = false,
        /** The row whose change, or whose taking out, is on its way. */
        val changing: String? = null,
        /** What could not be done, and why. */
        val failed: Pair<Failure, Throwable>? = null,
        /** The changes of the pantries that wait to be sent, in the order they were made. */
        val waiting: List<Waiting> = emptyList(),
        /** The fresh row whose window of freezing is open. */
        val freezing: PantryRow? = null,
        /** How many packages it goes into the freezer in, as typed in its window. */
        val freezePortions: String = "1",
        /** Why the row of the open window could not be put into the freezer. */
        val freezeFailure: Throwable? = null,
    ) {
        val busy: Boolean get() = adding || changing != null

        /** The rows shown: as read, with the changes of the profile's pantry that wait to be sent over them. */
        val shownRows: List<PantryRow> get() = rows.withWaiting(profileId, waiting.map(Waiting::change))

        /** The rows with a change that waits to be sent: something put in, under its key, or a row changed. */
        val waitingRows: Set<String> get() = waiting.mapNotNullTo(mutableSetOf()) { waiting ->
            when (val change = waiting.change) {
                is QueuedChange.PantryAdded -> change.key.takeIf { change.profileId == profileId }
                is QueuedChange.PantryChanged -> change.id.takeIf { change.profileId == profileId }
                is QueuedChange.PantryFrozen -> change.id.takeIf { change.profileId == profileId }
                else -> null
            }
        }

        /** What is put in and waits to be sent, by its key: the change in the queue that takes it out again. */
        val waitingAdditions: Map<String, Long> get() =
            waiting.mapNotNull { waiting -> (waiting.change as? QueuedChange.PantryAdded)?.let { it.key to waiting.id } }.toMap()

        /**
         * The rows under their categories, each category where its first row comes, as the web's page groups them, and
         * by name in each, a food's packages in the freezer right after its fresh row, the one going off first first.
         */
        val groups: List<Pair<String, List<PantryRow>>> get() {
            val byName = Collator.getInstance()
            return shownRows.groupBy { it.category }.toList().map { (category, rows) ->
                category to rows.sortedWith(
                    compareBy<PantryRow, String>(byName) { it.name }.thenBy { it.frozen }.thenBy(nullsLast()) { it.bestBefore },
                )
            }
        }

        /** The number each package in the freezer is told by, where its food has more than one: in the order they stand. */
        val packageNumbers: Map<String, Int> get() = groups.flatMap { (_, rows) ->
            rows.filter { it.frozen }.groupBy { it.ingredientId }.values.filter { it.size > 1 }
                .flatMap { packages -> packages.mapIndexed { index, row -> row.id to index + 1 } }
        }.toMap()

        /**
         * The ingredients found, as many as the web's list offers: those the pantry holds nothing fresh of yet, and any of
         * them to go into the freezer, as packages of their own.
         */
        val offered: List<StockableIngredient> get() =
            form.found.filter { found -> form.frozen || shownRows.none { it.ingredientId == found.id && !it.frozen } }.take(OFFERED)

        /** Whether what the form holds can be put in: into the freezer, in packages of at least one of its unit each. */
        val canAdd: Boolean get() = form.picked != null && form.amount != null && form.parts != null && profileId != null && !busy

        /** How many packages the row of the open window goes into, where the number typed is one the freezer takes. */
        val freezeCount: Int? get() = portionCount(freezePortions)

        /** What each package of the row of the open window holds; null where a part would hold less than one of its unit. */
        val freezeParts: List<Double>? get() {
            val row = freezing ?: return null
            return portionsOf(row.quantity, freezeCount ?: return null)
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var reading: Job? = null
    private var searching: Job? = null

    private val again = ReadingAnew()

    /** What was put in and taken out again while it waited: it leaves the queue unsent. */
    private val withdrawn = mutableSetOf<Long>()

    init {
        loadProfiles()
        followWaiting()
    }

    /**
     * Follows the changes of the pantries that wait to be sent. Once one is sent, the server has
     * answered: the screen is no longer offline, and the pantry is read again as the server has it.
     */
    private fun followWaiting() {
        viewModelScope.launch {
            queue.waiting.map { waiting -> waiting.filter { profileOf(it.change) != null } }
                .distinctUntilChanged()
                .collect { waiting ->
                    val sent = _state.value.waiting.any { before -> waiting.none { it.id == before.id } && before.id !in withdrawn }
                    withdrawn.retainAll { id -> waiting.any { it.id == id } }
                    _state.update { it.copy(waiting = waiting, offline = it.offline && !sent) }
                    if (sent) read(showLoading = false)
                }
        }
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _state.update { it.copy(loadingProfiles = true, profilesError = null) }
            attempt { users.profiles() }
                .onSuccess { found ->
                    _state.update { it.copy(loadingProfiles = false, profiles = found, profileId = it.profileId?.takeIf { id -> found.any { p -> p.id == id } } ?: found.firstOrNull()?.id) }
                    read()
                }
                .onFailure { e -> _state.update { it.copy(loadingProfiles = false, profilesError = e, pulled = false) } }
        }
    }

    /**
     * Reads the pantry again when the screen is shown again, keeping what is shown until the answer is
     * there; on coming back to the front only as often as [ReadingAnew] lets it.
     */
    fun shownAgain(cameToTheFront: Boolean) {
        if (again.due(cameToTheFront)) readAnew()
    }

    /** Reads the pantry again at once, the screen pulled down; the profiles too where they could not be read. */
    fun pull() {
        if (_state.value.pulled) return
        _state.update { it.copy(pulled = true) }
        readAnew()
    }

    private fun readAnew() {
        val current = _state.value
        when {
            current.profilesError != null -> loadProfiles()
            current.loadingProfiles || current.loading || current.busy || current.profileId == null -> _state.update { it.copy(pulled = false) }
            else -> read(showLoading = false)
        }
    }

    fun retry() = read()

    fun pickProfile(profileId: String) {
        if (_state.value.profileId == profileId || _state.value.busy) return
        _state.update { it.copy(profileId = profileId, rows = emptyList(), failed = null) }
        read()
    }

    /** Looks for what is typed in the catalogue once two letters are typed and typing pauses, as the profile's pickers do. */
    fun search(text: String) {
        _state.update { it.copy(form = it.form.copy(search = text, found = emptyList(), searched = false)) }
        searching?.cancel()
        if (text.trim().length < ProfilesViewModel.SEARCH_FROM) return
        searching = viewModelScope.launch {
            delay(ProfilesViewModel.SEARCH_DELAY_MS)
            val found = attempt { pantry.ingredients(text.trim()) }.getOrDefault(emptyList())
            _state.update { state ->
                if (state.form.search != text) state else state.copy(form = state.form.copy(found = found, searched = true))
            }
        }
    }

    /** Takes [ingredient] for the form, in the unit it is put in at first; the search is emptied. */
    fun pick(ingredient: StockableIngredient) {
        searching?.cancel()
        _state.update { it.copy(form = it.form.copy(search = "", found = emptyList(), searched = false, picked = ingredient, unit = ingredient.unit)) }
    }

    /** Lets another ingredient be looked for in the place of the one picked. */
    fun unpick() = _state.update { it.copy(form = it.form.copy(picked = null)) }

    /** Takes what is typed as the amount where it is a number not under nothing, or nothing yet. */
    fun typeQuantity(text: String) {
        if (text.isBlank() || amountOf(text) != null) _state.update { it.copy(form = it.form.copy(quantity = text)) }
    }

    fun chooseUnit(unit: String) = _state.update { it.copy(form = it.form.copy(unit = unit)) }

    fun chooseBestBefore(day: LocalDate?) = _state.update { it.copy(form = it.form.copy(bestBefore = day)) }

    /**
     * Into the freezer or not: what goes in is dated as the freezer keeps it from [today], unless a day is chosen
     * already; the day the box wrote goes with the box while nobody changed it.
     */
    fun freezeIt(on: Boolean, today: LocalDate) = _state.update { state ->
        val form = state.form
        state.copy(
            form = when {
                on && form.bestBefore == null -> frozenUntil(today).let { form.copy(frozen = true, bestBefore = it, boxDay = it) }
                on -> form.copy(frozen = true)
                form.boxDay != null && form.bestBefore == form.boxDay -> form.copy(frozen = false, bestBefore = null, boxDay = null)
                else -> form.copy(frozen = false, boxDay = null)
            },
        )
    }

    /** Takes what is typed as the number of portions where it is digits alone, or nothing yet. */
    fun typePortions(text: String) {
        if (text.all(Char::isDigit) && text.length <= 2) _state.update { it.copy(form = it.form.copy(portions = text)) }
    }

    /** Opens the window things are put in with, the form as it was left and nothing said yet. */
    fun openAdd() = _state.update { it.copy(addWindow = true, lastAdded = null, failed = it.failed?.takeUnless { (what, _) -> what == Failure.ADD }) }

    /** Closes the window: what it said of the last thing put in, or why that did not go in, goes with it. */
    fun closeAdd() = _state.update { it.copy(addWindow = false, lastAdded = null, failed = it.failed?.takeUnless { (what, _) -> what == Failure.ADD }) }

    /** Puts what the form holds into the pantry, and empties the form; a refusal leaves it as it is, to be put right. */
    fun add() {
        val current = _state.value
        if (!current.canAdd) return
        val profileId = current.profileId ?: return
        val form = current.form
        val ingredient = form.picked ?: return
        val amount = form.amount ?: return
        val portions = form.portionCount ?: return
        val added = Added(ingredient.name, amount, if (form.unit == "piece") ingredient.pieceName else form.unit, form.frozen, portions)
        _state.update { it.copy(adding = true, failed = null, lastAdded = null) }
        viewModelScope.launch {
            attempt { pantry.add(profileId, ingredient, amount, form.unit, form.bestBefore, form.frozen, portions) }
                .onSuccess { submitted ->
                    _state.update {
                        when (submitted) {
                            is Submitted.Sent -> it.copy(adding = false, rows = it.rows.withRow(submitted.answer), form = AddForm(), lastAdded = added)
                            // Kept to be sent: shown among the rows as one of the changes waiting.
                            is Submitted.Kept -> it.copy(adding = false, form = AddForm(), lastAdded = added, offline = it.offline || submitted.offline)
                        }
                    }
                    if (submitted is Submitted.Sent) read(showLoading = false)
                }
                .onFailure { e ->
                    _state.update { it.copy(adding = false, failed = Failure.ADD to e) }
                    read(showLoading = false)
                }
        }
    }

    /**
     * Sends what [change] says of [row]; the row is shown as the API then keeps it. Something put in
     * that waits to be sent is not changed until it is.
     */
    fun change(row: PantryRow, change: PantryChange) {
        val profileId = _state.value.profileId ?: return
        if (change.isEmpty || _state.value.busy || row.id in _state.value.waitingAdditions) return
        _state.update { it.copy(changing = row.id, failed = null) }
        viewModelScope.launch {
            attempt { pantry.change(profileId, row.id, change) }
                .onSuccess { submitted ->
                    _state.update {
                        when (submitted) {
                            is Submitted.Sent -> it.copy(changing = null, rows = it.rows.withRow(submitted.answer))
                            // Kept to be sent: shown over the row as one of the changes waiting.
                            is Submitted.Kept -> it.copy(changing = null, offline = it.offline || submitted.offline)
                        }
                    }
                    if (submitted is Submitted.Sent) read(showLoading = false)
                }
                .onFailure { e ->
                    _state.update { it.copy(changing = null, failed = Failure.CHANGE to e) }
                    read(showLoading = false)
                }
        }
    }

    /** A removal as it was made: of [row], from the pantry of [profileId], whichever profile is chosen when it is sent. */
    data class Removal(val profileId: String, val row: PantryRow)

    /** A row is cheap to put in again: it goes at once, and is taken out once the moments to take it back are over. */
    private val later = LaterRemoval<Removal>(viewModelScope, idOf = { it.row.id }) { removal ->
        // Once nothing else is being changed, and done before the next: a removal is never dropped for another change.
        _state.first { !it.busy }
        removeNow(removal)
        _state.first { !it.busy }
        // Out of sight until the pantry no longer shows it, from the server or from the changes waiting, or until a
        // failure shows it again.
        withTimeoutOrNull(GONE_WITHIN_MILLIS) { _state.first { s -> s.failed != null || s.shownRows.none { it.id == removal.row.id } } }
    }

    /** The row removed a moment ago, which can still be taken back. */
    val removedLater: StateFlow<Removal?> = later.waiting

    /** The rows out of sight: removed a moment ago, or sent and not answered yet. */
    val hidden: StateFlow<Set<Any>> = later.hidden

    /**
     * Takes [row] out; with [after], as the screen asks, it is first held that long to be taken back
     * ([keepRemoved]). The screen passes the moments; nothing else waits. Something put in that waits
     * to be sent leaves the queue at once: held, it could be sent meanwhile, and come back.
     */
    fun remove(row: PantryRow, after: Long = 0) {
        val removal = Removal(_state.value.profileId ?: return, row)
        if (row.id in _state.value.waitingAdditions) later.removeAtOnce(removal) else later.remove(removal, after)
    }

    /** Takes the removal back: the row stays, and nothing is sent. */
    fun keepRemoved() = later.undo()

    override fun onCleared() {
        // A removal still waiting is not taken back by leaving the screen: it is sent from the application's scope.
        val removal = later.take() ?: return
        application.launch { runCatching { pantry.remove(removal.profileId, removal.row.id) } }
    }

    /**
     * Takes the row of [removal] out of the pantry; the pantry is read again either way, as a row taken out
     * elsewhere is gone too. Something put in that waits to be sent is taken out of the queue instead, unless
     * it was sent meanwhile: then the pantry is read again, with it in.
     */
    private fun removeNow(removal: Removal) {
        val (profileId, row) = removal
        if (_state.value.busy) return
        _state.update { it.copy(changing = row.id, failed = null) }
        val waiting = _state.value.waitingAdditions[row.id]
        if (waiting != null) {
            withdrawn += waiting
            viewModelScope.launch {
                val taken = queue.withdraw(waiting)
                _state.update { it.copy(changing = null) }
                if (!taken) {
                    withdrawn -= waiting
                    read(showLoading = false)
                }
            }
            return
        }
        viewModelScope.launch {
            attempt { pantry.remove(profileId, row.id) }
                .onSuccess { submitted ->
                    _state.update {
                        when (submitted) {
                            is Submitted.Sent -> it.copy(changing = null, rows = it.rows.filterNot { kept -> kept.id == row.id })
                            // Kept to be sent: left out of the rows as one of the changes waiting.
                            is Submitted.Kept -> it.copy(changing = null, offline = it.offline || submitted.offline)
                        }
                    }
                    if (submitted is Submitted.Sent) read(showLoading = false)
                }
                .onFailure { e ->
                    _state.update { it.copy(changing = null, failed = Failure.REMOVE to e) }
                    read(showLoading = false)
                }
        }
    }

    /** Opens the window that puts the fresh [row] into the freezer, whole to begin with. Something put in that waits to be sent is not frozen until it is. */
    fun openFreeze(row: PantryRow) {
        val current = _state.value
        if (row.frozen || current.busy || row.id in current.waitingAdditions) return
        _state.update { it.copy(freezing = row, freezePortions = "1", freezeFailure = null) }
    }

    /** Takes what is typed in the window as its number of portions where it is digits alone, or nothing yet. */
    fun typeFreezePortions(text: String) {
        if (text.all(Char::isDigit) && text.length <= 2) _state.update { it.copy(freezePortions = text) }
    }

    /** Closes the window of freezing; a freezing on its way goes on. */
    fun closeFreeze() = _state.update { it.copy(freezing = null, freezeFailure = null) }

    /**
     * Puts the row of the open window into the freezer on [today], in the portions typed there: the pantry is shown as the
     * API answers it, the row gone and its packages in the freezer. A failure is said in the window while it is open.
     */
    fun freeze(today: LocalDate) {
        val current = _state.value
        val row = current.freezing ?: return
        val profileId = current.profileId ?: return
        val portions = current.freezeCount ?: return
        if (current.freezeParts == null || current.busy) return
        _state.update { it.copy(changing = row.id, freezeFailure = null, failed = null) }
        viewModelScope.launch {
            attempt { pantry.freeze(profileId, row.id, portions, today) }
                .onSuccess { submitted ->
                    _state.update {
                        when (submitted) {
                            is Submitted.Sent -> it.copy(changing = null, freezing = null, rows = submitted.answer)
                            // Kept to be sent: shown frozen as one of the changes waiting.
                            is Submitted.Kept -> it.copy(changing = null, freezing = null, offline = it.offline || submitted.offline)
                        }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        if (it.freezing?.id == row.id) it.copy(changing = null, freezeFailure = e)
                        else it.copy(changing = null, failed = Failure.FREEZE to e)
                    }
                    read(showLoading = false)
                }
        }
    }

    /**
     * Reads the profile's pantry. Where [showLoading] is false, what is shown stays until the answer is
     * there, and stays where none comes.
     */
    private fun read(showLoading: Boolean = true) {
        val profileId = _state.value.profileId ?: return _state.update { it.copy(pulled = false) }
        again.begun()
        reading?.cancel()
        reading = viewModelScope.launch {
            if (showLoading) _state.update { it.copy(loading = true, error = null) }
            val before = _state.value.rows
            attempt { pantry.rows(profileId) }
                .onSuccess { rows ->
                    // A row changed while this was read was answered after it: what is shown is newer.
                    _state.update { if (showLoading || it.rows === before) it.copy(loading = false, rows = rows, error = null, pulled = false, readAt = System.currentTimeMillis(), offline = false) else it.copy(loading = false, pulled = false) }
                }
                .onFailure { e ->
                    _state.update { if (showLoading || it.rows.isEmpty()) it.copy(loading = false, rows = emptyList(), error = e, pulled = false) else it.copy(loading = false, pulled = false, offline = e is IOException) }
                }
        }
    }

    /** These rows with [row] in the place of the one of its id, or after them where there is none. */
    private fun List<PantryRow>.withRow(row: PantryRow): List<PantryRow> =
        if (any { it.id == row.id }) map { if (it.id == row.id) row else it } else this + row

    companion object {
        /** How many of the ingredients found are offered, as many as the web's list offers. */
        const val OFFERED = 8

        /** An amount typed, with a decimal comma as well as a point; null for none, or for one under nothing. */
        fun amountOf(typed: String): Double? = typed.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

        /**
         * What a row's fields change of it: its amount where the number typed is not the one shown, so that
         * an amount left as shown does not write the rounded number over the exact one; its day where it is
         * another. Null where the field holds no amount, or nothing changes.
         */
        fun changeOf(row: PantryRow, typed: String, day: LocalDate?): PantryChange? {
            val amount = amountOf(typed) ?: return null
            val newDay = day != row.bestBefore
            return PantryChange(quantity = amount.takeIf { it != row.shown }, newDay = newDay, bestBefore = day.takeIf { newDay }).takeUnless { it.isEmpty }
        }
    }
}

/** The profile whose pantry a change is of; null for a change of anything else. */
private fun profileOf(change: QueuedChange): String? = when (change) {
    is QueuedChange.PantryAdded -> change.profileId
    is QueuedChange.PantryChanged -> change.profileId
    is QueuedChange.PantryRemoved -> change.profileId
    is QueuedChange.PantryFrozen -> change.profileId
    else -> null
}

/**
 * The rows of [profileId]'s pantry with its changes that wait to be sent over them, in the order they
 * were made: something put in after the rows, under its key; a row changed as the change says; a row
 * taken out left out.
 */
internal fun List<PantryRow>.withWaiting(profileId: String?, waiting: List<QueuedChange>): List<PantryRow> =
    waiting.filter { profileOf(it) == profileId }.fold(this) { rows, change ->
        when (change) {
            is QueuedChange.PantryAdded -> rows + PantryRow(
                id = change.key,
                ingredientId = change.ingredientId,
                name = change.name,
                category = change.category,
                shown = change.quantity,
                shownUnit = change.unit,
                bestBefore = change.bestBefore?.let(LocalDate::parse),
                frozen = change.frozen,
            )
            is QueuedChange.PantryChanged -> rows.map { row ->
                if (row.id != change.id) row
                else row.copy(
                    shown = change.quantity ?: row.shown,
                    // What a new amount weighs is the server's to say once the change reaches it.
                    shownGrams = if (change.quantity == null) row.shownGrams else null,
                    bestBefore = if (change.newDay) change.bestBefore?.let(LocalDate::parse) else row.bestBefore,
                )
            }
            is QueuedChange.PantryRemoved -> rows.filterNot { it.id == change.id }
            is QueuedChange.PantryFrozen -> rows.map { row ->
                if (row.id != change.id) row else row.copy(frozen = true, bestBefore = LocalDate.parse(change.bestBefore))
            }
            else -> rows
        }
    }
