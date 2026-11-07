// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.DiaryRepository
import net.whiteravens.dietapp.domain.Expenditure
import net.whiteravens.dietapp.ui.common.attempt
import javax.inject.Inject

/**
 * What a profile's diary and weights say it really uses, and the correction of its target that
 * comes of it: proposed on the dashboard to accept or put off, kept and taken away on the profile.
 * A decision changes the profile's target, which whoever shows it reads again ([decided]).
 */
@HiltViewModel
class ExpenditureViewModel @Inject constructor(private val diary: DiaryRepository) : ViewModel() {

    /** A decision on the correction. */
    enum class Decision { ACCEPT, LATER, DROP }

    data class UiState(
        val profileId: String = "",
        val expenditure: Expenditure? = null,
        val deciding: Boolean = false,
        /** Why the last decision was not saved. */
        val failed: Throwable? = null,
        /** How many decisions were saved: a change of it is the target's to be read again. */
        val decided: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun open(profileId: String) {
        if (_state.value.profileId == profileId) return
        _state.value = UiState(profileId = profileId)
        read()
    }

    /** Reads it again; what is shown stays where no answer comes. Nothing is shown where nothing could be read. */
    fun read() {
        val profileId = _state.value.profileId.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            attempt { diary.expenditure(profileId) }.onSuccess { read -> _state.update { if (it.profileId == profileId) it.copy(expenditure = read) else it } }
        }
    }

    fun decide(decision: Decision) {
        val profileId = _state.value.profileId.takeIf { it.isNotEmpty() } ?: return
        if (_state.value.deciding) return
        _state.update { it.copy(deciding = true, failed = null) }
        viewModelScope.launch {
            attempt {
                when (decision) {
                    Decision.ACCEPT -> diary.acceptExpenditure(profileId)
                    Decision.LATER -> diary.putOffExpenditure(profileId)
                    Decision.DROP -> diary.dropExpenditure(profileId)
                }
            }
                .onSuccess { answer -> _state.update { it.copy(expenditure = answer, deciding = false, decided = it.decided + 1) } }
                .onFailure { error -> _state.update { it.copy(deciding = false, failed = error) } }
        }
    }
}
