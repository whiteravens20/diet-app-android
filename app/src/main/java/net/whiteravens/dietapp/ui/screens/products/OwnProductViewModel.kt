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
import net.whiteravens.dietapp.domain.Product
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * The window a product of one's own is written in. Opened on a product of the base, it writes the
 * reader's correction of it: its figures to start from and its code, which the API keeps as theirs.
 */
@HiltViewModel
class OwnProductViewModel @Inject constructor(
    private val products: ProductRepository,
) : ViewModel() {

    data class UiState(
        val id: String? = null,
        val draft: OwnProductDraft = OwnProductDraft(),
        /** The form was sent once: what is wrong with a field is said from now on. */
        val showProblems: Boolean = false,
        /** How often it was sent with something wrong: each time the first such field comes into view. */
        val refusals: Int = 0,
        val saving: Boolean = false,
        /** Why the API did not take it. */
        val refused: Throwable? = null,
        /** The product as the API took it: the window has done its work. */
        val saved: OwnProduct? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Opens the window on [own], on a correction of [from], a product of the base, or on a new product of [code]. */
    fun open(own: OwnProduct?, code: String, from: Product? = null) {
        _state.value = UiState(id = own?.product?.id, draft = OwnProductDraft.of(own?.product ?: from, code))
    }

    /** What is typed; a refusal is of what was typed before. */
    fun type(change: (OwnProductDraft) -> OwnProductDraft) = _state.update {
        it.copy(draft = change(it.draft), refused = null)
    }

    fun save() {
        val current = _state.value
        val product = current.draft.toNew()
        if (current.saving) return
        if (product == null) return _state.update { it.copy(showProblems = true, refusals = it.refusals + 1) }
        _state.update { it.copy(showProblems = true, saving = true, refused = null) }
        viewModelScope.launch {
            attempt { current.id?.let { id -> products.change(id, product) } ?: products.write(product) }
                .onSuccess { saved -> _state.update { it.copy(saving = false, saved = saved) } }
                .onFailure { failure -> _state.update { it.copy(saving = false, refused = failure) } }
        }
    }
}
