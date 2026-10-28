// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.FavoriteRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.data.sync.Waiting
import net.whiteravens.dietapp.domain.FavoriteFilter
import net.whiteravens.dietapp.domain.MarkedRecipe
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.ui.common.ReadingAnew
import net.whiteravens.dietapp.ui.common.attempt
import java.io.IOException
import java.text.Collator
import javax.inject.Inject

/**
 * The recipes a profile marked, as the web's page has them: narrowed by words of the title, a meal,
 * how they are marked and a tag of the profile's, in the order chosen; each recipe's mark and tags
 * changed where they are shown, and its mark taken off. A change is shown from the moment it is
 * chosen and taken back where the API refuses it; without a network it waits to be sent, shown over
 * the marks read.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favorites: FavoriteRepository,
    private val users: UserRepository,
    private val queue: ChangeQueue,
) : ViewModel() {

    /** What a mark is changed to: how the recipe is marked, the tags it has, or both. */
    data class Change(val sentiment: String? = null, val tags: List<String>? = null)

    /** What could not be done to a mark: saved as changed, or taken off. */
    enum class Failure { SAVE, REMOVE }

    data class UiState(
        val loadingProfiles: Boolean = true,
        /** A read asked for by pulling the screen down is under way. */
        val pulled: Boolean = false,
        /** When what is shown was read, in the phone's milliseconds; null before the first answer. */
        val readAt: Long? = null,
        /** Reading it anew failed for want of a network: what is shown is as it was at [readAt]. */
        val offline: Boolean = false,
        val profiles: List<Profile> = emptyList(),
        val profilesError: Throwable? = null,
        /** The profile whose marks are shown. */
        val profileId: String? = null,
        val filter: FavoriteFilter = FavoriteFilter(),
        val loading: Boolean = false,
        val items: List<MarkedRecipe> = emptyList(),
        /** Every tag the profile's marks are under, in the order of the alphabet: what the list can be narrowed to. */
        val tags: List<String> = emptyList(),
        val error: Throwable? = null,
        /** A change of a mark on its way to the API, and the recipe it is of. */
        val sending: Pair<String, Change>? = null,
        /** The recipe whose mark is being taken off. */
        val removing: String? = null,
        /** What could not be done to a mark, and why. */
        val failed: Pair<Failure, Throwable>? = null,
        /** The changes of marks that wait to be sent, in the order they were made. */
        val waiting: List<Waiting> = emptyList(),
    ) {
        val busy: Boolean get() = sending != null || removing != null

        /** [item] as the screen shows it: as it is kept, with the changes of it that wait to be sent and what is on its way on top. */
        fun shown(item: MarkedRecipe): MarkedRecipe {
            val waited = mine(item.recipeId).fold(item) { shown, change ->
                if (change is QueuedChange.Marked) shown.copy(sentiment = change.sentiment, tags = change.tags) else shown
            }
            val change = sending?.takeIf { it.first == item.recipeId }?.second ?: return waited
            return waited.copy(sentiment = change.sentiment ?: waited.sentiment, tags = change.tags ?: waited.tags)
        }

        /** The marks shown: those read, less those whose taking off waits to be sent. */
        val shownItems: List<MarkedRecipe> get() = items.filterNot { mine(it.recipeId).lastOrNull() is QueuedChange.Unmarked }

        /** The recipes whose mark has a change that waits to be sent. */
        val waitingRecipes: Set<String> get() = shownItems.mapNotNullTo(mutableSetOf()) { item -> item.recipeId.takeIf { mine(it).isNotEmpty() } }

        /** The changes of the profile's mark of [recipeId] that wait to be sent, in the order they were made. */
        private fun mine(recipeId: String): List<QueuedChange> =
            waiting.map(Waiting::change).filter { markOf(it) == (profileId to recipeId) }
    }

    /** The words of the search field as typed; looked for once typing pauses. */
    val query = MutableStateFlow("")

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var reading: Job? = null

    private val again = ReadingAnew()

    init {
        loadProfiles()
        followWaiting()
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            query.drop(1).debounce(350).distinctUntilChanged().collect { words ->
                _state.update { it.copy(filter = it.filter.copy(search = words)) }
                read()
            }
        }
    }

    /**
     * Follows the changes of marks that wait to be sent. Once one is sent, the server has answered:
     * the screen is no longer offline, and the list is read again as the server has it.
     */
    private fun followWaiting() {
        viewModelScope.launch {
            queue.waiting.map { waiting -> waiting.filter { markOf(it.change) != null } }
                .distinctUntilChanged()
                .collect { waiting ->
                    val sent = _state.value.waiting.any { before -> waiting.none { it.id == before.id } }
                    _state.update { it.copy(waiting = waiting, offline = it.offline && !sent) }
                    if (sent) read(showLoading = false)
                }
        }
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _state.update { it.copy(loadingProfiles = true, profilesError = null) }
            attempt { users.profiles() }
                .onSuccess { found ->
                    _state.update { it.copy(loadingProfiles = false, profiles = found, profileId = it.profileId?.takeIf { id -> found.any { p -> p.id == id } } ?: found.firstOrNull()?.id) }
                    read()
                }
                .onFailure { e -> _state.update { it.copy(loadingProfiles = false, profilesError = e, pulled = false) } }
        }
    }

    /**
     * Reads the list again when the screen is shown again, keeping what is shown until the answer is
     * there; on coming back to the front only as often as [ReadingAnew] lets it.
     */
    fun shownAgain(cameToTheFront: Boolean) {
        if (again.due(cameToTheFront)) readAnew()
    }

    /** Reads the list again at once, the screen pulled down; the profiles too where they could not be read. */
    fun pull() {
        if (_state.value.pulled) return
        _state.update { it.copy(pulled = true) }
        readAnew()
    }

    private fun readAnew() {
        val current = _state.value
        when {
            current.profilesError != null -> loadProfiles()
            current.loadingProfiles || current.loading || current.busy || current.profileId == null -> _state.update { it.copy(pulled = false) }
            else -> read(showLoading = false)
        }
    }

    fun retry() = read()

    /** Shows the marks of [profileId]: a tag is a profile's own, so one of another profile's narrows nothing here. */
    fun pickProfile(profileId: String) {
        if (_state.value.profileId == profileId || _state.value.busy) return
        _state.update { it.copy(profileId = profileId, filter = it.filter.copy(tag = null), items = emptyList(), tags = emptyList(), failed = null) }
        read()
    }

    /** Narrows the list and puts it in order as [filter] says; the words are the search field's. */
    fun narrow(filter: FavoriteFilter) {
        _state.update { it.copy(filter = filter.copy(search = it.filter.search)) }
        read()
    }

    fun setSentiment(item: MarkedRecipe, sentiment: String) {
        if (sentiment != _state.value.shown(item).sentiment) change(item, Change(sentiment = sentiment))
    }

    /** Puts [typed] on [item] as a tag, trimmed: one it has already, or one past the most a recipe holds, is not put on. */
    fun addTag(item: MarkedRecipe, typed: String) {
        val tag = typed.trim()
        val tags = _state.value.shown(item).tags
        if (tag.isEmpty() || tag.length > FavoriteFilter.TAG_LENGTH || tag in tags || tags.size >= FavoriteFilter.MOST_TAGS) return
        change(item, Change(tags = tags + tag))
    }

    fun removeTag(item: MarkedRecipe, tag: String) = change(item, Change(tags = _state.value.shown(item).tags - tag))

    /** Takes the mark off [item]; the list is read again either way, as a mark taken off elsewhere is gone too. */
    fun remove(item: MarkedRecipe) {
        val profileId = _state.value.profileId ?: return
        if (_state.value.busy) return
        _state.update { it.copy(removing = item.recipeId, failed = null) }
        viewModelScope.launch {
            attempt { favorites.unmark(profileId, item.recipeId) }
                .onSuccess { submitted ->
                    _state.update {
                        when (submitted) {
                            is Submitted.Sent -> it.copy(removing = null, items = it.items.filterNot { kept -> kept.recipeId == item.recipeId })
                            // Kept to be sent: left out of the list as one of the changes waiting.
                            is Submitted.Kept -> it.copy(removing = null, offline = it.offline || submitted.offline)
                        }
                    }
                    if (submitted is Submitted.Sent) read(showLoading = false)
                }
                .onFailure { e ->
                    _state.update { it.copy(removing = null, failed = Failure.REMOVE to e) }
                    read(showLoading = false)
                }
        }
    }

    /** Marks [item] anew, with [change] over what it is shown as now: the API keeps both, the sentiment and the tags, as sent. */
    private fun change(item: MarkedRecipe, change: Change) {
        val profileId = _state.value.profileId ?: return
        if (_state.value.busy) return
        val current = _state.value.shown(item)
        _state.update { it.copy(sending = item.recipeId to change, failed = null) }
        viewModelScope.launch {
            attempt { favorites.mark(profileId, item.recipeId, change.sentiment ?: current.sentiment, change.tags ?: current.tags) }
                .onSuccess { submitted ->
                    _state.update { state ->
                        when (submitted) {
                            // Kept as sent until the list read again says otherwise: nothing flickers back.
                            is Submitted.Sent -> state.copy(sending = null, items = state.items.map { if (it.recipeId == item.recipeId) state.shown(it) else it })
                            // Kept to be sent: shown over the mark as one of the changes waiting.
                            is Submitted.Kept -> state.copy(sending = null, offline = state.offline || submitted.offline)
                        }
                    }
                    if (submitted is Submitted.Sent) read(showLoading = false)
                }
                .onFailure { e ->
                    _state.update { it.copy(sending = null, failed = Failure.SAVE to e) }
                    read(showLoading = false)
                }
        }
    }

    /**
     * Reads the profile's marks as the filter narrows them, and every mark of the profile for the tags
     * to choose from where the filter leaves some out. Where [showLoading] is false, what is shown
     * stays until the answer is there, and stays where none comes.
     */
    private fun read(showLoading: Boolean = true) {
        val profileId = _state.value.profileId ?: return _state.update { it.copy(pulled = false) }
        again.begun()
        reading?.cancel()
        reading = viewModelScope.launch {
            if (showLoading) _state.update { it.copy(loading = true, error = null) }
            val before = _state.value.items
            val filter = _state.value.filter
            attempt {
                coroutineScope {
                    val every = if (filter.narrowed) async { favorites.marked(profileId) } else null
                    val shown = favorites.marked(profileId, filter)
                    (every?.await() ?: shown) to shown
                }
            }
                .onSuccess { (every, shown) ->
                    // A mark changed while this was read was answered after it: what is shown is newer.
                    _state.update {
                        if (showLoading || it.items === before) it.copy(loading = false, items = shown, tags = tagsOf(every), error = null, pulled = false, readAt = System.currentTimeMillis(), offline = false)
                        else it.copy(loading = false, pulled = false)
                    }
                }
                .onFailure { e ->
                    _state.update { if (showLoading || it.items.isEmpty()) it.copy(loading = false, items = emptyList(), error = e, pulled = false) else it.copy(loading = false, pulled = false, offline = e is IOException) }
                }
        }
    }

    private fun tagsOf(marks: List<MarkedRecipe>): List<String> = marks.flatMap { it.tags }.distinct().sortedWith(Collator.getInstance())
}

/** The profile and the recipe a change of a mark is of; null for a change of anything else. */
internal fun markOf(change: QueuedChange): Pair<String, String>? = when (change) {
    is QueuedChange.Marked -> change.profileId to change.recipeId
    is QueuedChange.Unmarked -> change.profileId to change.recipeId
    else -> null
}
