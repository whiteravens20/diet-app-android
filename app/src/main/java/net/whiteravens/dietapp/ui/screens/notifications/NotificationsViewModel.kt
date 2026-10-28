// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.domain.AccountNotification
import net.whiteravens.dietapp.ui.common.ReadingAnew
import net.whiteravens.dietapp.ui.common.attempt
import java.io.IOException
import javax.inject.Inject

/**
 * The account's notifications that are not read yet, with the names of the profiles they are for:
 * one opened is marked read first, one put away is marked read, without a network as well, where
 * the reading waits to be sent; and all can be at once, with a network.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notifications: NotificationRepository,
    private val users: UserRepository,
    private val queue: ChangeQueue,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val items: List<AccountNotification> = emptyList(),
        /** The profiles' names by their ids, to say whose a notification is. */
        val names: Map<String, String> = emptyMap(),
        val error: Throwable? = null,
        /** The notification being marked read, [ALL] for every one; null while none is. */
        val marking: String? = null,
        /** A read asked for by pulling the screen down is under way. */
        val pulled: Boolean = false,
        /** When what is shown was read, in the phone's milliseconds; null before the first answer. */
        val readAt: Long? = null,
        /** Reading it anew failed for want of a network: what is shown is as it was at [readAt]. */
        val offline: Boolean = false,
        /** The notifications whose reading waits to be sent. */
        val waiting: Set<String> = emptySet(),
    ) {
        /** The notifications shown: those read from the server, less those whose reading waits to be sent. */
        val shownItems: List<AccountNotification> get() = items.filterNot { it.id in waiting }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val again = ReadingAnew()

    init {
        load()
        followWaiting()
    }

    /**
     * Follows the notifications whose reading waits to be sent. Once one is sent, the server has
     * answered: the screen is no longer offline, and the list is read again as the server has it.
     */
    private fun followWaiting() {
        viewModelScope.launch {
            queue.waiting.map { waiting -> waiting.mapNotNullTo(mutableSetOf()) { (it.change as? QueuedChange.NotificationRead)?.id } }
                .distinctUntilChanged()
                .collect { waiting ->
                    val sent = _state.value.waiting.any { it !in waiting }
                    _state.update { it.copy(waiting = waiting, offline = it.offline && !sent) }
                    if (sent) read(showLoading = false)
                }
        }
    }

    fun load() = read(showLoading = true)

    /**
     * Reads the notifications again when the screen is shown again, keeping what is shown until the
     * answer is there; on coming back to the front only as often as [ReadingAnew] lets it.
     */
    fun shownAgain(cameToTheFront: Boolean) {
        if (!_state.value.loading && again.due(cameToTheFront)) read(showLoading = false)
    }

    /** Reads the notifications again at once, the screen pulled down. */
    fun pull() {
        if (_state.value.loading || _state.value.pulled) return
        _state.update { it.copy(pulled = true) }
        read(showLoading = false)
    }

    private fun read(showLoading: Boolean) {
        again.begun()
        viewModelScope.launch {
            if (showLoading) _state.update { it.copy(loading = true, error = null) }
            val before = _state.value.items
            // A profile whose name cannot be read leaves its notifications without one, nothing more.
            val names = async { attempt { users.profiles() }.getOrDefault(emptyList()).associate { it.id to it.name } }
            attempt { notifications.unread() }
                .onSuccess { found ->
                    val named = names.await()
                    // One read or put away while this was read was answered after it: what is shown is newer.
                    _state.update {
                        if (it.items === before) it.copy(loading = false, items = found, names = named, error = null, pulled = false, readAt = System.currentTimeMillis(), offline = false)
                        else it.copy(loading = false, pulled = false)
                    }
                }
                .onFailure { e ->
                    _state.update { if (showLoading || it.items.isEmpty()) it.copy(loading = false, error = e, pulled = false) else it.copy(pulled = false, offline = e is IOException) }
                }
        }
    }

    /** Marks [notification] read and, once it is or waits to be, takes the reader where it points ([go]). */
    fun open(notification: AccountNotification, go: () -> Unit) = mark(notification.id, then = go) { read(notification.id) }

    /** Marks [notification] read without going anywhere. */
    fun dismiss(notification: AccountNotification) = mark(notification.id) { read(notification.id) }

    /** Marks [id] read, or keeps that to be sent in its turn: the screen is offline where there was no network. */
    private suspend fun read(id: String) {
        val submitted = notifications.read(id)
        if (submitted is Submitted.Kept) _state.update { it.copy(offline = it.offline || submitted.offline) }
    }

    fun readAll() = mark(ALL) { notifications.readAll() }

    private fun mark(id: String, then: () -> Unit = {}, block: suspend () -> Unit) {
        if (_state.value.marking != null) return
        _state.update { it.copy(marking = id, error = null) }
        viewModelScope.launch {
            attempt { block() }
                // One the API no longer has for the account, its profile deleted meanwhile, is gone either way.
                .recoverCatching { e -> if (!(e is ApiException && e.code == "NOTIFICATION_NOT_FOUND")) throw e }
                .onSuccess {
                    _state.update { s -> s.copy(marking = null, items = if (id == ALL) emptyList() else s.items.filterNot { it.id == id }) }
                    then()
                }
                .onFailure { e -> _state.update { it.copy(marking = null, error = e) } }
        }
    }

    companion object {
        /** [UiState.marking] while every notification is being marked read. */
        const val ALL = "*"
    }
}
