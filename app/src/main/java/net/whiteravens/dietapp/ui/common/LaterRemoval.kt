// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How long a removal can be taken back before it is sent, unless the phone's accessibility settings ask for longer. */
const val UNDO_FOR_MILLIS = 6_000L

/**
 * A removal of something cheap to make again (an entry of the diary, a row of the pantry, a recipe's mark): gone from
 * the screen at once, and sent only once the moments to take it back are over. Nothing is asked first; a removal
 * taken back was never sent. A second removal sends the first at once, and a screen's model that is cleared sends
 * what still waits ([take]).
 */
class LaterRemoval<T>(private val scope: CoroutineScope, private val send: (T) -> Unit) {
    private val held = MutableStateFlow<T?>(null)

    /** What was removed a moment ago and can still be taken back: not shown. */
    val waiting: StateFlow<T?> = held.asStateFlow()
    private var timer: Job? = null

    fun remove(item: T, after: Long = UNDO_FOR_MILLIS) {
        sendNow()
        held.value = item
        timer = scope.launch {
            delay(after)
            timer = null
            sendNow()
        }
    }

    /** Takes the removal back: nothing is sent. */
    fun undo() {
        timer?.cancel()
        timer = null
        held.value = null
    }

    /** Sends what waits, at once. */
    fun sendNow() {
        timer?.cancel()
        timer = null
        val item = held.value ?: return
        held.value = null
        send(item)
    }

    /** What waits, taken out without being sent: for a model that is cleared and sends it another way. */
    fun take(): T? {
        timer?.cancel()
        timer = null
        return held.value.also { held.value = null }
    }
}
