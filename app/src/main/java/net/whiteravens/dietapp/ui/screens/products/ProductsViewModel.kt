// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.ProductRepository
import net.whiteravens.dietapp.domain.OwnProduct
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/** The account's products, as the screen keeps them. All of it needs a network. */
@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val products: ProductRepository,
) : ViewModel() {

    /** A question a product waits on: what the press does is said before it is done. */
    enum class Ask { SEND, REMOVE }

    data class UiState(
        val loading: Boolean = true,
        val items: List<OwnProduct> = emptyList(),
        /** Why the list could not be read. */
        val failed: Throwable? = null,
        /** Why something asked of a product or of the file was refused. */
        val refused: Throwable? = null,
        /** The product it was asked of; none for the file. */
        val refusedOn: String? = null,
        val busy: Boolean = false,
        val asking: Pair<String, Ask>? = null,
        /** The window is open: on a product, or on a new one. */
        val writing: Boolean = false,
        val writingOn: OwnProduct? = null,
        /** The file of what people added was saved. */
        val fileSaved: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        read()
    }

    fun read() {
        viewModelScope.launch {
            attempt { products.mine() }
                .onSuccess { items -> _state.update { it.copy(loading = false, items = items, failed = null) } }
                .onFailure { failure -> _state.update { it.copy(loading = false, failed = failure) } }
        }
    }

    fun openNew() = _state.update { it.copy(writing = true, writingOn = null, refused = null) }

    fun openOn(product: OwnProduct) = _state.update { it.copy(writing = true, writingOn = product, refused = null) }

    fun closeWindow() = _state.update { it.copy(writing = false, writingOn = null) }

    fun saved() {
        closeWindow()
        read()
    }

    fun ask(id: String, what: Ask) = _state.update { it.copy(asking = id to what, refused = null) }

    fun notNow() = _state.update { it.copy(asking = null) }

    fun send(id: String) = act(id) { products.send(id) }

    fun withdraw(id: String) = act(id) { products.withdraw(id) }

    fun remove(id: String) = act(id) { products.remove(id) }

    /** Does what was asked, and reads the list again whatever came of it: a refusal says the list had moved. */
    private fun act(id: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, refused = null, refusedOn = null) }
        viewModelScope.launch {
            val failure = attempt { block() }.exceptionOrNull()
            _state.update { it.copy(busy = false, asking = null, refused = failure, refusedOn = id.takeIf { failure != null }) }
            read()
        }
    }

    /** Reads the file of what people added and gives it to [write], which puts it where somebody chose. */
    fun saveAdditions(write: suspend (String) -> Unit) {
        _state.update { it.copy(refused = null, refusedOn = null, fileSaved = false) }
        viewModelScope.launch {
            attempt { write(products.additions()) }
                .onSuccess { _state.update { it.copy(fileSaved = true) } }
                .onFailure { failure -> _state.update { it.copy(refused = failure) } }
        }
    }
}
