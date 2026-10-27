// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.ServerVersion
import javax.inject.Inject

/** What the instance publishes of itself, read anew when the screen opens. */
@HiltViewModel
class AboutViewModel @Inject constructor(private val server: ServerRepository) : ViewModel() {
    /** Its limits, how to reach its operator and where its users find help; null until it has said. */
    val rules: StateFlow<InstanceRules?> = server.rules

    /** The version of the API the server runs, as far as it has said. */
    val version: StateFlow<ServerVersion> = server.version

    init {
        viewModelScope.launch { server.refresh() }
    }
}
