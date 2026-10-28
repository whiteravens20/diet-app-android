// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.ServerVersion
import net.whiteravens.dietapp.ui.common.ReadingAnew
import javax.inject.Inject

/** What the instance publishes of itself, read anew when the screen opens, when it is shown again and when it is pulled down. */
@HiltViewModel
class AboutViewModel @Inject constructor(private val server: ServerRepository) : ViewModel() {
    /** Its limits, how to reach its operator and where its users find help; null until it has said. */
    val rules: StateFlow<InstanceRules?> = server.rules

    /** The version of the API the server runs, as far as it has said. */
    val version: StateFlow<ServerVersion> = server.version

    private val _pulled = MutableStateFlow(false)

    /** A read asked for by pulling the screen down is under way. */
    val pulled: StateFlow<Boolean> = _pulled.asStateFlow()

    /** When what the instance publishes was read, and whether reading it anew failed for want of a network. */
    data class Reading(val readAt: Long? = null, val offline: Boolean = false)

    private val _reading = MutableStateFlow(Reading())
    val reading: StateFlow<Reading> = _reading.asStateFlow()

    private val again = ReadingAnew()

    init {
        read()
    }

    /** Reads what the instance publishes again when the screen is shown again; on coming back to the front only as often as [ReadingAnew] lets it. */
    fun shownAgain(cameToTheFront: Boolean) {
        if (again.due(cameToTheFront)) read()
    }

    /** Reads what the instance publishes again at once, the screen pulled down. */
    fun pull() {
        if (_pulled.value) return
        _pulled.value = true
        read()
    }

    private fun read() {
        again.begun()
        viewModelScope.launch {
            val answered = server.readAgain()
            _reading.update { if (answered) Reading(readAt = System.currentTimeMillis()) else it.copy(offline = it.readAt != null) }
            _pulled.value = false
        }
    }
}
