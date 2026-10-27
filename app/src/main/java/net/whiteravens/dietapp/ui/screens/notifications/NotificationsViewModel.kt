// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.AccountNotification
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * The account's notifications that are not read yet, with the names of the profiles they are for:
 * one opened is marked read first, one put away is marked read, and all can be at once.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notifications: NotificationRepository,
    private val users: UserRepository,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val items: List<AccountNotification> = emptyList(),
        /** The profiles' names by their ids, to say whose a notification is. */
        val names: Map<String, String> = emptyMap(),
        val error: Throwable? = null,
        /** The notification being marked read, [ALL] for every one; null while none is. */
        val marking: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // A profile whose name cannot be read leaves its notifications without one, nothing more.
            val names = async { attempt { users.profiles() }.getOrDefault(emptyList()).associate { it.id to it.name } }
            attempt { notifications.unread() }
                .onSuccess { found -> _state.update { it.copy(loading = false, items = found, names = names.await()) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e) } }
        }
    }

    /** Marks [notification] read and, once it is, takes the reader where it points ([go]). */
    fun open(notification: AccountNotification, go: () -> Unit) = mark(notification.id, then = go) { notifications.read(notification.id) }

    /** Marks [notification] read without going anywhere. */
    fun dismiss(notification: AccountNotification) = mark(notification.id) { notifications.read(notification.id) }

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
