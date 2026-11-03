// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.more

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.InstanceRules
import javax.inject.Inject

/** What the list of the rest of the app needs to know of the instance. */
@HiltViewModel
class MoreViewModel @Inject constructor(server: ServerRepository) : ViewModel() {
    /** What the instance says of itself; null until it was read. */
    val rules: StateFlow<InstanceRules?> = server.rules
}
