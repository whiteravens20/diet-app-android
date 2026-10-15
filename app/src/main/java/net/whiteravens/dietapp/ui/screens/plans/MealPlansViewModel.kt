// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.MealPlanSummary
import net.whiteravens.dietapp.domain.PlanCalendarDay
import net.whiteravens.dietapp.domain.PlanFilter
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.ui.common.attempt
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Plans per profile, offline-first: the Room cache renders the whole list immediately, a network
 * refresh replaces it, and a failed refresh keeps the cache on screen and flags
 * [UiState.offline] instead of erroring the whole screen. The list can be narrowed by whether a
 * plan is under way, to come or over, and to the plans that hold days of a span. Beside it, a
 * month of the profile's plans as a calendar.
 */
@HiltViewModel
class MealPlansViewModel @Inject constructor(
    private val users: UserRepository,
    private val plans: MealPlanRepository,
) : ViewModel() {

    /** A month of a profile's plans: its days in weeks from Monday, and what the plans hold of them. */
    data class Calendar(
        val month: YearMonth,
        val days: Map<LocalDate, PlanCalendarDay> = emptyMap(),
        val loading: Boolean = true,
        val error: Throwable? = null,
    ) {
        val weeks: List<List<LocalDate>> get() = weeksOf(month)
    }

    data class UiState(
        val loading: Boolean = true,
        val profiles: List<Profile> = emptyList(),
        val selectedProfileId: String? = null,
        val plans: List<MealPlanSummary> = emptyList(),
        val filter: PlanFilter = PlanFilter(),
        /** The last page of the list fetched, and how many there are. */
        val page: Int = 1,
        val totalPages: Int = 1,
        /** When the list on screen was fetched, if it came from the cache. */
        val syncedAt: Long? = null,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val offline: Boolean = false,
        val error: Throwable? = null,
        val calendar: Calendar = Calendar(YearMonth.now()),
    ) {
        val hasMore: Boolean get() = page < totalPages
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var listing: Job? = null
    private var reading: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            attempt { users.profiles() }
                .onSuccess { profiles ->
                    _state.value = UiState(loading = false, profiles = profiles)
                    profiles.firstOrNull()?.let { selectProfile(it.id) }
                }
                .onFailure { _state.value = UiState(loading = false, error = it) }
        }
    }

    fun selectProfile(profileId: String) {
        _state.update {
            it.copy(
                selectedProfileId = profileId,
                plans = emptyList(),
                page = 1,
                totalPages = 1,
                syncedAt = null,
                error = null,
                // The month stays; its days were another profile's.
                calendar = it.calendar.copy(days = emptyMap(), loading = true, error = null),
            )
        }
        list(profileId)
        readCalendar()
    }

    /** The list narrowed anew, from its first page. */
    fun narrow(filter: PlanFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter, plans = emptyList(), page = 1, totalPages = 1, syncedAt = null, error = null) }
        _state.value.selectedProfileId?.let(::list)
    }

    /** The list's first page from the network, shown from the copy on the phone first where there is one. */
    private fun list(profileId: String) {
        val filter = _state.value.filter
        listing?.cancel()
        listing = viewModelScope.launch {
            // The copy on the phone is of the whole list: a narrowed one is asked for alone.
            val cached = if (filter.isEmpty) plans.cachedPlans(profileId) else null
            _state.update {
                it.copy(
                    plans = cached?.plans.orEmpty(),
                    totalPages = cached?.totalPages ?: 1,
                    syncedAt = cached?.syncedAt,
                    refreshing = true,
                    offline = false,
                )
            }
            attempt { plans.plans(profileId, filter = filter) }
                .onSuccess { fresh ->
                    ifStillShown(profileId, filter) {
                        it.copy(plans = fresh.plans, page = 1, totalPages = fresh.totalPages, syncedAt = null, refreshing = false, error = null)
                    }
                }
                .onFailure { e ->
                    ifStillShown(profileId, filter) {
                        it.copy(
                            refreshing = false,
                            offline = e is IOException,
                            error = e.takeIf { _ -> e !is IOException },
                        )
                    }
                }
        }
    }

    /** The next page of the list, below what is shown. */
    fun showMore() {
        val current = _state.value
        val profileId = current.selectedProfileId ?: return
        if (!current.hasMore || current.loadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            attempt { plans.plans(profileId, current.page + 1, current.filter) }
                .onSuccess { next ->
                    ifStillShown(profileId, current.filter) {
                        val shown = it.plans.map { plan -> plan.id }.toSet()
                        it.copy(
                            plans = it.plans + next.plans.filter { plan -> plan.id !in shown },
                            page = next.page,
                            totalPages = next.totalPages,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { e ->
                    ifStillShown(profileId, current.filter) {
                        it.copy(loadingMore = false, offline = e is IOException, error = e.takeIf { _ -> e !is IOException })
                    }
                }
        }
    }

    /** Another month in the calendar: [months] on from the one shown, back where it is below nought. */
    fun leaf(months: Long) = showMonth(_state.value.calendar.month.plusMonths(months))

    fun showMonth(month: YearMonth) {
        if (month == _state.value.calendar.month && !_state.value.calendar.loading) return
        // The month before stays while the next is on its way: its days are other days.
        _state.update { it.copy(calendar = it.calendar.copy(month = month, loading = true, error = null)) }
        readCalendar()
    }

    /** The month asked for again, after it failed. */
    fun retryCalendar() {
        _state.update { it.copy(calendar = it.calendar.copy(loading = true, error = null)) }
        readCalendar()
    }

    private fun readCalendar() {
        val profileId = _state.value.selectedProfileId ?: return
        val calendar = _state.value.calendar
        val weeks = calendar.weeks
        reading?.cancel()
        reading = viewModelScope.launch {
            attempt { plans.calendar(profileId, weeks.first().first(), weeks.last().last()) }
                .onSuccess { days ->
                    _state.update {
                        if (it.selectedProfileId != profileId || it.calendar.month != calendar.month) it
                        else it.copy(calendar = it.calendar.copy(days = days.associateBy(PlanCalendarDay::date), loading = false))
                    }
                }
                .onFailure { e ->
                    _state.update {
                        if (it.selectedProfileId != profileId || it.calendar.month != calendar.month) it
                        else it.copy(calendar = it.calendar.copy(loading = false, error = e))
                    }
                }
        }
    }

    /** Guards against a profile or a filter switch racing an in-flight load. */
    private fun ifStillShown(profileId: String, filter: PlanFilter, transform: (UiState) -> UiState) {
        _state.update { if (it.selectedProfileId == profileId && it.filter == filter) transform(it) else it }
    }

    companion object {
        /** The weeks a month's days fall in, Monday first, with the days of the months beside it that share them. */
        fun weeksOf(month: YearMonth): List<List<LocalDate>> {
            val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList().chunked(7)
        }
    }
}
