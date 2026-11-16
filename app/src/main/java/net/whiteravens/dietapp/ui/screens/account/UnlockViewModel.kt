// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import net.whiteravens.dietapp.ui.unlock.Unlock
import javax.inject.Inject

/** The unlock of the application by a fingerprint, as the account's settings turn it on and off. */
@HiltViewModel
class UnlockViewModel @Inject constructor(private val unlock: Unlock) : ViewModel() {
    /** Whether it is on; null until the phone has said. */
    val on: StateFlow<Boolean?> = unlock.on.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val asking = MutableStateFlow(false)

    /** A prompt is on the screen, or what it allowed is being done. */
    val busy: StateFlow<Boolean> = asking.asStateFlow()

    private val notTurnedOn = MutableStateFlow(false)

    /** The last try at turning it on ended without it: cancelled, or the phone would not make its key. */
    val failed: StateFlow<Boolean> = notTurnedOn.asStateFlow()

    /** Whether the phone can have it: a strong biometric is enrolled; read again whenever the screen comes back. */
    fun available(): Boolean = unlock.canTurnOn()

    fun turnOn() {
        if (asking.value) return
        asking.value = true
        notTurnedOn.value = false
        unlock.turnOn { on ->
            asking.value = false
            notTurnedOn.value = !on
        }
    }

    fun turnOff() {
        if (asking.value) return
        asking.value = true
        notTurnedOn.value = false
        unlock.turnOff { asking.value = false }
    }
}
