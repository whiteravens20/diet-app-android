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
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.WEEK_DAYS
import net.whiteravens.dietapp.domain.WeekSummary
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * A profile's week in sum, as its card shows it: the seven full days before today, and in the diary
 * a week further back or forth at a time, never past yesterday. Weights are said in the account's
 * unit.
 */
@HiltViewModel
class WeekSummaryViewModel @Inject constructor(
    private val diary: DiaryRepository,
    private val users: UserRepository,
) : ViewModel() {

    data class UiState(
        val profileId: String = "",
        /** The last day of the week shown. */
        val to: LocalDate = yesterday(),
        val week: WeekSummary? = null,
        val loading: Boolean = true,
        /** Why the week could not be read, where none is shown. */
        val error: Throwable? = null,
        val unit: WeightUnit = WeightUnit.of(null, Locale.getDefault()),
    ) {
        /** The week shown is the last one: nothing comes after it. */
        val last: Boolean get() = to >= yesterday()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The card of [profileId]: its last week, and the account's unit of weight. */
    fun open(profileId: String) {
        if (_state.value.profileId == profileId) return
        _state.value = UiState(profileId = profileId)
        read()
        viewModelScope.launch {
            attempt { users.me() }.getOrNull()?.let { me -> _state.update { it.copy(unit = WeightUnit.of(me.weightUnit, Locale.getDefault())) } }
        }
    }

    /** The week before the one shown. */
    fun previous() = move(-WEEK_DAYS.toLong())

    /** The week after the one shown, up to the last. */
    fun next() {
        if (!_state.value.last) move(WEEK_DAYS.toLong())
    }

    private fun move(days: Long) {
        _state.update { it.copy(to = minOf(it.to.plusDays(days), yesterday()), week = null, loading = true, error = null) }
        read()
    }

    /** Reads the week shown again; what is shown stays where no answer comes. */
    fun read() {
        val (profileId, to) = _state.value.let { it.profileId to it.to }
        if (profileId.isEmpty()) return
        viewModelScope.launch {
            attempt { diary.week(profileId, to) }
                .onSuccess { week -> _state.update { if (it.profileId == profileId && it.to == to) it.copy(week = week, loading = false, error = null) else it } }
                .onFailure { error -> _state.update { if (it.profileId == profileId && it.to == to) it.copy(loading = false, error = error.takeIf { _ -> it.week == null }) else it } }
        }
    }
}

private fun yesterday(): LocalDate = LocalDate.now().minusDays(1)
