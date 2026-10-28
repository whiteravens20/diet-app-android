// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.NotificationRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.repository.WeightRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.data.sync.Waiting
import net.whiteravens.dietapp.domain.CalorieTarget
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.PlanOverview
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.WeightEntry
import net.whiteravens.dietapp.domain.WeightPoint
import net.whiteravens.dietapp.ui.common.ReadingAnew
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/**
 * Profiles with their engine-computed calorie targets, what their plan of the moment comes to and their
 * weights, fetched in parallel; a weight is logged and the last one taken back from its profile's card,
 * without a network as well, where the change waits to be sent.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val users: UserRepository,
    private val plans: MealPlanRepository,
    private val weights: WeightRepository,
    private val notifications: NotificationRepository,
    private val queue: ChangeQueue,
) : ViewModel() {

    /**
     * A profile, its calorie target or why there is none to show, what its plan of the moment
     * comes to (null where it has no plan) or why that could not be read, and the weights it
     * logged in the last ninety days, the newest first, with the same by day as the API draws them
     * for the chart, or why they could not be read.
     */
    data class ProfileStats(
        val profile: Profile,
        val target: CalorieTarget?,
        val targetError: Throwable? = null,
        val overview: PlanOverview? = null,
        val overviewError: Throwable? = null,
        val weights: List<WeightEntry>? = null,
        val days: List<WeightPoint>? = null,
        val weightsError: Throwable? = null,
    )

    /** What is typed into a profile's weight log, what is on its way to the API, and what went wrong. */
    data class WeightLog(
        val typed: String = "",
        val logging: Boolean = false,
        /** The last weight is being taken back. */
        val removing: Boolean = false,
        val problem: WeightProblem? = null,
    )

    /** What went wrong in a profile's weight log. */
    sealed interface WeightProblem {
        /** What was typed is no number; nothing was sent. */
        data object NotANumber : WeightProblem

        /** The API refused the weight typed, for the reason it gives. */
        data class Refused(val refusal: FieldRefusal) : WeightProblem

        /** The weight typed could not be logged. */
        data class LogFailed(val error: Throwable) : WeightProblem

        /** The last weight could not be taken back. */
        data class RemoveFailed(val error: Throwable) : WeightProblem
    }

    data class UiState(
        val loading: Boolean = true,
        val profiles: List<ProfileStats> = emptyList(),
        val error: Throwable? = null,
        /** The unit weights are shown and typed in: the account's, and until it has chosen one, that of where the phone is. */
        val unit: WeightUnit = WeightUnit.of(null, Locale.getDefault()),
        /** Each profile's weight log, by the profile's id; one that is not here is empty. */
        val weightLogs: Map<String, WeightLog> = emptyMap(),
        /** How many of the account's notifications are not read yet, as they were last read. */
        val unread: Int = 0,
        /** A read asked for by pulling the screen down is under way. */
        val pulled: Boolean = false,
        /** When what is shown was read, in the phone's milliseconds; null before the first answer. */
        val readAt: Long? = null,
        /** Reading it anew failed for want of a network: what is shown is as it was at [readAt]. */
        val offline: Boolean = false,
        /** The weights logged and removed that wait to be sent, in the order they were made. */
        val waitingWeights: List<Waiting> = emptyList(),
        /** How many notifications were read on the phone and wait for that to be sent. */
        val readsWaiting: Int = 0,
    ) {
        /** How many notifications the bell counts: those not read yet, less those read on the phone. */
        val shownUnread: Int get() = (unread - readsWaiting).coerceAtLeast(0)

        /** The profiles as shown: their weights as read, with those logged and removed that wait to be sent over them. */
        val shown: List<ProfileStats> get() = profiles.map { it.withWaiting(waitingWeights.map(Waiting::change)) }

        /** The weights logged that wait to be sent, by the ids they are shown under. */
        val waitingEntries: Set<String> get() = waitingWeights.mapNotNullTo(mutableSetOf()) { (it.change as? QueuedChange.WeightLogged)?.key }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val again = ReadingAnew()

    /** How many times each profile's weights by day were read again after a change, by the profile's id. */
    private val daysRead = mutableMapOf<String, Int>()

    /** The weights logged that were taken back while they waited: they leave the queue unsent. */
    private val withdrawn = mutableSetOf<Long>()

    init {
        load()
        followWaiting()
        followReads()
    }

    /**
     * Follows the notifications read on the phone that wait to be sent. Once one is sent, the count
     * read from the server holds it no longer.
     */
    private fun followReads() {
        viewModelScope.launch {
            queue.waiting.map { waiting -> waiting.count { it.change is QueuedChange.NotificationRead } }
                .distinctUntilChanged()
                .collect { reads ->
                    _state.update { it.copy(unread = (it.unread - (it.readsWaiting - reads).coerceAtLeast(0)).coerceAtLeast(0), readsWaiting = reads) }
                }
        }
    }

    /**
     * Follows the weights logged and removed that wait to be sent. Once one is sent, the server has
     * answered: the screen is no longer offline, and its profile's weights are read again.
     */
    private fun followWaiting() {
        viewModelScope.launch {
            queue.waiting.map { waiting -> waiting.filter { it.change is QueuedChange.WeightLogged || it.change is QueuedChange.WeightDeleted } }
                .distinctUntilChanged()
                .collect { waiting ->
                    val sent = _state.value.waitingWeights.filter { before -> waiting.none { it.id == before.id } && before.id !in withdrawn }
                    withdrawn.retainAll { id -> waiting.any { it.id == id } }
                    _state.update { it.copy(waitingWeights = waiting, offline = it.offline && sent.isEmpty()) }
                    sent.mapNotNull { profileOf(it.change) }.distinct().forEach(::readWeightsAgain)
                }
        }
    }

    fun load() = read(showLoading = true)

    /**
     * Reads the profiles again when the screen is shown again, from a profile made or changed
     * elsewhere, keeping what is shown until the answer is there; nothing while the first reading runs,
     * and on coming back to the front only as often as [ReadingAnew] lets it.
     */
    fun shownAgain(cameToTheFront: Boolean) {
        if (!_state.value.loading && again.due(cameToTheFront)) read(showLoading = false)
    }

    /** Reads the profiles again at once, the screen pulled down; what is shown stays until the answer is there. */
    fun pull() {
        if (_state.value.loading || _state.value.pulled) return
        _state.update { it.copy(pulled = true) }
        read(showLoading = false)
    }

    /** What is typed into [profileId]'s weight log. */
    fun typeWeight(profileId: String, typed: String) = changeLog(profileId) { it.copy(typed = typed) }

    /**
     * Logs what is typed for [profileId], read in the account's unit and sent in kilograms. What is no
     * number is said and not sent, and a weight the API refuses is said with the bound it gives; one
     * logged empties the field, is the newest, and the days of the chart are read again.
     */
    fun logWeight(profileId: String) {
        val log = _state.value.weightLogs[profileId] ?: WeightLog()
        if (log.logging || log.typed.isBlank()) return
        val kg = _state.value.unit.typedKg(log.typed)
        if (kg == null) {
            changeLog(profileId) { it.copy(problem = WeightProblem.NotANumber) }
            return
        }
        changeLog(profileId) { it.copy(logging = true, problem = null) }
        viewModelScope.launch {
            attempt { weights.log(profileId, kg) }
                .onSuccess { submitted ->
                    val logged = (submitted as? Submitted.Sent)?.answer
                    _state.update { state ->
                        state.copy(
                            weightLogs = state.weightLogs + (profileId to (state.weightLogs[profileId] ?: WeightLog()).copy(typed = "", logging = false)),
                            profiles = state.profiles.map { stats ->
                                if (stats.profile.id != profileId || stats.weights == null || logged == null) stats
                                else stats.copy(weights = (stats.weights + logged).sortedByDescending { it.recordedAt })
                            },
                            // Kept to be sent: shown over the card's weights as one of the changes waiting.
                            offline = state.offline || (submitted as? Submitted.Kept)?.offline == true,
                        )
                    }
                    if (logged != null) readDaysAgain(profileId)
                }
                .onFailure { e ->
                    val refused = (e as? ApiException)?.fieldRefusals?.get("kg")
                    changeLog(profileId) { it.copy(logging = false, problem = refused?.let(WeightProblem::Refused) ?: WeightProblem.LogFailed(e)) }
                }
        }
    }

    /**
     * Takes back the newest weight of [profileId], and reads the days of the chart again; asked again,
     * the one before it. One that waits to be sent is taken out of the queue instead, unless it was
     * sent meanwhile: then the weights are read again, with it among them.
     */
    fun removeLatestWeight(profileId: String) {
        val latest = _state.value.shown.firstOrNull { it.profile.id == profileId }?.weights?.firstOrNull() ?: return
        if (_state.value.weightLogs[profileId]?.removing == true) return
        changeLog(profileId) { it.copy(removing = true, problem = null) }
        val waiting = _state.value.waitingWeights.firstOrNull { (it.change as? QueuedChange.WeightLogged)?.key == latest.id }
        if (waiting != null) {
            withdrawn += waiting.id
            viewModelScope.launch {
                val taken = queue.withdraw(waiting.id)
                changeLog(profileId) { it.copy(removing = false) }
                if (!taken) {
                    withdrawn -= waiting.id
                    readWeightsAgain(profileId)
                }
            }
            return
        }
        viewModelScope.launch {
            attempt { weights.delete(profileId, latest.id) }
                .onSuccess { submitted ->
                    val sent = submitted is Submitted.Sent
                    _state.update { state ->
                        state.copy(
                            weightLogs = state.weightLogs + (profileId to (state.weightLogs[profileId] ?: WeightLog()).copy(removing = false)),
                            profiles = state.profiles.map { stats ->
                                if (stats.profile.id == profileId && sent) stats.copy(weights = stats.weights?.filterNot { it.id == latest.id }) else stats
                            },
                            // Kept to be sent: left out of the card's weights as one of the changes waiting.
                            offline = state.offline || (submitted as? Submitted.Kept)?.offline == true,
                        )
                    }
                    if (sent) readDaysAgain(profileId)
                }
                .onFailure { e -> changeLog(profileId) { it.copy(removing = false, problem = WeightProblem.RemoveFailed(e)) } }
        }
    }

    /**
     * Reads [profileId]'s weights by day again after one was logged or taken back, where its card has
     * its weights: the chart's days and means are the API's. A reading a later one overtook is dropped.
     */
    private fun readDaysAgain(profileId: String) {
        if (_state.value.profiles.firstOrNull { it.profile.id == profileId }?.weights == null) return
        val reading = (daysRead[profileId] ?: 0) + 1
        daysRead[profileId] = reading
        viewModelScope.launch {
            val read = attempt { weights.days(profileId, Instant.now().minus(WEIGHT_DAYS), ZoneId.systemDefault()) }
            if (daysRead[profileId] != reading) return@launch
            _state.update { state ->
                state.copy(
                    profiles = state.profiles.map { stats ->
                        if (stats.profile.id != profileId) stats
                        else read.fold({ stats.copy(days = it, weightsError = null) }, { stats.copy(weightsError = it) })
                    },
                )
            }
        }
    }

    /**
     * Reads [profileId]'s weights again, and their days, where its card has its weights: after a
     * change of them was sent, what the server has now takes the place of what was shown. A reading a
     * later one overtook is dropped.
     */
    private fun readWeightsAgain(profileId: String) {
        if (_state.value.profiles.firstOrNull { it.profile.id == profileId }?.weights == null) return
        val reading = (daysRead[profileId] ?: 0) + 1
        daysRead[profileId] = reading
        viewModelScope.launch {
            val read = attempt { weightsOf(profileId, Instant.now().minus(WEIGHT_DAYS)) }
            if (daysRead[profileId] != reading) return@launch
            _state.update { state ->
                state.copy(
                    profiles = state.profiles.map { stats ->
                        if (stats.profile.id != profileId) stats
                        else read.fold({ (entries, days) -> stats.copy(weights = entries, days = days, weightsError = null) }, { stats.copy(weightsError = it) })
                    },
                )
            }
        }
    }

    /** [profileId]'s weights since [since], the newest first, and the same by day where the phone is. */
    private suspend fun weightsOf(profileId: String, since: Instant) = coroutineScope {
        val entries = async { weights.entries(profileId, since) }
        val days = async { weights.days(profileId, since, ZoneId.systemDefault()) }
        entries.await() to days.await()
    }

    private fun changeLog(profileId: String, change: (WeightLog) -> WeightLog) =
        _state.update { it.copy(weightLogs = it.weightLogs + (profileId to change(it.weightLogs[profileId] ?: WeightLog()))) }

    private fun read(showLoading: Boolean) {
        again.begun()
        viewModelScope.launch {
            if (showLoading) _state.update { it.copy(loading = true, profiles = emptyList(), error = null) }
            val before = _state.value.profiles
            // Weights are shown in the account's unit; while it cannot be read, in the one shown before.
            val unit = async { attempt { users.me() }.getOrNull()?.let { WeightUnit.of(it.weightUnit, Locale.getDefault()) } }
            val unread = async { attempt { notifications.unread().size }.getOrNull() }
            val since = Instant.now().minus(WEIGHT_DAYS)
            attempt {
                coroutineScope {
                    users.profiles()
                        .map { profile ->
                            async {
                                // A failed read degrades one card, not the screen, and the card says why.
                                val target = async { attempt { users.calorieTarget(profile.id) } }
                                val overview = async { attempt { plans.overview(profile.id) } }
                                val logged = async { attempt { weightsOf(profile.id, since) } }
                                ProfileStats(
                                    profile,
                                    target.await().getOrNull(),
                                    target.await().exceptionOrNull(),
                                    overview.await().getOrNull(),
                                    overview.await().exceptionOrNull(),
                                    weights = logged.await().getOrNull()?.first,
                                    days = logged.await().getOrNull()?.second,
                                    weightsError = logged.await().exceptionOrNull(),
                                )
                            }
                        }
                        .map { it.await() }
                }
            }
                .onSuccess { found ->
                    val inUnit = unit.await()
                    val notRead = unread.await()
                    // A weight logged or taken back while this was read was answered after it: what is shown is newer.
                    _state.update {
                        if (showLoading || it.profiles === before) {
                            it.copy(
                                loading = false,
                                profiles = found,
                                error = null,
                                unit = inUnit ?: it.unit,
                                unread = notRead ?: it.unread,
                                pulled = false,
                                readAt = System.currentTimeMillis(),
                                offline = false,
                            )
                        } else {
                            it.copy(loading = false, unread = notRead ?: it.unread, pulled = false)
                        }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        if (showLoading || it.profiles.isEmpty()) it.copy(loading = false, profiles = emptyList(), error = e, pulled = false)
                        else it.copy(pulled = false, offline = e is IOException)
                    }
                }
        }
    }

    private companion object {
        /** How far back the weights of a card go, as the web's chart does. */
        val WEIGHT_DAYS: Duration = Duration.ofDays(90)
    }
}

/** The profile a change of weights is about; null for a change of anything else. */
private fun profileOf(change: QueuedChange): String? = when (change) {
    is QueuedChange.WeightLogged -> change.profileId
    is QueuedChange.WeightDeleted -> change.profileId
    else -> null
}

/**
 * The profile with the weights that wait to be sent over those read: one logged among them, under
 * its key, and one removed left out. Nothing is put over weights that could not be read.
 */
internal fun DashboardViewModel.ProfileStats.withWaiting(waiting: List<QueuedChange>): DashboardViewModel.ProfileStats {
    val read = weights ?: return this
    val removed = waiting.mapNotNullTo(mutableSetOf()) { change -> (change as? QueuedChange.WeightDeleted)?.takeIf { it.profileId == profile.id }?.id }
    val logged = waiting.mapNotNull { change -> (change as? QueuedChange.WeightLogged)?.takeIf { it.profileId == profile.id } }
        .map { WeightEntry(id = it.key, kg = it.kg, recordedAt = Instant.parse(it.recordedAt)) }
    if (removed.isEmpty() && logged.isEmpty()) return this
    return copy(weights = (read.filterNot { it.id in removed } + logged).sortedByDescending { it.recordedAt })
}
