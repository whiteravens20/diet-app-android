// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.config.ConfirmEmailStore
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.SessionUser
import net.whiteravens.dietapp.ui.common.attempt
import java.time.Instant
import javax.inject.Inject

/**
 * The account's address while it waits to be confirmed, on an instance that sends mail, what
 * confirming it gives there, and the link sent again on the reader's word: what the notice over every
 * screen says. Without mail there is nothing to confirm with, and every address counts as confirmed
 * when its account is made. "Not now" puts the notice aside on the phone for a month, for the account
 * it was said for.
 */
@HiltViewModel
class ConfirmEmailViewModel @Inject constructor(
    private val users: UserRepository,
    instance: ServerRepository,
    private val asides: ConfirmEmailStore,
) : ViewModel() {

    data class UiState(
        /** The address that waits to be confirmed; null where none does, or before the account was read. */
        val waiting: String? = null,
        /** Confirming it lets the account use the instance's AI; otherwise it unlocks nothing. */
        val unlocksAi: Boolean = false,
        val sending: Boolean = false,
        /** The link went out again. */
        val sent: Boolean = false,
        val error: Throwable? = null,
    )

    /** The account as it was read last, where its address is not confirmed. */
    private val unconfirmed = MutableStateFlow<SessionUser?>(null)
    private val sending = MutableStateFlow(UiState())

    val state: StateFlow<UiState> = combine(unconfirmed, instance.rules, asides.aside, sending) { user, rules, aside, sent ->
        val putAside = aside != null && aside.account == user?.id && aside.until.isAfter(Instant.now())
        val shown = user?.takeIf { rules?.mailsSent == true && !putAside }
        sent.copy(waiting = shown?.email, unlocksAi = rules?.confirmationUnlocksAi == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /**
     * Reads the account again: an address confirmed in the browser since takes the notice away. A
     * read that fails leaves the notice as it was.
     */
    fun load() {
        viewModelScope.launch {
            attempt { users.me() }.onSuccess { user -> unconfirmed.value = user.takeUnless { it.emailVerified } }
        }
    }

    /** Sends the link that confirms the address again. */
    fun resend() {
        if (sending.value.sending) return
        viewModelScope.launch {
            sending.update { it.copy(sending = true, error = null) }
            attempt { users.resendVerification() }
                .onSuccess { sending.update { it.copy(sending = false, sent = true) } }
                .onFailure { e -> sending.update { it.copy(sending = false, error = e) } }
        }
    }

    /** "Not now": the notice goes for a month, on this phone, for this account. */
    fun later() {
        val account = unconfirmed.value?.id ?: return
        viewModelScope.launch { asides.putAside(account) }
    }
}
