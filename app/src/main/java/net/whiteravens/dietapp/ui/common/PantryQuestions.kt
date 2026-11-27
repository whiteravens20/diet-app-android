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

/** A meal's or an entry's eaten food and what it does to its own profile's pantry. */
data class EatenPortion(val of: EatenOf, val pantry: EatenPantry) {
    /** The food the window names: what taking it would take, or, to be given back, what it took. */
    fun foods(question: PantryQuestion) = if (question == PantryQuestion.TAKE) pantry.offer else pantry.taken
}

/** The window of what eating does to the pantry: its question, each portion, and a take or a giving back on its way or refused. */
data class PantryWindow(
    val question: PantryQuestion,
    val portions: List<EatenPortion>,
    val sending: Boolean = false,
    val error: Throwable? = null,
)

/**
 * What eating a meal of a plan, or an entry of the diary, does to the pantry, asked once the server has the change: a
 * mark set or an entry written opens the window that offers to take what was eaten off the pantry, a mark taken back
 * the one that offers to give back what was taken, and the removal of an entry that took food asks first. A meal eaten
 * by both people of a joint plan is two portions, each from its own person's pantry, taken on one press. The window
 * opens only where the API says there is something to take or to give back: on a day a shopping list covers it says
 * nothing, and where the API cannot be asked nothing is asked and nothing changes. [changed] is told once the pantry
 * moved.
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
     * Asks of [of] (one meal or entry, or each person's meal of a meal eaten by both) what [question] is about; with
     * [remove], the entry's removal, which goes at once where nothing is to be given back.
     */
    fun ask(of: List<EatenOf>, question: PantryQuestion, remove: (() -> Unit)? = null) {
        scope.launch {
            val portions = of.mapNotNull { one ->
                attempt { pantry.eaten(one) }.getOrNull()?.let { EatenPortion(one, it) }
            }.filter { it.foods(question).isNotEmpty() }
            if (portions.isEmpty()) {
                remove?.invoke()
                return@launch
            }
            this@PantryQuestions.remove = remove
            _window.value = PantryWindow(question, portions)
        }
    }

    /**
     * "Zdejmij ze spiżarni", "Oddaj do spiżarni", "Oddaj i usuń": each portion in turn, a portion taken already
     * offering nothing, so that a press again takes only what is left; the entry goes once its food is back.
     */
    fun confirm() {
        val window = _window.value ?: return
        if (window.sending) return
        _window.value = window.copy(sending = true, error = null)
        scope.launch {
            val done = attempt {
                for (portion in window.portions) {
                    if (window.question == PantryQuestion.TAKE) pantry.takeEaten(portion.of) else pantry.giveBackEaten(portion.of)
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
