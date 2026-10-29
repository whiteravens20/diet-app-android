// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.update.Asked
import net.whiteravens.dietapp.data.update.UpdateInstaller
import net.whiteravens.dietapp.data.update.UpdateStep
import net.whiteravens.dietapp.data.update.Updates
import net.whiteravens.dietapp.data.update.VersionFile
import javax.inject.Inject

/** A newer build of the application: whether one is out, getting it, and asking for it. */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: Updates,
    private val installer: UpdateInstaller,
) : ViewModel() {
    /** Whether this build asks anywhere at all. */
    val hasChannel: Boolean = updates.hasChannel()

    val available: StateFlow<VersionFile?> = updates.available.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latest: StateFlow<VersionFile?> = updates.latest.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val asked: StateFlow<Asked?> = updates.asked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val on: StateFlow<Boolean> = updates.on.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val step: StateFlow<UpdateStep> = installer.step

    private val _asking = MutableStateFlow(false)
    val asking: StateFlow<Boolean> = _asking.asStateFlow()

    fun allowed(): Boolean = installer.allowed()

    fun allowance(): Intent = installer.allowance()

    fun get(version: VersionFile) {
        viewModelScope.launch { installer.get(version) }
    }

    /** Hides [version] until a newer one is out, and drops what was downloaded of it. */
    fun later(version: VersionFile) {
        installer.reset()
        viewModelScope.launch { updates.putOff(version.versionCode) }
    }

    fun askNow() {
        if (_asking.value) return
        _asking.value = true
        viewModelScope.launch {
            try {
                updates.ask()
            } finally {
                _asking.value = false
            }
        }
    }

    fun turn(on: Boolean) {
        viewModelScope.launch { updates.turn(on) }
    }
}
