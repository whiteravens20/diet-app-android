// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.WaterRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.data.sync.Waiting
import net.whiteravens.dietapp.domain.MAX_WATER_ENTRY_ML
import net.whiteravens.dietapp.domain.WaterDay
import net.whiteravens.dietapp.domain.WaterEntry
import net.whiteravens.dietapp.ui.common.attempt
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * The water of a profile's day, as its card shows it on the dashboard and in the diary: the day read,
 * an amount added at a press or typed, the last entry removed. A change goes through the queue of
 * changes, and without a network waits there: the card shows the day as read with what waits over
 * it, and reads it again once a change of it is sent.
 */
@HiltViewModel
class WaterViewModel @Inject constructor(
    private val water: WaterRepository,
    private val queue: ChangeQueue,
) : ViewModel() {

    /** What went wrong on the card. */
    sealed interface Problem {
        /** What was typed is no amount the API takes: a whole number of millilitres, 1 to 5000. */
        data object NotAnAmount : Problem

        /** The water could not be saved. */
        data class AddFailed(val error: Throwable) : Problem

        /** The last entry could not be removed. */
        data class RemoveFailed(val error: Throwable) : Problem
    }

    data class UiState(
        val profileId: String = "",
        val day: LocalDate? = null,
        /** The day as the API answered it last. */
        val read: WaterDay? = null,
        val loading: Boolean = true,
        /** Why the day could not be read, where it was never read. */
        val error: Throwable? = null,
        /** The amount of its own being typed. */
        val typed: String = "",
        /** An addition or a removal is on its way. */
        val busy: Boolean = false,
        val problem: Problem? = null,
        /** The additions and removals of the day that wait to be sent, in the order they were made. */
        val waiting: List<Waiting> = emptyList(),
    ) {
        /** The day as shown: as read, with what waits to be sent over it. */
        val shown: WaterDay? get() = read?.let { withWaiting(it, waiting) }

        /**
         * What waits to be added where the day was never read, as the day would hold it alone: its
         * sum and its last entry, nothing of the profile's target or of the days before.
         */
        val waitingOnly: WaterDay?
            get() = if (read != null || day == null || waiting.none { it.change is QueuedChange.WaterAdded }) null
            else withWaiting(WaterDay(day, 0, null, 0, null, emptyList()), waiting)

        /** The entry the card would remove: the last one shown, or the last that waits where the day was never read. */
        val last: WaterEntry? get() = (shown ?: waitingOnly)?.last

        /** The last entry is an addition that waits to be sent. */
        val lastWaits: Boolean get() = last?.let { last -> waiting.any { (it.change as? QueuedChange.WaterAdded)?.key == last.id } } == true

        /** Water can be added: the first reading is over, read or not, and nothing is on its way. */
        val ready: Boolean get() = !loading && !busy
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var following: Job? = null

    /** The card of [profileId]'s [day]: read, and its changes that wait followed. */
    fun open(profileId: String, day: LocalDate) {
        if (_state.value.profileId == profileId && _state.value.day == day) return
        _state.value = UiState(profileId = profileId, day = day)
        read()
        following?.cancel()
        following = viewModelScope.launch {
            queue.waiting.map { all -> all.filter { it.change.isOf(profileId, day) } }
                .distinctUntilChanged()
                .collect { waiting ->
                    val sent = _state.value.waiting.any { before -> waiting.none { it.id == before.id } }
                    _state.update { it.copy(waiting = waiting) }
                    // Sent: the server has it, and the day is read as it now is.
                    if (sent) read()
                }
        }
    }

    /** Reads the day again; what is shown stays where no answer comes. */
    fun read() {
        val (profileId, day) = _state.value.let { it.profileId to (it.day ?: return) }
        viewModelScope.launch {
            attempt { water.day(profileId, day) }
                .onSuccess { read -> _state.update { if (it.profileId == profileId && it.day == day) it.copy(read = read, loading = false, error = null) else it } }
                .onFailure { error -> _state.update { if (it.profileId == profileId && it.day == day) it.copy(loading = false, error = error.takeIf { _ -> it.read == null }) else it } }
        }
    }

    fun type(text: String) = _state.update { it.copy(typed = text.filter(Char::isDigit).take(4), problem = null) }

    /** Adds what was typed, where it is an amount the API takes. */
    fun addTyped() {
        val ml = _state.value.typed.toIntOrNull()
        if (ml == null || ml < 1 || ml > MAX_WATER_ENTRY_ML) {
            _state.update { it.copy(problem = Problem.NotAnAmount) }
            return
        }
        add(ml, typed = true)
    }

    /** Adds [ml] to the day. */
    fun add(ml: Int, typed: Boolean = false) {
        val (profileId, day) = _state.value.let { it.profileId to (it.day ?: return) }
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            attempt { water.add(profileId, day, ml) }
                .onSuccess { submitted ->
                    _state.update { state ->
                        state.copy(
                            busy = false,
                            typed = if (typed) "" else state.typed,
                            read = (submitted as? Submitted.Sent)?.answer ?: state.read,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(busy = false, problem = Problem.AddFailed(error)) } }
        }
    }

    /** Removes the day's last entry: one that waits to be sent leaves the queue unsent. */
    fun removeLast() {
        val state = _state.value
        val day = state.day ?: return
        val last = state.last ?: return
        if (state.busy) return
        _state.update { it.copy(busy = true, problem = null) }
        val waiting = state.waiting.firstOrNull { (it.change as? QueuedChange.WaterAdded)?.key == last.id }
        viewModelScope.launch {
            if (waiting != null) {
                // Taken back before it was sent; sent meanwhile, it is the server's to remove after all.
                if (!queue.withdraw(waiting.id)) read()
                _state.update { it.copy(busy = false) }
                return@launch
            }
            attempt { water.delete(state.profileId, day, last) }
                .onSuccess { submitted ->
                    _state.update { it.copy(busy = false, read = (submitted as? Submitted.Sent)?.answer ?: it.read) }
                }
                .onFailure { error -> _state.update { it.copy(busy = false, problem = Problem.RemoveFailed(error)) } }
        }
    }
}

/** Whether a change is one of water of [profileId]'s [day]. */
private fun QueuedChange.isOf(profileId: String, day: LocalDate): Boolean = when (this) {
    is QueuedChange.WaterAdded -> this.profileId == profileId && this.day == day.toString()
    is QueuedChange.WaterDeleted -> this.profileId == profileId && this.day == day.toString()
    else -> false
}

/**
 * [day] with the additions and removals that wait to be sent over it: their amounts in its sum, its
 * entries and its last of the seven days; an addition is the last entry, named by its key, at the
 * moment it was made. A removal of the last entry leaves none to name until the server says the next.
 */
internal fun withWaiting(day: WaterDay, waiting: List<Waiting>): WaterDay = waiting.fold(day) { shown, (_, change, madeAt) ->
    when (change) {
        is QueuedChange.WaterAdded -> shown.copy(
            ml = shown.ml + change.ml,
            entries = shown.entries + 1,
            last = WaterEntry(change.key, change.ml, Instant.ofEpochMilli(madeAt)),
        )
        is QueuedChange.WaterDeleted -> shown.copy(
            ml = (shown.ml - change.ml).coerceAtLeast(0),
            entries = (shown.entries - 1).coerceAtLeast(0),
            last = shown.last?.takeUnless { it.id == change.id },
        )
        else -> shown
    }
}.let { shown -> shown.copy(days = shown.days.map { if (it.day == shown.day) it.copy(ml = shown.ml) else it }) }
