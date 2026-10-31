// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.DiaryRepository
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.RecipeRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.data.sync.ChangeQueue
import net.whiteravens.dietapp.data.sync.QueuedChange
import net.whiteravens.dietapp.data.sync.Submitted
import net.whiteravens.dietapp.data.sync.Waiting
import net.whiteravens.dietapp.domain.DIARY_DAYS_BACK
import net.whiteravens.dietapp.domain.DiaryDay
import net.whiteravens.dietapp.domain.DiaryEntry
import net.whiteravens.dietapp.domain.DiaryIngredient
import net.whiteravens.dietapp.domain.DiaryPlanMeal
import net.whiteravens.dietapp.domain.FIRST_DIARY_DAY
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.LAST_DIARY_DAY
import net.whiteravens.dietapp.domain.MEAL_TYPES
import net.whiteravens.dietapp.domain.MOST_DIARY_ENTRIES
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.Rebalance
import net.whiteravens.dietapp.domain.Rebalanced
import net.whiteravens.dietapp.domain.Recipe
import net.whiteravens.dietapp.domain.RecipeFilter
import net.whiteravens.dietapp.domain.mealAt
import net.whiteravens.dietapp.ui.common.ReadingAnew
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.navigation.DiaryRoute
import net.whiteravens.dietapp.ui.screens.profiles.ProfilesViewModel
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * The diary, as the web's page keeps it: what a profile ate on a day, the meals its plan marks as
 * eaten and the entries written for the day, against the day's target. A day of today or of the year
 * before it takes entries. An entry is written, changed and taken out, and a meal of the plan marked
 * eaten, without a network as well, where the change waits to be sent, shown over what was read; what
 * was read last of a day is there without a network. Copying a day and resizing the plan's meals ask
 * the server. A meal marked eaten says how its day was balanced, with the way back.
 */
@HiltViewModel
class DiaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diary: DiaryRepository,
    private val users: UserRepository,
    private val plans: MealPlanRepository,
    private val recipes: RecipeRepository,
    private val queue: ChangeQueue,
) : ViewModel() {

    /** What could not be done. */
    enum class Failure { REMOVE, EATEN, REBALANCE, COPY }

    /** What became of a copy of a day: how many entries came, from which day. */
    data class Copied(val from: LocalDate, val count: Int)

    data class UiState(
        val loadingProfiles: Boolean = true,
        val profiles: List<Profile> = emptyList(),
        val profilesError: Throwable? = null,
        /** The profile whose diary is shown. */
        val profileId: String? = null,
        val date: LocalDate = LocalDate.now(),
        val loading: Boolean = false,
        /** The day as it was read, or as it was kept; null before that, or where neither can be had. */
        val day: DiaryDay? = null,
        val error: Throwable? = null,
        /** A read asked for by pulling the screen down is under way. */
        val pulled: Boolean = false,
        /** When the day shown was read, in the phone's milliseconds; null where it is not known. */
        val readAt: Long? = null,
        /** Reading it anew failed for want of a network: what is shown is as it was at [readAt]. */
        val offline: Boolean = false,
        /** The changes of diaries and of plans' meals that wait to be sent, in the order they were made. */
        val waiting: List<Waiting> = emptyList(),
        /** The window that adds an entry, while it is open. */
        val form: EntryForm? = null,
        /** The window that changes an entry, while it is open. */
        val edit: EntryEdit? = null,
        /** What the open window holds is said to be wrong, once it was sent. */
        val showProblems: Boolean = false,
        /** What is in the open window is on its way. */
        val sending: Boolean = false,
        /** The fields the API refused of what the open window sent, by their paths. */
        val refused: Map<String, FieldRefusal> = emptyMap(),
        /** Why the API refused what the open window sent, other than by its fields. */
        val windowError: Throwable? = null,
        /** A change of the day that asks the server is on its way, or an entry being taken out. */
        val changing: Boolean = false,
        /** What could not be done, and why. */
        val failed: Pair<Failure, Throwable>? = null,
        /** What the last change rebalanced, and of which plan, until it is put away. */
        val rebalance: Rebalance? = null,
        val rebalancedPlan: String? = null,
        /** What a press to resize the day left of its gap where it moved nothing: what it says instead of nothing. */
        val resizeLeft: Double? = null,
        /** The day to copy from. */
        val copyFrom: LocalDate = date.minusDays(1).coerceAtLeast(FIRST_DIARY_DAY),
        val copying: Boolean = false,
        /** What the last copy into this day came to, while the page stays on it. */
        val copied: Copied? = null,
    ) {
        val today: LocalDate get() = LocalDate.now()

        /** The day is still to come: nothing can have been eaten on it. */
        val ahead: Boolean get() = date > today

        /** The day is more than a year back: it takes no new entry. */
        val tooOld: Boolean get() = date < today.minusDays(DIARY_DAYS_BACK)

        private val changes: List<QueuedChange> get() = waiting.map(Waiting::change)

        /** The entries shown: as read, with those that wait to be sent over them. */
        val entries: List<DiaryEntry> get() = profileId?.let { day?.entries.orEmpty().entriesWithWaiting(it, date.toString(), changes) }.orEmpty()

        /** The meals of the plan shown: as read, with those that wait to be sent over them. */
        val planMeals: List<DiaryPlanMeal> get() = day?.plan?.let { plan -> plan.meals.mealsWithWaiting(plan.id, date.toString(), changes) }.orEmpty()

        /** The meals the day has, those of its plan and those something was written for, in the order they are eaten. */
        val slots: List<String> get() = MEAL_TYPES.filter { slot -> planMeals.any { it.slot == slot } || entries.any { it.slot == slot } }

        /** The day holds the most entries it may. */
        val full: Boolean get() = entries.size >= MOST_DIARY_ENTRIES

        /** Whether the day takes a new entry. */
        val canAdd: Boolean get() = !ahead && !tooOld && !full

        /** The entries with a change that waits to be sent: one written, under its key, or one changed. */
        val waitingEntries: Set<String> get() = waiting.mapNotNullTo(mutableSetOf()) { waiting ->
            when (val change = waiting.change) {
                is QueuedChange.DiaryAdded -> change.key
                is QueuedChange.DiaryChanged -> change.id
                else -> null
            }
        }

        /** What is written and waits to be sent, by its key: the change in the queue that takes it back. */
        val waitingAdditions: Map<String, Long> get() = waiting.mapNotNull { waiting -> (waiting.change as? QueuedChange.DiaryAdded)?.let { it.key to waiting.id } }.toMap()

        /** The plan's meals with a change that waits to be sent: an eaten mark, or a meal of one's own, under its key. */
        val waitingMeals: Set<String> get() = waiting.mapNotNullTo(mutableSetOf()) { waiting ->
            when (val change = waiting.change) {
                is QueuedChange.Eaten -> change.mealId
                is QueuedChange.OwnMealAdded -> change.key
                else -> null
            }
        }

        /** The plan's meals of one's own that wait to be added: they are marked eaten once the server has them. */
        val waitingOwnMeals: Set<String> get() = waiting.mapNotNullTo(mutableSetOf()) { (it.change as? QueuedChange.OwnMealAdded)?.key }

        /** How many changes that wait to be sent the day's sum, the server's, leaves out. */
        val leftOut: Int get() = profileId?.let { waitingOverDay(it, date.toString(), day?.entries.orEmpty(), day?.plan?.id, day?.plan?.meals.orEmpty(), changes) } ?: 0
    }

    private val _state = MutableStateFlow(
        UiState(
            date = savedStateHandle.get<String>(DiaryRoute::date.name).toDiaryDay() ?: LocalDate.now(),
            profileId = savedStateHandle.get<String>(DiaryRoute::profileId.name),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var reading: Job? = null
    private var searching: Job? = null

    private val again = ReadingAnew()

    /** What was written and taken back while it waited: it leaves the queue unsent. */
    private val withdrawn = mutableSetOf<Long>()

    init {
        // The window asked for by the way here opens once: a screen brought back keeps it as it was left.
        if (savedStateHandle.get<Boolean>(DiaryRoute::adding.name) == true) {
            savedStateHandle[DiaryRoute::adding.name] = false
            openWindow(mealAt(LocalTime.now()))
        }
        loadProfiles()
        followWaiting()
    }

    /**
     * Follows the changes that wait to be sent. Once one is sent, the server has answered: the screen
     * is no longer offline, and the day is read again as the server has it.
     */
    private fun followWaiting() {
        viewModelScope.launch {
            queue.waiting.map { waiting -> waiting.filter { it.change.isOfADiary() } }
                .distinctUntilChanged()
                .collect { waiting ->
                    val sent = _state.value.waiting.any { before -> waiting.none { it.id == before.id } && before.id !in withdrawn }
                    withdrawn.retainAll { id -> waiting.any { it.id == id } }
                    _state.update { it.copy(waiting = waiting, offline = it.offline && !sent) }
                    if (sent) read(showLoading = false)
                }
        }
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _state.update { it.copy(loadingProfiles = true, profilesError = null) }
            attempt { users.profilesOrKept() }
                .onSuccess { found ->
                    _state.update { state ->
                        state.copy(
                            loadingProfiles = false,
                            profiles = found,
                            profileId = state.profileId?.takeIf { id -> found.any { it.id == id } } ?: found.firstOrNull()?.id,
                        )
                    }
                    read()
                }
                .onFailure { e -> _state.update { it.copy(loadingProfiles = false, profilesError = e, pulled = false) } }
        }
    }

    /** Reads the day again when the screen is shown again; on coming back to the front only as often as [ReadingAnew] lets it. */
    fun shownAgain(cameToTheFront: Boolean) {
        if (again.due(cameToTheFront)) readAnew()
    }

    /** Reads the day again at once, the screen pulled down; the profiles too where they could not be read. */
    fun pull() {
        if (_state.value.pulled) return
        _state.update { it.copy(pulled = true) }
        readAnew()
    }

    private fun readAnew() {
        val current = _state.value
        when {
            current.profilesError != null -> loadProfiles()
            current.loadingProfiles || current.loading || current.profileId == null -> _state.update { it.copy(pulled = false) }
            else -> read(showLoading = false)
        }
    }

    fun retry() = read()

    /**
     * Reads the day: what was kept of it is shown first, where it was kept. Where [showLoading] is
     * false, what is shown stays until the answer is there, and stays where none comes. Without a
     * network what is shown stays too, and says so.
     */
    private fun read(showLoading: Boolean = true) {
        val profileId = _state.value.profileId ?: return _state.update { it.copy(pulled = false) }
        val date = _state.value.date
        again.begun()
        reading?.cancel()
        reading = viewModelScope.launch {
            if (showLoading) {
                _state.update { it.copy(loading = true, error = null) }
                val kept = attempt { diary.keptDay(profileId, date) }.getOrNull()
                _state.update { it.copy(loading = kept == null, day = kept, readAt = kept?.syncedAt) }
            }
            attempt { diary.day(profileId, date) }
                .onSuccess { day -> _state.update { it.copy(loading = false, day = day, error = null, pulled = false, offline = false, readAt = System.currentTimeMillis()) } }
                .onFailure { e ->
                    _state.update {
                        when {
                            // The window opened on what cannot be looked for without a network, and nothing was done in it: it opens on what can.
                            e is IOException -> it.copy(
                                loading = false,
                                pulled = false,
                                offline = true,
                                form = it.form?.takeIf { form -> form.untouched }?.copy(kind = EntryKind.MEAL) ?: it.form,
                            )
                            showLoading || it.day == null -> it.copy(loading = false, pulled = false, day = null, error = e)
                            else -> it.copy(loading = false, pulled = false)
                        }
                    }
                }
        }
    }

    fun pickProfile(profileId: String) {
        if (_state.value.profileId == profileId) return
        _state.update { it.left().copy(profileId = profileId, day = null) }
        read()
    }

    /** Goes to [date], one a request may name; what was said of the day before goes. */
    fun pickDay(date: LocalDate) {
        if (_state.value.date == date || date < FIRST_DIARY_DAY || date > LAST_DIARY_DAY) return
        _state.update { it.left().copy(date = date, day = null, copyFrom = date.minusDays(1).coerceAtLeast(FIRST_DIARY_DAY), copied = null) }
        read()
    }

    private fun UiState.left() = copy(failed = null, rebalance = null, rebalancedPlan = null, resizeLeft = null, copied = null)

    // ── The window that adds an entry ───────────────────────────────────────

    /** Opens the window that adds an entry, at the meal [slot]; without a network on what can be written without one. */
    fun openWindow(slot: String) {
        searching?.cancel()
        _state.update {
            it.copy(
                form = EntryForm(slot = slot, kind = if (it.offline) EntryKind.MEAL else EntryKind.RECIPE),
                showProblems = false,
                refused = emptyMap(),
                windowError = null,
            )
        }
    }

    /** Opens the window at the meal the phone's clock says, or at the first the day has where it is another day. */
    fun openWindowNow() {
        val current = _state.value
        openWindow(if (current.date == current.today) mealAt(LocalTime.now()) else current.slots.firstOrNull() ?: MEAL_TYPES.first())
    }

    /** Puts the open window away: what was typed in it goes with it, and so does an answer on its way to it. */
    fun closeWindow() {
        searching?.cancel()
        _state.update { it.copy(form = null, edit = null, showProblems = false, refused = emptyMap(), windowError = null) }
    }

    private fun changeForm(change: (EntryForm) -> EntryForm) = _state.update { state ->
        state.copy(form = state.form?.let(change), windowError = null)
    }

    fun chooseKind(kind: EntryKind) {
        searching?.cancel()
        changeForm { it.copy(kind = kind, search = "", recipes = emptyList(), ingredients = emptyList(), searched = false, searchFailed = null) }
        _state.update { it.copy(showProblems = false, refused = emptyMap()) }
    }

    fun chooseSlot(slot: String) = changeForm { it.copy(slot = slot) }

    /** Looks for what is typed once two letters are typed and typing pauses, as the pantry's search does. */
    fun search(text: String) {
        changeForm { it.copy(search = text, recipes = emptyList(), ingredients = emptyList(), searched = false, searchFailed = null) }
        searching?.cancel()
        val kind = _state.value.form?.kind ?: return
        if (text.trim().length < ProfilesViewModel.SEARCH_FROM) return
        searching = viewModelScope.launch {
            delay(ProfilesViewModel.SEARCH_DELAY_MS)
            if (kind == EntryKind.RECIPE) {
                attempt { recipes.search(RecipeFilter(search = text.trim())).items.take(OFFERED) }.let { found ->
                    answered(text, found.exceptionOrNull()) { it.copy(recipes = found.getOrDefault(emptyList())) }
                }
            } else {
                attempt { diary.ingredients(text.trim()).take(OFFERED) }.let { found ->
                    answered(text, found.exceptionOrNull()) { it.copy(ingredients = found.getOrDefault(emptyList())) }
                }
            }
        }
    }

    private fun answered(text: String, failed: Throwable?, found: (EntryForm) -> EntryForm) = changeForm { form ->
        if (form.search != text) form else found(form).copy(searched = true, searchFailed = failed)
    }

    fun pickRecipe(recipe: Recipe) {
        searching?.cancel()
        changeForm { it.copy(recipe = recipe, search = "", recipes = emptyList(), searched = false, searchFailed = null) }
    }

    /** Takes [ingredient], in the unit the API offers first: the one it is weighed or measured in. */
    fun pickIngredient(ingredient: DiaryIngredient) {
        searching?.cancel()
        changeForm { it.copy(ingredient = ingredient, unit = ingredient.units.first(), search = "", ingredients = emptyList(), searched = false, searchFailed = null) }
    }

    /** Lets another be looked for in the place of the one picked. */
    fun unpick() = changeForm { it.copy(recipe = null, ingredient = null, servings = "1", quantity = "") }

    /** What is typed into a field of the window: [change] says which. */
    fun typeEntry(field: EntryField, change: (EntryForm) -> EntryForm) {
        changeForm(change)
        _state.update { it.copy(refused = it.refused - field.path) }
    }

    /** Writes what the window holds for the day: the window goes, and a refusal leaves it as it is, to be put right. */
    fun addEntry() {
        val current = _state.value
        val form = current.form ?: return
        val profileId = current.profileId ?: return
        if (current.sending) return
        val entry = form.entry() ?: return _state.update { it.copy(showProblems = true) }
        _state.update { it.copy(sending = true, showProblems = true, refused = emptyMap(), windowError = null, failed = null, copied = null, resizeLeft = null) }
        viewModelScope.launch {
            attempt { diary.add(profileId, current.date, entry) }
                .onSuccess { submitted ->
                    // Kept to be sent: shown among the entries as one of the changes waiting.
                    _state.update { it.copy(sending = false, form = null, showProblems = false, offline = it.offline || (submitted as? Submitted.Kept)?.offline == true) }
                    if (submitted is Submitted.Sent) read(showLoading = false)
                }
                .onFailure(::windowRefused)
        }
    }

    /**
     * The window says why: by its fields where the API named them, a change of an entry naming the
     * amount by the word alone, and in words of its own where it named none the window has.
     */
    private fun windowRefused(e: Throwable) {
        val fields = EntryField.entries.map { it.path }
        val refused = (e as? ApiException)?.fieldRefusals.orEmpty()
            .mapKeys { (path, _) -> if (path == "quantity") EntryField.QUANTITY.path else path }
            .filterKeys { it in fields }
        _state.update { it.copy(sending = false, refused = refused, windowError = e.takeIf { refused.isEmpty() }) }
    }

    // ── The window that changes an entry ────────────────────────────────────

    /** Opens the window that changes [entry]; one that waits to be written is changed once the server has it. */
    fun openEdit(entry: DiaryEntry) {
        if (entry.id in _state.value.waitingAdditions) return
        _state.update { it.copy(edit = EntryEdit(entry), showProblems = false, refused = emptyMap(), windowError = null) }
    }

    fun typeEdit(change: (EntryEdit) -> EntryEdit) = _state.update { state ->
        state.copy(edit = state.edit?.let(change), windowError = null, refused = emptyMap())
    }

    /** Changes the entry as the window says: the window goes, and a refusal leaves it as it is, to be put right. */
    fun saveEdit() {
        val current = _state.value
        val edit = current.edit ?: return
        val profileId = current.profileId ?: return
        if (current.sending) return
        if (edit.problems().isNotEmpty()) return _state.update { it.copy(showProblems = true) }
        if (!edit.changed) return closeWindow()
        _state.update { it.copy(sending = true, showProblems = true, refused = emptyMap(), windowError = null, failed = null, copied = null, resizeLeft = null) }
        viewModelScope.launch {
            attempt { diary.change(profileId, edit.entry.id, edit.newSlot, edit.newServings, edit.newQuantity) }
                .onSuccess { submitted ->
                    _state.update { it.copy(sending = false, edit = null, showProblems = false, offline = it.offline || (submitted as? Submitted.Kept)?.offline == true) }
                    if (submitted is Submitted.Sent) read(showLoading = false)
                }
                .onFailure(::windowRefused)
        }
    }

    // ── What is done on the day itself ──────────────────────────────────────

    /**
     * Takes [entry] out of the diary. One that waits to be written is taken out of the queue instead,
     * unless it was sent meanwhile: then the day is read again, with it there.
     */
    fun remove(entry: DiaryEntry) {
        val current = _state.value
        val profileId = current.profileId ?: return
        if (current.changing) return
        _state.update { it.copy(changing = true, failed = null, copied = null, resizeLeft = null) }
        val waiting = current.waitingAdditions[entry.id]
        if (waiting != null) {
            withdrawn += waiting
            viewModelScope.launch {
                val taken = queue.withdraw(waiting)
                _state.update { it.copy(changing = false) }
                if (!taken) {
                    withdrawn -= waiting
                    read(showLoading = false)
                }
            }
            return
        }
        viewModelScope.launch {
            attempt { diary.remove(profileId, entry.id) }
                .onSuccess { submitted ->
                    _state.update { it.copy(changing = false, offline = it.offline || (submitted as? Submitted.Kept)?.offline == true) }
                    if (submitted is Submitted.Sent) read(showLoading = false)
                }
                .onFailure { e ->
                    _state.update { it.copy(changing = false, failed = Failure.REMOVE to e) }
                    read(showLoading = false)
                }
        }
    }

    /** Marks a meal of the plan eaten or not, where the day has come; it rebalances its day, and says how, once the server has it. */
    fun setEaten(meal: DiaryPlanMeal) {
        val current = _state.value
        val planId = current.day?.plan?.id ?: return
        if (current.changing || current.ahead || meal.id in current.waitingOwnMeals) return
        _state.update { it.copy(changing = true, failed = null, copied = null, resizeLeft = null, rebalance = null, rebalancedPlan = null) }
        viewModelScope.launch {
            attempt { plans.setEaten(planId, meal.id, !meal.eaten) }
                .onSuccess { submitted ->
                    when (submitted) {
                        is Submitted.Sent -> rebalanced(planId, submitted.answer)
                        is Submitted.Kept -> _state.update { it.copy(changing = false, offline = it.offline || submitted.offline) }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(changing = false, failed = Failure.EATEN to e) }
                    read(showLoading = false)
                }
        }
    }

    /** Gives the plan's uneaten meals other portions, so that the day comes as close to its target as it can. A press that moves nothing says so. */
    fun resize() {
        val current = _state.value
        val planId = current.day?.plan?.id ?: return
        if (current.changing) return
        ask(planId, resizing = true) { plans.resizeDay(planId, current.date.toString()) }
    }

    /** Puts the meals the last change scaled back as they were. */
    fun undoRebalance() {
        val current = _state.value
        val rebalance = current.rebalance ?: return
        val planId = current.rebalancedPlan ?: return
        if (current.changing) return
        ask(planId, said = false) { plans.undo(planId, rebalance) }
    }

    /** Spreads what the days from today are off their targets by over the remaining days of the plan. */
    fun spread() {
        val current = _state.value
        val planId = current.rebalancedPlan ?: return
        if (current.changing) return
        ask(planId) { plans.rebalanceRest(planId) }
    }

    fun dismissRebalance() = _state.update { it.copy(rebalance = null, rebalancedPlan = null) }

    /** A change of the plan that asks the server, one at a time: the day is read again, and what it rebalanced said where [said]. */
    private fun ask(planId: String, said: Boolean = true, resizing: Boolean = false, block: suspend () -> Rebalanced) {
        _state.update { it.copy(changing = true, failed = null, copied = null, resizeLeft = null, rebalance = null, rebalancedPlan = null) }
        viewModelScope.launch {
            attempt { block() }
                .onSuccess { rebalanced(planId, it, said, resizing) }
                .onFailure { e ->
                    _state.update { it.copy(changing = false, failed = Failure.REBALANCE to e) }
                    read(showLoading = false)
                }
        }
    }

    /** What a change of the plan left: said where it scaled a meal; a press to resize that moved nothing says what is left. */
    private fun rebalanced(planId: String, answer: Rebalanced, said: Boolean = true, resizing: Boolean = false) {
        val rebalance = answer.rebalance
        val moved = rebalance?.takeIf { said && it.changes.isNotEmpty() }
        _state.update {
            it.copy(
                changing = false,
                rebalance = moved,
                rebalancedPlan = planId.takeIf { moved != null },
                resizeLeft = rebalance?.takeIf { resizing && it.changes.isEmpty() }?.unabsorbedKcal,
            )
        }
        read(showLoading = false)
    }

    // ── Copying a day ───────────────────────────────────────────────────────

    fun chooseCopyFrom(day: LocalDate) = _state.update { it.copy(copyFrom = day) }

    /** Copies what was eaten on the chosen day into the day shown; what came here is not copied again by a second press. */
    fun copy() {
        val current = _state.value
        val profileId = current.profileId ?: return
        val from = current.copyFrom
        if (current.copying || from == current.date || (current.copied?.from == from && current.copied.count > 0)) return
        _state.update { it.copy(copying = true, failed = null, copied = null, resizeLeft = null, rebalance = null, rebalancedPlan = null) }
        viewModelScope.launch {
            attempt { diary.copy(profileId, from, current.date) }
                .onSuccess { copied ->
                    _state.update { it.copy(copying = false, day = copied.day, copied = Copied(from, copied.copied), readAt = System.currentTimeMillis(), offline = false) }
                }
                .onFailure { e -> _state.update { it.copy(copying = false, failed = Failure.COPY to e) } }
        }
    }

    companion object {
        /** How many of what a search finds are offered, as many as the web's window offers. */
        const val OFFERED = 8
    }
}

/** The day [this] names, one a request may name; null for any other. */
private fun String?.toDiaryDay(): LocalDate? {
    val day = try {
        this?.let(LocalDate::parse)
    } catch (e: DateTimeParseException) {
        null
    }
    return day?.takeIf { it >= FIRST_DIARY_DAY && it <= LAST_DIARY_DAY }
}
