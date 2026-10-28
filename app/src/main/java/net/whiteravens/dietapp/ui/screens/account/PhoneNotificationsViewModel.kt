// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.notify.PhoneNotificationsSwitch
import javax.inject.Inject

/** The account's notifications on the phone: off until the user turns them on, and turned off again. */
@HiltViewModel
class PhoneNotificationsViewModel @Inject constructor(private val switch: PhoneNotificationsSwitch) : ViewModel() {
    /** Whether they are on; null until the phone has said. */
    val on: StateFlow<Boolean?> = switch.on.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun turnOn() {
        viewModelScope.launch { switch.turnOn() }
    }

    fun turnOff() {
        viewModelScope.launch { switch.turnOff() }
    }
}
