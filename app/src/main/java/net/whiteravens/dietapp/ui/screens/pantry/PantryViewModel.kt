// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.PantryRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.data.sync.Waiting
import net.whiteravens.dietapp.domain.PantryChange
import net.whiteravens.dietapp.domain.PantryRow
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.StockableIngredient
import net.whiteravens.dietapp.ui.common.ReadingAnew
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import java.io.IOException
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
) : ViewModel() {

    /** What could not be done: something put in, a row changed, a row taken out. */
    enum class Failure { ADD, CHANGE, REMOVE }

    /** What is being put into the pantry: the ingredient looked for and picked, how much of it in which unit, and until when. */
    data class AddForm(
        val search: String = "",
        val found: List<StockableIngredient> = emptyList(),
        /** Whether the catalogue has answered the words as they are typed now. */
        val searched: Boolean = false,
        val picked: StockableIngredient? = null,
        val quantity: String = "",
        val unit: String = "g",
        val bestBefore: LocalDate? = null,
    ) {
        /** The amount typed, where it is more than nothing. */
        val amount: Double? get() = amountOf(quantity)?.takeIf { it > 0 }
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
        /** Whether something is on its way into the pantry. */
        val adding: Boolean = false,
        /** The row whose change, or whose taking out, is on its way. */
        val changing: String? = null,
        /** What could not be done, and why. */
        val failed: Pair<Failure, Throwable>? = null,
        /** The changes of the pantries that wait to be sent, in the order they were made. */
        val waiting: List<Waiting> = emptyList(),
    ) {
        val busy: Boolean get() = adding || changing != null

        /** The rows shown: as read, with the changes of the profile's pantry that wait to be sent over them. */
        val shownRows: List<PantryRow> get() = rows.withWaiting(profileId, waiting.map(Waiting::change))

        /** The rows with a change that waits to be sent: something put in, under its key, or a row changed. */
        val waitingRows: Set<String> get() = waiting.mapNotNullTo(mutableSetOf()) { waiting ->
            when (val change = waiting.change) {
                is QueuedChange.PantryAdded -> change.key.takeIf { change.profileId == profileId }
                is QueuedChange.PantryChanged -> change.id.takeIf { change.profileId == profileId }
                else -> null
            }
        }

        /** What is put in and waits to be sent, by its key: the change in the queue that takes it out again. */
        val waitingAdditions: Map<String, Long> get() =
            waiting.mapNotNull { waiting -> (waiting.change as? QueuedChange.PantryAdded)?.let { it.key to waiting.id } }.toMap()

        /** The rows under their categories, each category where its first row comes, as the web's page groups them. */
        val groups: List<Pair<String, List<PantryRow>>> get() = shownRows.groupBy { it.category }.toList()

        /** The ingredients found that the pantry holds none of yet, as many as the web's list offers. */
        val offered: List<StockableIngredient> get() = form.found.filter { found -> shownRows.none { it.ingredientId == found.id } }.take(OFFERED)

        /** Whether what the form holds can be put in. */
        val canAdd: Boolean get() = form.picked != null && form.amount != null && profileId != null && !busy
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

    /** Puts what the form holds into the pantry, and empties the form; a refusal leaves it as it is, to be put right. */
    fun add() {
        val current = _state.value
        if (!current.canAdd) return
        val profileId = current.profileId ?: return
        val form = current.form
        val ingredient = form.picked ?: return
        val amount = form.amount ?: return
        _state.update { it.copy(adding = true, failed = null) }
        viewModelScope.launch {
            attempt { pantry.add(profileId, ingredient, amount, form.unit, form.bestBefore) }
                .onSuccess { submitted ->
                    _state.update {
                        when (submitted) {
                            is Submitted.Sent -> it.copy(adding = false, rows = it.rows.withRow(submitted.answer), form = AddForm())
                            // Kept to be sent: shown among the rows as one of the changes waiting.
                            is Submitted.Kept -> it.copy(adding = false, form = AddForm(), offline = it.offline || submitted.offline)
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

    /**
     * Takes [row] out of the pantry; the pantry is read again either way, as a row taken out elsewhere
     * is gone too. Something put in that waits to be sent is taken out of the queue instead, unless it
     * was sent meanwhile: then the pantry is read again, with it in.
     */
    fun remove(row: PantryRow) {
        val profileId = _state.value.profileId ?: return
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
            )
            is QueuedChange.PantryChanged -> rows.map { row ->
                if (row.id != change.id) row
                else row.copy(shown = change.quantity ?: row.shown, bestBefore = if (change.newDay) change.bestBefore?.let(LocalDate::parse) else row.bestBefore)
            }
            is QueuedChange.PantryRemoved -> rows.filterNot { it.id == change.id }
            else -> rows
        }
    }
