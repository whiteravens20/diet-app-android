// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.unlock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What stands in front of the application's screens. */
sealed interface Gate {
    /** The setting is not read yet: nothing is shown, neither the screens nor the lock. */
    data object Reading : Gate

    /** The unlock is off, or it was passed: the screens are shown. */
    data object Open : Gate

    /** The screens wait for an unlock; [askNow] where the prompt is to be shown by itself, once. */
    data class Locked(val askNow: Boolean) : Gate

    /** The phone cannot ask now (no fingerprint left, locked out, no screen lock): only a sign-out is offered. */
    data object CannotAsk : Gate

    /** The fingerprints of the phone changed since the unlock was turned on: the session ends here, and the reader is told why. */
    data object Voided : Gate
}

/** How a prompt ended. */
enum class PromptAnswer {
    Passed,

    /** Cancelled, failed too often, or closed by the phone: the application stays closed. */
    NotPassed,

    /** Android 10's way out: the account's password instead of a fingerprint. */
    Password,
}

/**
 * The gate in front of the application's screens while the unlock is on: locked when the process starts, and again
 * when the application comes back after [AWAY_MILLIS] or more in the background. What it knows lives in the process
 * alone: a process Android started again starts locked, and turning the phone does not lock. It never stands in front
 * of what runs in the background (the notifications, the queue of changes, the water button), which keep the session
 * under the key they always had.
 *
 * [canAsk] says whether the phone can show a prompt now, [voided] whether the key that watches the fingerprints was
 * voided by a change of them since the unlock was turned on. Both are asked each time the gate locks, so that it never
 * opens on a phone that cannot ask, and a changed set of fingerprints ends the session rather than let it through.
 */
class UnlockGate(
    private val now: () -> Long,
    private val canAsk: () -> Boolean,
    private val voided: () -> Boolean,
) {
    private val gate = MutableStateFlow<Gate>(Gate.Reading)
    val state: StateFlow<Gate> = gate.asStateFlow()

    private var on: Boolean? = null
    private var awaySince: Long? = null

    /** The setting, as read when the process starts and each time it changes. */
    fun settingIs(on: Boolean) {
        val first = this.on == null
        this.on = on
        when {
            // The ending of a voided session is said until the reader has read it, whatever the setting says meanwhile.
            gate.value == Gate.Voided -> Unit
            !on -> gate.value = Gate.Open
            // Turned on in the settings, after a prompt that passed: the screens stay.
            !first -> Unit
            else -> lock()
        }
    }

    /** The application went to the background. */
    fun wentAway() {
        awaySince = now()
    }

    /** The application came back to the front. */
    fun cameBack() {
        val since = awaySince ?: return
        awaySince = null
        if (on == true && gate.value == Gate.Open && now() - since >= AWAY_MILLIS) lock()
    }

    /**
     * Whether something that shows the account outside the screens (the water shortcut's message) may go ahead: the
     * gate is open, and would stay open if the application came back now. Unread or locked, it may not.
     */
    fun lets(): Boolean {
        if (gate.value != Gate.Open || on == null) return false
        val since = awaySince ?: return true
        return on == false || now() - since < AWAY_MILLIS
    }

    /** "Odblokuj" pressed on the locked screen. */
    fun askAgain() {
        if (gate.value is Gate.Locked || gate.value == Gate.CannotAsk) lock()
    }

    /** The prompt the gate asked for is on the screen: it is not asked for again until it ends. */
    fun prompted() {
        if ((gate.value as? Gate.Locked)?.askNow == true) gate.value = Gate.Locked(askNow = false)
    }

    /**
     * How the prompt ended. [Gate.Voided] and the way out of Android 10 end the session, which the caller does: the
     * gate stays closed meanwhile, and opens when the end of the session turns the setting off, its tokens gone first.
     */
    fun answered(answer: PromptAnswer) {
        if (gate.value !is Gate.Locked) return
        gate.value = when (answer) {
            PromptAnswer.Passed -> if (voided()) Gate.Voided else Gate.Open
            PromptAnswer.NotPassed, PromptAnswer.Password -> Gate.Locked(askNow = false)
        }
    }

    /** The reader read why the session ended: the sign-in follows. */
    fun voidedSeen() {
        if (gate.value == Gate.Voided) gate.value = Gate.Open
    }

    private fun lock() {
        gate.value = when {
            voided() -> Gate.Voided
            !canAsk() -> Gate.CannotAsk
            else -> Gate.Locked(askNow = true)
        }
    }

    companion object {
        /** How long the application may stay in the background before it asks again: half an hour, his decision. */
        const val AWAY_MILLIS = 30 * 60_000L
    }
}
