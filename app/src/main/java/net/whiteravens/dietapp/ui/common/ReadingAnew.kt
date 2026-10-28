// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * When a screen shown again reads anew what it shows. Shown again after another screen of the
 * application, it reads at once, since that screen may have changed what this one shows. Shown again
 * because the application came back to the front, it reads only when its last read began at least
 * [AT_MOST_EVERY_MILLIS] before, so that switching between applications does not spend the API's
 * limit of requests. Opening a screen and pulling it down read whenever they are asked.
 */
class ReadingAnew(private val now: () -> Long = System::currentTimeMillis) {
    private var lastBegun: Long? = null

    /** A read of what the screen shows begins, whatever asked for it. */
    fun begun() {
        lastBegun = now()
    }

    /** Whether the screen, shown again, reads anew now: [cameToTheFront] where the application had been away. */
    fun due(cameToTheFront: Boolean): Boolean {
        val begun = lastBegun ?: return true
        return !cameToTheFront || now() - begun >= AT_MOST_EVERY_MILLIS
    }

    companion object {
        const val AT_MOST_EVERY_MILLIS = 30_000L
    }
}

/**
 * How many times the application has come back to the front: its window started again after it had
 * been stopped, by another application, the home screen, the screen turned off or the phone turned.
 * The activity counts it; the screens compare it with what it was when they were hidden.
 */
object Returns {
    var count: Int = 0
        private set

    private var stopped = false

    fun stopped() {
        stopped = true
    }

    fun started() {
        if (stopped) {
            stopped = false
            count++
        }
    }
}

/**
 * Calls [shownAgain] each time the screen is shown again after it was hidden, saying whether the
 * application had been away from the front meanwhile. Not when the screen is first shown: opening it
 * reads already.
 */
@Composable
fun WhenShownAgain(shownAgain: (cameToTheFront: Boolean) -> Unit) {
    var returnsWhenHidden by rememberSaveable { mutableIntStateOf(NEVER_HIDDEN) }
    val current by rememberUpdatedState(shownAgain)
    LifecycleResumeEffect(Unit) {
        if (returnsWhenHidden != NEVER_HIDDEN) current(Returns.count != returnsWhenHidden)
        onPauseOrDispose { returnsWhenHidden = Returns.count }
    }
}

private const val NEVER_HIDDEN = -1
