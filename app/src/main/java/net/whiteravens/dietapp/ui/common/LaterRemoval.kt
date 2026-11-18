// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long a removal can be taken back before it is sent, unless the phone's accessibility settings ask for longer. */
const val UNDO_FOR_MILLIS = 6_000L

/**
 * How long a removal that is done waits for its screen to stop showing what it removed, as the server or the queue of
 * changes has it, before it lets it show again: the queue says what waits within a moment.
 */
const val GONE_WITHIN_MILLIS = 2_000L

/**
 * A removal of something cheap to make again (an entry of the diary, a row of the pantry, a recipe's mark): gone from
 * the screen at once, and sent only once the moments to take it back are over. Nothing is asked first; a removal
 * taken back was never sent. A second removal sends the first at once, and a screen's model that is cleared sends
 * what still waits ([take]).
 *
 * Each removal is given to [send], which returns once it is done: the screen's model sends it once nothing else is
 * being changed. What is removed stays out of sight ([hidden]) until then: it shows again only where the removal
 * failed and the screen reads it again.
 */
class LaterRemoval<T>(
    private val scope: CoroutineScope,
    private val idOf: (T) -> Any,
    private val send: suspend (T) -> Unit,
) {
    private val held = MutableStateFlow<T?>(null)

    /** What was removed a moment ago and can still be taken back. */
    val waiting: StateFlow<T?> = held.asStateFlow()

    private val out = MutableStateFlow<Set<Any>>(emptySet())

    /** What is out of sight: removed a moment ago, or sent and not done yet. */
    val hidden: StateFlow<Set<Any>> = out.asStateFlow()

    private var timer: Job? = null

    fun remove(item: T, after: Long = UNDO_FOR_MILLIS) {
        sendNow()
        held.value = item
        out.update { it + idOf(item) }
        timer = scope.launch {
            delay(after)
            timer = null
            sendNow()
        }
    }

    /** Removes [item] with no moments to take it back, once what was removed before it is sent. */
    fun removeAtOnce(item: T) {
        sendNow()
        dispatch(item)
    }

    /** Takes the removal back: nothing is sent. */
    fun undo() {
        timer?.cancel()
        timer = null
        val item = held.value ?: return
        held.value = null
        out.update { it - idOf(item) }
    }

    /** Sends what waits, at once. */
    fun sendNow() {
        timer?.cancel()
        timer = null
        val item = held.value ?: return
        held.value = null
        dispatch(item)
    }

    private fun dispatch(item: T) {
        out.update { it + idOf(item) }
        scope.launch {
            try {
                send(item)
            } finally {
                out.update { it - idOf(item) }
            }
        }
    }

    /** What waits, taken out without being sent: for a model that is cleared and sends it another way. */
    fun take(): T? {
        timer?.cancel()
        timer = null
        val item = held.value ?: return null
        held.value = null
        out.update { it - idOf(item) }
        return item
    }
}
