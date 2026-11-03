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
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.ProductRepository
import net.whiteravens.dietapp.domain.OwnProduct
import net.whiteravens.dietapp.domain.Product
import net.whiteravens.dietapp.domain.productCodeOf
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * The window a product of one's own is written in. A code the base knows is refused, as a code is in
 * one place: the window then shows what the base holds of it, and leads to one without a code.
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
        val saving: Boolean = false,
        /** Why the API did not take it. */
        val refused: Throwable? = null,
        /** What the base holds of a code it refused. */
        val base: Product? = null,
        /** The product as the API took it: the window has done its work. */
        val saved: OwnProduct? = null,
    ) {
        val codeKnown: Boolean get() = (refused as? ApiException)?.code == CODE_KNOWN
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Opens the window on [own], or on a new product of [code]. */
    fun open(own: OwnProduct?, code: String) {
        _state.value = UiState(id = own?.product?.id, draft = OwnProductDraft.of(own, code))
    }

    /** What is typed; a refusal is of what was typed before. */
    fun type(change: (OwnProductDraft) -> OwnProductDraft) = _state.update {
        it.copy(draft = change(it.draft), refused = null, base = null)
    }

    /** Whoever disagrees with the base writes theirs without a code. */
    fun withoutCode() = type { it.copy(code = "") }

    fun save() {
        val current = _state.value
        val product = current.draft.toNew()
        if (product == null || current.saving) return _state.update { it.copy(showProblems = true) }
        _state.update { it.copy(showProblems = true, saving = true, refused = null, base = null) }
        viewModelScope.launch {
            attempt { current.id?.let { id -> products.change(id, product) } ?: products.write(product) }
                .onSuccess { saved -> _state.update { it.copy(saving = false, saved = saved) } }
                .onFailure { failure ->
                    _state.update { it.copy(saving = false, refused = failure) }
                    if ((failure as? ApiException)?.code == CODE_KNOWN) readTheBase(current.draft.code)
                }
        }
    }

    private suspend fun readTheBase(typed: String) {
        val code = productCodeOf(typed) ?: return
        val base = attempt { products.inTheBase(code) }.getOrNull() ?: return
        _state.update { if (it.codeKnown) it.copy(base = base) else it }
    }

    private companion object {
        const val CODE_KNOWN = "PRODUCT_CODE_KNOWN"
    }
}
