// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.MealPlanRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.DaySettings
import net.whiteravens.dietapp.domain.DaySettingsPreview
import net.whiteravens.dietapp.domain.FieldRefusal
import net.whiteravens.dietapp.domain.MealPlan
import net.whiteravens.dietapp.domain.PlanDay
import net.whiteravens.dietapp.domain.PlanWeek
import net.whiteravens.dietapp.domain.RelaxationNeeded
import net.whiteravens.dietapp.ui.common.attempt
import net.whiteravens.dietapp.ui.screens.profiles.WeightUnit
import java.util.Locale
import javax.inject.Inject

/**
 * The settings of a day of a running plan, as the web's window changes them: the day's kind, its
 * number of meals, a target of its own, a limit on cooking time and whether it uses up what the
 * pantry has to use up. What a change would do is the API's answer to the settings on the screen,
 * asked once the typing has paused, and a field the API refuses is marked with the reason it gives;
 * nothing changes until the settings are saved.
 */
@HiltViewModel
class DaySettingsViewModel @Inject constructor(
    private val plans: MealPlanRepository,
    private val users: UserRepository,
) : ViewModel() {

    data class UiState(
        val planId: String = "",
        val dayId: String = "",
        /** The day, `2026-11-06`: what evening its week out names it by. */
        val date: String = "",
        /** The week of the plan the day is in, as the plan said it stood when the window opened. */
        val week: PlanWeek? = null,
        /** The last save asked for evens the week out too: letting the limits give way asks the same. */
        val evenWeek: Boolean = false,
        /** The unit the pace of the week is said in: the account's, and until it is read, that of where the phone is. */
        val unit: WeightUnit = WeightUnit.of(null, Locale.getDefault()),
        /** The settings the window started from: what the kind goes back to when a skipped day is no longer skipped. */
        val start: DaySettings? = null,
        /** The kind as the window chooses it: `normal` | `training` | `rest` | `skip`. */
        val kind: String = "normal",
        val mealCount: Int = 3,
        /** A target of the day's own and the most minutes of cooking, as typed: empty for none. */
        val target: String = "",
        val cookTime: String = "",
        val useUpBy: Boolean = false,
        val preview: DaySettingsPreview? = null,
        /** The preview is for the settings on the screen; false while a newer one is on its way. */
        val current: Boolean = false,
        /** What is drawn is kept to this day, whatever else of its cookings there is. */
        val only: Boolean = false,
        /** Why the preview could not be had, where it was not for the fields the API refused. */
        val previewError: Throwable? = null,
        /** The fields the API refused, by the path it names them by (`calorieTarget`, `cookTimeBudgetMinutes`). */
        val refused: Map<String, FieldRefusal> = emptyMap(),
        val saving: Boolean = false,
        val error: Throwable? = null,
        /** The limits that stood in the way of a save, which the user may let give way. */
        val inTheWay: RelaxationNeeded? = null,
        /** The plan with the settings saved: the window is done. */
        val saved: MealPlan? = null,
        /** A save was refused as the plan had changed: it is to be read anew. */
        val stale: Boolean = false,
    ) {
        /** The settings on the screen, or null while a field holds what is no whole number. */
        val settings: DaySettings?
            get() {
                val start = start ?: return null
                if (targetNotWhole || cookTimeNotWhole) return null
                val own = target.typed()!!
                val minutes = cookTime.typed()!!
                return DaySettings(
                    dayType = if (kind == SKIP) start.dayType else kind,
                    skip = kind == SKIP,
                    mealCount = mealCount,
                    calorieTarget = own.value,
                    cookTimeMinutes = minutes.value,
                    useUpBy = useUpBy,
                )
            }

        /** Whether the typed target, or the typed minutes, is no whole number; an empty field is none. The API says what it takes. */
        val targetNotWhole: Boolean get() = target.typed() == null
        val cookTimeNotWhole: Boolean get() = cookTime.typed() == null

        /** The change alone leaves the week off the plan's target: it can be saved alone, or with the week evened out. */
        val twoWays: Boolean get() = current && preview?.unchanged == false && preview.week?.unevenAlone == true

        /** Nothing of the day changes and its week stands off its target: the week can be evened out as it is. */
        val evenAlone: Boolean get() = current && preview?.unchanged == true && week?.uneven == true
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var asking: Job? = null

    /** The window on [day] of [week], as its settings stand. */
    fun open(planId: String, day: PlanDay, week: PlanWeek? = null) {
        val settings = day.settings ?: return
        _state.value = UiState(
            planId = planId,
            dayId = day.id,
            date = day.date,
            week = week,
            start = settings,
            kind = if (settings.skip) SKIP else settings.dayType,
            mealCount = settings.mealCount,
            target = settings.calorieTarget?.toString().orEmpty(),
            cookTime = settings.cookTimeMinutes?.toString().orEmpty(),
            useUpBy = settings.useUpBy,
        )
        ask(pause = false)
        viewModelScope.launch {
            val unit = attempt { users.me() }.getOrNull()?.let { WeightUnit.of(it.weightUnit, Locale.getDefault()) } ?: return@launch
            _state.update { it.copy(unit = unit) }
        }
    }

    fun setKind(kind: String) = edit { it.copy(kind = kind) }
    fun setMealCount(count: Int) = edit { it.copy(mealCount = count) }
    fun setTarget(typed: String) = edit { it.copy(target = typed) }
    fun setCookTime(typed: String) = edit { it.copy(cookTime = typed) }
    fun setUseUpBy(on: Boolean) = edit { it.copy(useUpBy = on) }

    /** Keeps what is drawn for the day to this day: the other meals of its cookings stay as they are. */
    fun setOnly(on: Boolean) {
        _state.update { it.copy(only = on) }
    }

    /** Saves the settings on the plan as the preview had it; with [evenWeek], the week of the day is evened out with them. */
    fun save(evenWeek: Boolean = false) {
        _state.update { it.copy(evenWeek = evenWeek) }
        send(allowRelaxations = null)
    }

    /** Saves them again, letting the limits that stood in the way give way, the week evened out as the save asked. */
    fun letGiveWay() {
        val needed = _state.value.inTheWay ?: return
        // The refusal names them in the order in which they give way.
        send(allowRelaxations = needed.needs)
    }

    /** Evens out the week of the day as it is, on the plan at the preview's revision. */
    fun evenWeek() {
        val current = _state.value
        val preview = current.preview ?: return
        if (current.saving || !current.evenAlone) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, stale = false) }
            attempt { plans.evenWeek(current.planId, current.date, preview.revision) }
                .onSuccess { plan -> _state.update { it.copy(saving = false, saved = plan) } }
                .onFailure { e ->
                    val changed = e is ApiException && e.code == "PLAN_CHANGED"
                    _state.update { it.copy(saving = false, error = e, stale = changed) }
                    if (changed) ask(pause = false)
                }
        }
    }

    private fun edit(change: (UiState) -> UiState) {
        _state.update { change(it).copy(current = false, inTheWay = null, error = null, refused = emptyMap()) }
        ask(pause = true)
    }

    /** Asks what the settings on the screen would do, once the typing has paused; an answer to older settings is not shown as theirs. */
    private fun ask(pause: Boolean) {
        asking?.cancel()
        val asked = _state.value.settings ?: return
        val current = _state.value
        asking = viewModelScope.launch {
            if (pause) delay(PAUSE_MILLIS)
            attempt { plans.previewDaySettings(current.planId, current.dayId, asked) }
                .onSuccess { preview ->
                    _state.update { if (it.settings == asked) it.copy(preview = preview, current = true, previewError = null, refused = emptyMap()) else it }
                }
                .onFailure { e ->
                    val refused = (e as? ApiException)?.fieldRefusals.orEmpty()
                    _state.update { if (it.settings == asked) it.copy(previewError = e.takeIf { refused.isEmpty() }, refused = refused) else it }
                }
        }
    }

    private fun send(allowRelaxations: List<String>?) {
        val current = _state.value
        val settings = current.settings ?: return
        val preview = current.preview ?: return
        if (current.saving || !current.current || preview.unchanged) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, stale = false) }
            attempt {
                plans.applyDaySettings(
                    current.planId,
                    current.dayId,
                    settings,
                    preview.revision,
                    allowRelaxations,
                    together = !current.only && preview.cooking?.changes?.isNotEmpty() == true,
                    evenWeek = current.evenWeek,
                )
            }
                .onSuccess { plan -> _state.update { it.copy(saving = false, saved = plan) } }
                .onFailure { e ->
                    val needed = plans.relaxationNeeded(e)
                    // The preview was for the plan as it was: where the plan changed, both are read anew.
                    val changed = e is ApiException && e.code == "PLAN_CHANGED"
                    val refused = (e as? ApiException)?.fieldRefusals.orEmpty()
                    _state.update {
                        it.copy(saving = false, inTheWay = needed, error = e.takeIf { needed == null && refused.isEmpty() }, stale = changed, refused = refused)
                    }
                    if (changed) ask(pause = false)
                }
        }
    }

    companion object {
        const val SKIP = "skip"

        /** The kinds of day the window offers, in the order the web's does. */
        val KINDS = listOf("normal", "training", "rest", SKIP)

        /** The numbers of meals a day can have. */
        val MEAL_COUNTS = listOf(2, 3, 4, 5)

        /** How long the typing has to pause before the API is asked. */
        const val PAUSE_MILLIS = 300L
    }
}

/** A whole number as typed: an empty field is none ([Typed] of null), what is no whole number is null. */
private data class Typed(val value: Int?)

private fun String.typed(): Typed? = if (isBlank()) Typed(null) else trim().toIntOrNull()?.let { Typed(it) }
