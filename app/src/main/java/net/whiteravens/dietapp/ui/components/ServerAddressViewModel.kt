// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.BuildConfig
import net.whiteravens.dietapp.data.config.AddressProblem
import net.whiteravens.dietapp.data.config.TypedAddress
import net.whiteravens.dietapp.data.config.readAddress
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.ServerProbe
import javax.inject.Inject

/**
 * The server dialog's check of an address: read by the rules the build talks by (plain `http` in
 * a debug build alone), then asked what answers there. A wrong address is named at once; one
 * where no Diet App API answers is named too, and is kept only when asked again, for a server
 * that is down for now.
 */
@HiltViewModel
class ServerAddressViewModel @Inject constructor(private val servers: ServerRepository) : ViewModel() {

    sealed interface State {
        data object Typing : State

        data object Checking : State

        data class Wrong(val problem: AddressProblem) : State

        /** No Diet App API answers at [url] as it should: kept only when asked again. */
        data class Doubtful(val url: String, val probe: ServerProbe) : State

        /** A Diet App API answers at [url]: it is kept. */
        data class Answers(val url: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Typing)
    val state: StateFlow<State> = _state.asStateFlow()

    private var checking: Job? = null

    fun check(typed: String) {
        checking?.cancel()
        when (val address = readAddress(typed, plainHttp = BuildConfig.DEBUG)) {
            is TypedAddress.Wrong -> _state.value = State.Wrong(address.problem)
            is TypedAddress.Base -> {
                _state.value = State.Checking
                checking = viewModelScope.launch {
                    val probe = servers.probe(address.url)
                    _state.value = if (probe == ServerProbe.Answers) {
                        State.Answers(address.url)
                    } else {
                        State.Doubtful(address.url, probe)
                    }
                }
            }
        }
    }

    /** The address is typed again, or the dialog is gone: what was said of the last one no longer holds. */
    fun typing() {
        checking?.cancel()
        _state.value = State.Typing
    }
}
