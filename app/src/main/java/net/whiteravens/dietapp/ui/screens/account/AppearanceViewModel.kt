// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.config.AppearanceStore
import net.whiteravens.dietapp.domain.Palette
import javax.inject.Inject

/** The colour scheme chosen on this phone, null where the account's is followed, and the account's. */
data class AppearanceState(val chosen: Palette? = null, val account: Palette = Palette.DEFAULT)

/** The choice of the application's colour scheme in the account's settings. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(private val appearance: AppearanceStore) : ViewModel() {
    val state: StateFlow<AppearanceState> = combine(appearance.chosen, appearance.account) { chosen, account ->
        AppearanceState(chosen, account ?: Palette.DEFAULT)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceState())

    /** Chooses [palette] on this phone; null follows the account's again. */
    fun choose(palette: Palette?) {
        viewModelScope.launch { appearance.choose(palette) }
    }
}
