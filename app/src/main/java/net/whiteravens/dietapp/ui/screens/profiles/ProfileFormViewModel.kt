// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.CalorieQuestion
import net.whiteravens.dietapp.domain.CaloriePreview
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.LabelLimits
import net.whiteravens.dietapp.domain.PlanLimits
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.domain.ProfileFigures
import net.whiteravens.dietapp.ui.common.attempt
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * A profile's form: a new profile, one kept ([PROFILE_ID] in the route), or the first profile of an
 * account, set up in three steps ([STEPS]). What the typed figures come to is asked of the API once
 * typing pauses and they are numbers, and each pace of loss says what it comes to in a day; a field
 * the API refuses, there or on saving, is marked with its reason until it changes. Nothing is kept
 * before the form is saved.
 */
@HiltViewModel
class ProfileFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val users: UserRepository,
    server: ServerRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val form: ProfileForm = ProfileForm(),
        val unit: WeightUnit = WeightUnit.of(null, Locale.getDefault()),
        /** The answer last given, and the question it answers. */
        val answer: CaloriePreview? = null,
        val answered: CalorieQuestion? = null,
        val asking: Boolean = false,
        /** The question whose answer failed. */
        val failed: CalorieQuestion? = null,
        /** The question the API refused for its fields, which [refusals] name. */
        val refusedQuestion: CalorieQuestion? = null,
        /** The fields the API refused, in the last answer about them, until each is changed. */
        val refusals: ProfileRefusals = ProfileRefusals(),
        /** The fields say what is wrong with them once going on or saving has been refused for it. */
        val showProblems: Boolean = false,
        /** The step of the first profile's three that is shown. */
        val step: Int = 0,
        val saving: Boolean = false,
        val error: Throwable? = null,
        /** The profile as saved: the form is done. */
        val saved: Profile? = null,
        /** The name of the profile edited, as it is kept; null for a new one. */
        val editing: String? = null,
        /** The id of the profile edited, and the correction of its target it keeps, kcal a day. */
        val editingId: String? = null,
        val correction: Int? = null,
        /** What the form of a kept profile says of its changes, each kept as it is made. */
        val autosave: Autosave = Autosave.IDLE,
        /** Leaving was asked while a change is not kept: the form asks whether to leave it behind. */
        val askLeaving: Boolean = false,
        /** The form of a kept profile is left, every change kept or left behind. */
        val left: Boolean = false,
    ) {
        /**
         * Why losing weight is a risk for the body the form describes, as the API's last answer for
         * the form's figures says it; none when no loss is asked for, and none before an answer.
         */
        val risks: List<String>
            get() = if (form.question(unit) == null) emptyList() else answer?.calculation?.lossRisks.orEmpty()

        val question: CalorieQuestion?
            get() = form.question(unit)

        /** What the form's figures come to: none while they cannot be worked out, rather than what earlier ones came to. */
        val preview: CaloriePreview?
            get() = if (question == null) null else answer

        /** What is shown belongs to figures typed a moment ago. */
        val behind: Boolean
            get() = asking || question != answered

        /** The figures as they are could not be worked out. */
        val previewFailed: Boolean
            get() = question != null && question == failed

        /** The API refused the figures as they are: the fields say why. */
        val previewRefused: Boolean
            get() = question != null && question == refusedQuestion

        /** The day's target the custom diet's grams are worked out at: the last the API said, while new figures are asked about. */
        val ownTarget: Int?
            get() = answer?.calculation?.dailyTarget?.roundToInt()

        /** There are figures to work out, and no answer to show yet. */
        val waiting: Boolean
            get() = question != null && preview == null && !previewFailed && !previewRefused

        /** The fields that hold no number where one is needed. */
        val problems: Set<ProfileField>
            get() = form.problems(unit)

        /** What the fields say is wrong with them: no number, once going on or saving was refused for it, and what the API refused. */
        val marks: ProfileMarks
            get() = ProfileMarks(
                notNumbers = if (showProblems) problems else emptySet(),
                kindNotNumbers = if (showProblems) form.kindProblems() else emptyMap(),
                refusals = refusals,
            )
    }

    private val profileId: String? = savedStateHandle[PROFILE_ID]
    private var kept: ProfileDetails? = null

    /** The answers given for this form's figures: going back to figures asked about before asks nothing. */
    private val answers = object : LinkedHashMap<CalorieQuestion, CaloriePreview>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CalorieQuestion, CaloriePreview>) = size > ANSWERS_KEPT
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** What the instance's catalogue holds, for the marks on the diets; null until it has said. */
    val rules: StateFlow<InstanceRules?> = server.rules

    init {
        load()
        if (server.rules.value == null) viewModelScope.launch { server.refresh() }
        previews()
    }

    /** Reads the account's unit and, for a profile edited, the profile as it is kept. */
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // Weights are shown and typed in the account's unit, and until it has chosen one, in that of where the phone is.
            val unit = attempt { users.me() }.getOrNull()?.let { WeightUnit.of(it.weightUnit, Locale.getDefault()) }
                ?: _state.value.unit
            val id = profileId
            if (id == null) {
                _state.update { it.copy(loading = false, unit = unit) }
            } else {
                attempt { profiles.profile(id) }
                    .onSuccess { details ->
                        kept = details
                        _state.update {
                            it.copy(
                                loading = false,
                                unit = unit,
                                form = ProfileForm.of(details, unit),
                                editing = details.figures.name,
                                editingId = details.id,
                                correction = details.expenditureCorrectionKcal,
                            )
                        }
                    }
                    .onFailure { e -> _state.update { it.copy(loading = false, unit = unit, error = e) } }
            }
        }
    }

    /** The first profile's steps begin again with nobody's figures, once the profile they made is done with. */
    fun startOver() {
        _state.update { UiState(loading = false, unit = it.unit) }
    }

    fun edit(change: (ProfileForm) -> ProfileForm) {
        val before = _state.value.form
        _state.update {
            val now = change(it.form)
            it.copy(form = now, error = null, refusals = it.refusals.after(it.form, now))
        }
        // The form of a kept profile keeps a choice the moment it is made; a field typed into, once it is left.
        if (kept != null && before.choseOtherwise(_state.value.form)) keep()
    }

    /** The changes of the form of a kept profile, kept one after another, the newest on its way last. */
    private var keeping: Job? = null

    /** Sends the form of a kept profile again a while after a change met no network, while it is open. */
    private var again: Job? = null

    /**
     * Keeps what the form of a kept profile holds that the profile does not have yet, a part at a time, so that a
     * part the API refuses holds no other back: a field left, a choice made, the form left. A field that holds what
     * cannot be kept says so, and the rest is kept. Nothing for a new profile, which is made as a whole.
     */
    fun keep() {
        if (kept == null) return
        val before = keeping
        keeping = viewModelScope.launch {
            before?.join()
            keepNow()
        }
    }

    private suspend fun keepNow() {
        val details = kept ?: return
        val now = _state.value
        val refused = now.refusals.fields.keys
        // Protein per kilogram is judged at the target of the figures as they are, once the API has said it.
        val target = now.preview?.calculation?.dailyTarget?.roundToInt()
        val figures = now.form.keepable(details.figures, now.unit, refused, target)
        val limits = now.form.keepableLimits(details.preferences.limits, refused)
        val labels = now.form.keepableLabels(details.preferences.labelLimits, refused)
        val own = partsChanged(details.figures, figures)
        val preferences = limitPartsChanged(details.preferences.limits, limits, details.preferences.labelLimits, labels)
        val held = now.form.notKept(now.unit, target).isNotEmpty() || now.form.kindProblems().isNotEmpty()
        if (own.isEmpty() && preferences.isEmpty()) {
            // Nothing differs from the profile: what the form holds is kept, a value put back as it was among it.
            _state.update {
                val left = held || it.refusals.fields.isNotEmpty() || it.refusals.kinds.isNotEmpty()
                val status = when {
                    left -> Autosave.NOT_SAVED
                    it.autosave == Autosave.IDLE -> Autosave.IDLE
                    else -> Autosave.SAVED
                }
                it.copy(showProblems = it.showProblems || held, autosave = status)
            }
            return
        }
        _state.update { it.copy(autosave = Autosave.SAVING) }
        var offline = false
        var refusedOne = false
        /** Sends one part; what the API refuses of it is marked at its fields, and a lost network stops the rest. */
        suspend fun send(fields: Set<ProfileField>, preferencesRoute: Boolean, call: suspend () -> ProfileDetails) {
            attempt { call() }
                .onSuccess { answer ->
                    kept = answer
                    _state.update {
                        it.copy(
                            form = it.form.keptFrom(answer),
                            editing = answer.figures.name,
                            correction = answer.expenditureCorrectionKcal,
                            refusals = it.refusals.copy(fields = it.refusals.fields - fields),
                        )
                    }
                }
                .onFailure { e ->
                    if (e is IOException) {
                        offline = true
                        return@onFailure
                    }
                    refusedOne = true
                    val named = (e as? ApiException)?.fieldRefusals.orEmpty()
                        .mapKeys { (path, _) -> if (preferencesRoute) "preferences.$path" else path }
                    _state.update {
                        val answer = ProfileRefusals.of(named, it.form)
                        it.copy(
                            refusals = it.refusals.copy(fields = it.refusals.fields + answer.fields, kinds = it.refusals.kinds + answer.kinds),
                            error = e.takeIf { named.isEmpty() || answer.elsewhere.isNotEmpty() },
                        )
                    }
                }
        }
        for (part in own) {
            if (offline) break
            send(part.flatMap { FIELDS_OF_PARTS[it].orEmpty() }.toSet(), preferencesRoute = false) { profiles.change(details.id, figures, part) }
        }
        for (part in preferences) {
            if (offline) break
            send(FIELDS_OF_PARTS[part].orEmpty(), preferencesRoute = true) { profiles.changeLimits(details.id, limits, labels, setOf(part)) }
        }
        _state.update {
            it.copy(
                showProblems = it.showProblems || held,
                autosave = when {
                    offline -> Autosave.OFFLINE
                    refusedOne || held -> Autosave.NOT_SAVED
                    else -> Autosave.SAVED
                },
            )
        }
        if (offline) sendAgainSoon()
    }

    /** While the form is open and a change met no network, it is sent again every little while until it goes. */
    private fun sendAgainSoon() {
        if (again?.isActive == true) return
        again = viewModelScope.launch {
            while (_state.value.autosave == Autosave.OFFLINE) {
                delay(SEND_AGAIN_MS)
                keep()
                keeping?.join()
            }
        }
    }

    /**
     * Leaves the form of a kept profile once every change on its way is kept; where one is not, the form asks whether
     * to leave it behind ([stay], [leaveBehind]).
     */
    fun leave() {
        keep()
        viewModelScope.launch {
            keeping?.join()
            val now = _state.value.autosave
            _state.update { if (now == Autosave.OFFLINE || now == Autosave.NOT_SAVED) it.copy(askLeaving = true) else it.copy(left = true) }
        }
    }

    fun stay() {
        _state.update { it.copy(askLeaving = false) }
    }

    fun leaveBehind() {
        again?.cancel()
        _state.update { it.copy(askLeaving = false, left = true) }
    }

    @OptIn(FlowPreview::class)
    private fun previews() {
        viewModelScope.launch {
            state.map { it.question }
                .distinctUntilChanged()
                .debounce(PREVIEW_DELAY_MS)
                .collectLatest { question ->
                    if (question == null || question == _state.value.answered) return@collectLatest
                    answers[question]?.let { known ->
                        _state.update {
                            it.copy(
                                answer = known,
                                answered = question,
                                asking = false,
                                failed = null,
                                refusedQuestion = null,
                                refusals = it.refusals.with(ProfileRefusals(), ProfileField.QUESTION),
                            )
                        }
                        return@collectLatest
                    }
                    _state.update { it.copy(asking = true) }
                    attempt { profiles.preview(question) }
                        .onSuccess { answer ->
                            answers[question] = answer
                            _state.update {
                                it.copy(
                                    answer = answer,
                                    answered = question,
                                    asking = false,
                                    failed = null,
                                    refusedQuestion = null,
                                    refusals = it.refusals.with(ProfileRefusals(), ProfileField.QUESTION),
                                )
                            }
                        }
                        .onFailure { e ->
                            val refused = (e as? ApiException)?.fieldRefusals.orEmpty()
                            _state.update {
                                if (refused.isEmpty()) {
                                    it.copy(answer = null, answered = null, asking = false, failed = question)
                                } else {
                                    // The fields the figures are worked out from say why, as they are typed.
                                    it.copy(
                                        answer = null,
                                        answered = null,
                                        asking = false,
                                        refusedQuestion = question,
                                        refusals = it.refusals.with(ProfileRefusals.of(refused, it.form), ProfileField.QUESTION),
                                    )
                                }
                            }
                        }
                }
        }
    }

    /** Makes the profile the form holds; a kept profile keeps each change as it is made ([keep]). */
    fun save() {
        val current = _state.value
        if (current.saving) return
        val figures = current.form.figures(current.unit)
        val limits = current.form.planLimits()
        val labels = current.form.labelLimits()
        if (figures == null || limits == null || labels == null) {
            _state.update { it.copy(showProblems = true) }
            return
        }
        send(current.form) { profiles.create(figures, limits, labels) }
    }

    /** The first profile's next step, once the fields of this one hold numbers and none the API refused. */
    fun next() {
        val current = _state.value
        if (current.step >= STEPS.lastIndex) return
        if (current.problems.any { it in STEPS[current.step] }) {
            _state.update { it.copy(showProblems = true) }
            return
        }
        if (current.refusals.fields.keys.any { it in STEPS[current.step] }) return
        _state.update { it.copy(step = it.step + 1, showProblems = false, error = null) }
    }

    fun back() {
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), showProblems = false, error = null) }
    }

    /**
     * Makes the first profile, on the last step: with the allergens chosen there, and every other
     * preference the API's own. A step behind with a field that holds no number, or that the API
     * refused, is shown again.
     */
    fun make() {
        val current = _state.value
        if (current.saving) return
        val behind = STEPS.indexOfFirst { fields -> current.problems.any { it in fields } }
        if (behind != -1) {
            _state.update { it.copy(step = behind, showProblems = true) }
            return
        }
        val figures = current.form.figures(current.unit) ?: return
        send(current.form) { profiles.create(figures, limits = null) }
    }

    /** Sends [form] as [request] asks; a field the API refuses is marked, on the step that shows it. */
    private fun send(form: ProfileForm, request: suspend () -> Profile) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            attempt { request() }
                .onSuccess { saved -> _state.update { it.copy(saving = false, saved = saved) } }
                .onFailure { e ->
                    val refused = (e as? ApiException)?.fieldRefusals.orEmpty()
                    val refusals = ProfileRefusals.of(refused, form)
                    _state.update {
                        if (refused.isEmpty()) {
                            it.copy(saving = false, error = e)
                        } else {
                            val step = STEPS.indexOfFirst { fields -> refusals.fields.keys.any { field -> field in fields } }
                            // What the form has no field for is said as well.
                            it.copy(
                                saving = false,
                                refusals = refusals,
                                error = e.takeIf { refusals.elsewhere.isNotEmpty() },
                                step = if (step != -1 && step < it.step) step else it.step,
                            )
                        }
                    }
                }
        }
    }

    companion object {
        /** The route's argument naming the profile edited; absent for a new one. */
        const val PROFILE_ID = "profileId"

        /** How long the form of a kept profile waits before it sends a change that met no network again. */
        const val SEND_AGAIN_MS = 5_000L

        /** The fields of the form each part of a profile is typed in, by the part's name in the API. */
        private val FIELDS_OF_PARTS: Map<String, Set<ProfileField>> = mapOf(
            "name" to setOf(ProfileField.NAME),
            "age" to setOf(ProfileField.AGE),
            "heightCm" to setOf(ProfileField.HEIGHT),
            "weightKg" to setOf(ProfileField.WEIGHT),
            "targetWeightKg" to setOf(ProfileField.TARGET_WEIGHT),
            "waterTargetMl" to setOf(ProfileField.WATER_TARGET),
            "manualCalorieTarget" to setOf(ProfileField.OWN_TARGET),
            "customMacroSplit" to setOf(
                ProfileField.SPLIT,
                ProfileField.SPLIT_PROTEIN,
                ProfileField.SPLIT_FAT,
                ProfileField.SPLIT_CARBS,
                ProfileField.GRAMS_PROTEIN,
                ProfileField.GRAMS_FAT,
                ProfileField.GRAMS_CARBS,
            ),
            "customProteinPerKg" to setOf(ProfileField.PER_KG),
            "maxConsecutiveDaysSameMeal" to setOf(ProfileField.CONSECUTIVE_DAYS),
            "maxTimesPerWeekSameMeal" to setOf(ProfileField.TIMES_PER_WEEK),
            "maxMealsPerDaySameKind" to setOf(ProfileField.KIND_PER_DAY),
            "maxMealsPerWeekSameKind" to setOf(ProfileField.KIND_PER_WEEK),
            "inventoryBiasResetEvery" to setOf(ProfileField.PANTRY_COOL_DOWN),
            "labelLimits" to setOf(ProfileField.LABEL_SALT, ProfileField.LABEL_FIBER, ProfileField.LABEL_SATURATED_FAT, ProfileField.LABEL_SUGARS),
        )

        /**
         * The parts of a profile [after] has otherwise than [before], each to be sent alone: the custom diet with its
         * targets. Protein per kilogram goes with the split where either has it, so that an API that came before it
         * is never sent it, and a split named alone is the split's way.
         */
        internal fun partsChanged(before: ProfileFigures, after: ProfileFigures): List<Set<String>> = buildList {
            fun part(name: String, changed: Boolean) {
                if (changed) add(setOf(name))
            }
            part("name", before.name != after.name)
            part("age", before.age != after.age)
            part("sex", before.sex != after.sex)
            part("heightCm", before.heightCm != after.heightCm)
            part("weightKg", before.weightKg != after.weightKg)
            part("targetWeightKg", before.targetWeightKg != after.targetWeightKg)
            part("activityLevel", before.activityLevel != after.activityLevel)
            val toCustom = before.dietType != after.dietType && after.dietType == "custom"
            val targets = if (before.customProteinPerKg != null || after.customProteinPerKg != null) {
                setOf("customMacroSplit", "customProteinPerKg")
            } else {
                setOf("customMacroSplit")
            }
            if (toCustom) add(setOf("dietType") + targets) else part("dietType", before.dietType != after.dietType)
            val targetsChanged = before.customMacroSplit != after.customMacroSplit || before.customProteinPerKg != after.customProteinPerKg
            if (!toCustom && targetsChanged) add(targets)
            part("weeklyLossTarget", before.weeklyLossTarget != after.weeklyLossTarget)
            part("manualCalorieTarget", before.manualCalorieTarget != after.manualCalorieTarget)
            part("mealCount", before.mealCount != after.mealCount)
            part("weightReminderCadence", before.weightReminderCadence != after.weightReminderCadence)
            part("waterTracking", before.waterTracking != after.waterTracking)
            part("waterTargetMl", before.waterTargetMl != after.waterTargetMl)
            part("cookingReminders", before.cookingReminders != after.cookingReminders)
            part("waterReminders", before.waterReminders != after.waterReminders)
        }

        /** The limits of a profile's plans [after] has otherwise than [before], each to be sent alone, by its name in the API. */
        internal fun limitPartsChanged(before: PlanLimits, after: PlanLimits, labelsBefore: LabelLimits, labelsAfter: LabelLimits): List<String> =
            buildList {
                if (before.maxConsecutiveDaysSameMeal != after.maxConsecutiveDaysSameMeal) add("maxConsecutiveDaysSameMeal")
                if (before.maxTimesPerWeekSameMeal != after.maxTimesPerWeekSameMeal) add("maxTimesPerWeekSameMeal")
                if (before.maxMealsPerDaySameKind != after.maxMealsPerDaySameKind) add("maxMealsPerDaySameKind")
                if (before.maxMealsPerWeekSameKind != after.maxMealsPerWeekSameKind) add("maxMealsPerWeekSameKind")
                if (before.kindLimits != after.kindLimits) add("kindLimits")
                if (before.inventoryBiasResetEvery != after.inventoryBiasResetEvery) add("inventoryBiasResetEvery")
                if (labelsBefore != labelsAfter) add("labelLimits")
            }

        /** How long typing pauses before the figures are asked about. */
        const val PREVIEW_DELAY_MS = 400L

        /** How many answers the form remembers. */
        private const val ANSWERS_KEPT = 32

        /** The first profile's steps, in order, by the fields each of them shows: the body, the goal, the food. */
        val STEPS: List<Set<ProfileField>> = listOf(
            setOf(ProfileField.NAME, ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT),
            setOf(ProfileField.TARGET_WEIGHT, ProfileField.OWN_TARGET),
            ProfileField.OWN,
        )
    }
}

/** What the form of a kept profile says of its changes: none made yet, one on its way, all kept, one not kept, or one that met no network. */
enum class Autosave { IDLE, SAVING, SAVED, NOT_SAVED, OFFLINE }
