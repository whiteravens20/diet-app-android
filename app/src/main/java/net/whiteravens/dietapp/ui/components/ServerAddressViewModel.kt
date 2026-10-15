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
import net.whiteravens.dietapp.data.config.LocalNetworkAccess
import net.whiteravens.dietapp.data.config.TypedAddress
import net.whiteravens.dietapp.data.config.readAddress
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.ServerProbe
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

/**
 * The server dialog's check of an address: read by the rules the build talks by (plain `http` in
 * a debug build alone), then asked what answers there. A wrong address is named at once; one
 * where no Diet App API answers is named too, and is kept only when asked again, for a server
 * that is down for now. A server on the local network is asked about only once the app may reach
 * it (Android 17): until then the permission is asked for, and nothing is sent.
 */
@HiltViewModel
class ServerAddressViewModel @Inject constructor(
    private val servers: ServerRepository,
    private val localNetwork: LocalNetworkAccess,
) : ViewModel() {

    sealed interface State {
        data object Typing : State

        data object Checking : State

        data class Wrong(val problem: AddressProblem) : State

        /** No Diet App API answers at [url] as it should: kept only when asked again. */
        data class Doubtful(val url: String, val probe: ServerProbe) : State

        /** A Diet App API answers at [url]: it is kept. */
        data class Answers(val url: String) : State

        /** [url] is on the local network, which the app may not reach yet: the permission is asked for. */
        data class NeedsLocalNetwork(val url: String) : State

        /** The permission to reach the local network was refused, so nothing can be sent to [url]. */
        data class LocalNetworkRefused(val url: String) : State
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
                    if (!localNetwork.allowed() && localNetwork.isLocal(address.url.toHttpUrl().host)) {
                        _state.value = State.NeedsLocalNetwork(address.url)
                    } else {
                        probe(address.url)
                    }
                }
            }
        }
    }

    /** The answer to the request for the permission to reach the local network. */
    fun localNetworkAnswered(granted: Boolean) {
        val asked = _state.value as? State.NeedsLocalNetwork ?: return
        if (!granted) {
            _state.value = State.LocalNetworkRefused(asked.url)
            return
        }
        _state.value = State.Checking
        checking = viewModelScope.launch { probe(asked.url) }
    }

    private suspend fun probe(url: String) {
        val probe = servers.probe(url)
        _state.value = if (probe == ServerProbe.Answers) State.Answers(url) else State.Doubtful(url, probe)
    }

    /** The address is typed again, or the dialog is gone: what was said of the last one no longer holds. */
    fun typing() {
        checking?.cancel()
        _state.value = State.Typing
    }
}
