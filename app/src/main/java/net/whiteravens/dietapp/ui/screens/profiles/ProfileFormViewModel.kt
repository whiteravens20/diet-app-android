// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.ProfileRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.CalorieQuestion
import net.whiteravens.dietapp.domain.CaloriePreview
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.Profile
import net.whiteravens.dietapp.domain.ProfileDetails
import net.whiteravens.dietapp.ui.common.attempt
import java.util.Locale
import javax.inject.Inject

/**
 * A profile's form: a new profile, one kept ([PROFILE_ID] in the route), or the first profile of an
 * account, set up in three steps ([STEPS]). What the typed figures come to is asked of the API once
 * typing pauses and they are ones it would take, and each pace of loss says what it comes to in a
 * day; nothing is kept before the form is saved.
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
    ) {
        /**
         * Why losing weight is a risk for the body the form describes, as the API's last answer for
         * the form's figures says it; none when no loss is asked for, and none before an answer.
         */
        val risks: List<String>
            get() = if (form.question(unit) == null) emptyList() else answer?.calculation?.lossRisks.orEmpty()

        val question: CalorieQuestion?
            get() = form.question(unit, riskShown = risks.isNotEmpty())

        /** What the form's figures come to: none while they cannot be worked out, rather than what earlier ones came to. */
        val preview: CaloriePreview?
            get() = if (question == null) null else answer

        /** What is shown belongs to figures typed a moment ago. */
        val behind: Boolean
            get() = asking || question != answered

        /** The figures as they are could not be worked out. */
        val previewFailed: Boolean
            get() = question != null && question == failed

        /** There are figures to work out, and no answer to show yet. */
        val waiting: Boolean
            get() = question != null && preview == null && !previewFailed

        val problems: Set<ProfileField>
            get() = form.problems(unit)
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
                        _state.update { it.copy(loading = false, unit = unit, form = ProfileForm.of(details, unit), editing = details.figures.name) }
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
        _state.update { it.copy(form = change(it.form), error = null) }
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
                        _state.update { it.copy(answer = known, answered = question, asking = false, failed = null) }
                        return@collectLatest
                    }
                    _state.update { it.copy(asking = true) }
                    attempt { profiles.preview(question) }
                        .onSuccess { answer ->
                            answers[question] = answer
                            _state.update { it.copy(answer = answer, answered = question, asking = false, failed = null) }
                        }
                        .onFailure { _state.update { it.copy(answer = null, answered = null, asking = false, failed = question) } }
                }
        }
    }

    /** Saves the form: a new profile, or the kept one as it is from now on, its preferences as they were. */
    fun save() {
        val current = _state.value
        if (current.saving) return
        val figures = current.form.figures(current.unit)
        val limits = current.form.planLimits()
        if (figures == null || limits == null) {
            _state.update { it.copy(showProblems = true) }
            return
        }
        // Said only by a form that showed the risk: ticked is yes, not ticked is no.
        val acknowledged = current.form.acknowledgeRisk.takeIf { current.risks.isNotEmpty() }
        send {
            val before = kept
            if (before == null) profiles.create(figures, limits, acknowledged)
            else profiles.update(before.id, figures, before.preferences.with(limits), acknowledged)
        }
    }

    /** The first profile's next step, once the fields of this one hold what the API would take. */
    fun next() {
        val current = _state.value
        if (current.step >= STEPS.lastIndex) return
        if (current.problems.any { it in STEPS[current.step] }) {
            _state.update { it.copy(showProblems = true) }
            return
        }
        _state.update { it.copy(step = it.step + 1, showProblems = false, error = null) }
    }

    fun back() {
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), showProblems = false, error = null) }
    }

    /**
     * Makes the first profile, on the last step: with the allergens chosen there, and every other
     * preference the API's own. A step behind with a field the API would refuse is shown again.
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
        val acknowledged = current.form.acknowledgeRisk.takeIf { current.risks.isNotEmpty() }
        send { profiles.create(figures, limits = null, acknowledgeRisk = acknowledged) }
    }

    private fun send(request: suspend () -> Profile) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            attempt { request() }
                .onSuccess { saved -> _state.update { it.copy(saving = false, saved = saved) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e) } }
        }
    }

    companion object {
        /** The route's argument naming the profile edited; absent for a new one. */
        const val PROFILE_ID = "profileId"

        /** How long typing pauses before the figures are asked about. */
        const val PREVIEW_DELAY_MS = 400L

        /** How many answers the form remembers. */
        private const val ANSWERS_KEPT = 32

        /** The first profile's steps, in order, by the fields each of them shows: the body, the goal, the food. */
        val STEPS: List<Set<ProfileField>> = listOf(
            setOf(ProfileField.NAME, ProfileField.AGE, ProfileField.HEIGHT, ProfileField.WEIGHT),
            setOf(ProfileField.TARGET_WEIGHT, ProfileField.OWN_TARGET),
            setOf(ProfileField.SPLIT),
        )
    }
}
