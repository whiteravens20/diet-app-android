// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.whiteravens.dietapp.data.repository.PantryRepository
import net.whiteravens.dietapp.domain.EatenOf
import net.whiteravens.dietapp.domain.EatenPantry

/** What the window asks: to take what was eaten, to give back what was taken, or that before an entry is removed. */
enum class PantryQuestion { TAKE, GIVE_BACK, REMOVE }

/**
 * The window of what eating does to the pantry: its question, the meal or the entry it asks about ([of]), each
 * person's pantry it names ([pantries]: one, or of a meal eaten by both [both] portions, this plan's first), and a
 * take or a giving back on its way or refused.
 */
data class PantryWindow(
    val question: PantryQuestion,
    val of: EatenOf,
    val pantries: List<EatenPantry>,
    val both: Boolean = false,
    val sending: Boolean = false,
    val error: Throwable? = null,
) {
    /** The food the window names of [pantry]: what taking it would take, or, to be given back, what it took. */
    fun foods(pantry: EatenPantry) = if (question == PantryQuestion.TAKE) pantry.offer else pantry.taken
}

/**
 * What eating a meal of a plan, or an entry of the diary, does to the pantry, asked once the server has the change: a
 * mark set or an entry written opens the window that offers to take what was eaten off the pantry, a mark taken back
 * the one that offers to give back what was taken, and the removal of an entry that took food asks first. A meal eaten
 * by both people of a joint plan is both portions, worked out together by the server, each from its own person's
 * pantry first and what that lacks from the other's, asked once and taken on one press. The window opens only where
 * the API says there is something to take or to give back: on a day a shopping list covers it says nothing, and where
 * the API cannot be asked nothing is asked and nothing changes. [changed] is told once the pantry moved.
 */
class PantryQuestions(
    private val scope: CoroutineScope,
    private val pantry: PantryRepository,
    private val changed: () -> Unit = {},
) {
    private val _window = MutableStateFlow<PantryWindow?>(null)
    val window: StateFlow<PantryWindow?> = _window.asStateFlow()

    /** What removes the entry the window asks about, once what it took is given back or not. */
    private var remove: (() -> Unit)? = null

    /**
     * Asks of [of], one meal or entry, what [question] is about; with [remove], the entry's removal, which goes at once
     * where nothing is to be given back.
     */
    fun ask(of: EatenOf, question: PantryQuestion, remove: (() -> Unit)? = null) =
        open(of, question, both = false, remove) { listOf(pantry.eaten(of)) }

    /** Asks of [meal], of a joint plan and marked eaten by both, what taking both portions would take. */
    fun askBoth(meal: EatenOf.Meal) = open(meal, PantryQuestion.TAKE, both = true, remove = null) { pantry.eatenByBoth(meal) }

    private fun open(of: EatenOf, question: PantryQuestion, both: Boolean, remove: (() -> Unit)?, read: suspend () -> List<EatenPantry>) {
        scope.launch {
            val asked = PantryWindow(question, of, emptyList(), both)
            val pantries = attempt { read() }.getOrDefault(emptyList()).filter { asked.foods(it).isNotEmpty() }
            if (pantries.isEmpty()) {
                remove?.invoke()
                return@launch
            }
            this@PantryQuestions.remove = remove
            _window.value = asked.copy(pantries = pantries)
        }
    }

    /**
     * "Zdejmij ze spiżarni", "Oddaj do spiżarni", "Oddaj i usuń": once, a press again after a refusal taking only what
     * is left; the entry goes once its food is back.
     */
    fun confirm() {
        val window = _window.value ?: return
        if (window.sending) return
        _window.value = window.copy(sending = true, error = null)
        scope.launch {
            val of = window.of
            val done = attempt {
                when {
                    window.both && of is EatenOf.Meal -> pantry.takeEatenByBoth(of)
                    window.question == PantryQuestion.TAKE -> pantry.takeEaten(of)
                    else -> pantry.giveBackEaten(of)
                }
            }
            changed()
            done
                .onSuccess {
                    if (window.question == PantryQuestion.REMOVE) remove?.invoke()
                    close()
                }
                .onFailure { e -> _window.value = window.copy(sending = false, error = e) }
        }
    }

    /** "Usuń bez oddawania": the entry goes, what it took stays off the pantry. */
    fun removeOnly() {
        remove?.invoke()
        close()
    }

    /** The window put away: nothing is taken, given back or removed. */
    fun close() {
        _window.value = null
        remove = null
    }
}
