// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.unlock

import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockGateTest {
    private var clock = 0L
    private var canAsk = true
    private var voided = false
    private val gate = UnlockGate(now = { clock }, canAsk = { canAsk }, voided = { voided })

    private fun passed() {
        gate.prompted()
        gate.answered(PromptAnswer.Passed)
    }

    @Test
    fun `shows nothing until the setting is read, and nothing but the screens where the unlock is off`() {
        assertEquals(Gate.Reading, gate.state.value)

        gate.settingIs(false)

        assertEquals(Gate.Open, gate.state.value)
    }

    @Test
    fun `starts locked where it is on, asks once by itself, and opens at a prompt that passed`() {
        gate.settingIs(true)
        assertEquals(Gate.Locked(askNow = true), gate.state.value)

        gate.prompted()
        assertEquals(Gate.Locked(askNow = false), gate.state.value)

        gate.answered(PromptAnswer.Passed)
        assertEquals(Gate.Open, gate.state.value)
    }

    @Test
    fun `stays locked when the prompt is cancelled, until Odblokuj asks again`() {
        gate.settingIs(true)
        gate.prompted()

        gate.answered(PromptAnswer.NotPassed)
        assertEquals(Gate.Locked(askNow = false), gate.state.value)

        gate.askAgain()
        assertEquals(Gate.Locked(askNow = true), gate.state.value)
    }

    @Test
    fun `stays closed on the way out of Android 10, until the end of the session turns the setting off`() {
        gate.settingIs(true)
        gate.prompted()

        gate.answered(PromptAnswer.Password)
        assertEquals(Gate.Locked(askNow = false), gate.state.value)

        gate.settingIs(false)
        assertEquals(Gate.Open, gate.state.value)
    }

    @Test
    fun `locks again after half an hour in the background, and not after less`() {
        gate.settingIs(true)
        passed()

        gate.wentAway()
        clock += UnlockGate.AWAY_MILLIS - 1
        gate.cameBack()
        assertEquals(Gate.Open, gate.state.value)

        gate.wentAway()
        clock += UnlockGate.AWAY_MILLIS
        gate.cameBack()
        assertEquals(Gate.Locked(askNow = true), gate.state.value)
    }

    @Test
    fun `does not lock on a turn of the phone, nor where the unlock is off`() {
        gate.settingIs(true)
        passed()
        // The activity stopped and started again as the phone turns.
        gate.wentAway()
        gate.cameBack()
        assertEquals(Gate.Open, gate.state.value)

        gate.settingIs(false)
        gate.wentAway()
        clock += 10 * UnlockGate.AWAY_MILLIS
        gate.cameBack()
        assertEquals(Gate.Open, gate.state.value)
    }

    @Test
    fun `stays open when the unlock is turned on in the settings, after the prompt that turned it on`() {
        gate.settingIs(false)

        gate.settingIs(true)

        assertEquals(Gate.Open, gate.state.value)
    }

    @Test
    fun `offers only a sign-out where the phone cannot ask, and never opens there`() {
        canAsk = false
        gate.settingIs(true)
        assertEquals(Gate.CannotAsk, gate.state.value)

        gate.answered(PromptAnswer.Passed)
        assertEquals(Gate.CannotAsk, gate.state.value)

        canAsk = true
        gate.askAgain()
        assertEquals(Gate.Locked(askNow = true), gate.state.value)
    }

    @Test
    fun `ends the session where the fingerprints changed, before a prompt and after one, and says why until it is read`() {
        voided = true
        gate.settingIs(true)
        assertEquals(Gate.Voided, gate.state.value)

        // The end of the session turns the setting off; the reason stays on the screen.
        gate.settingIs(false)
        assertEquals(Gate.Voided, gate.state.value)
        gate.voidedSeen()
        assertEquals(Gate.Open, gate.state.value)
    }

    @Test
    fun `does not open at a prompt that passed once the fingerprints changed meanwhile`() {
        gate.settingIs(true)
        gate.prompted()
        voided = true

        gate.answered(PromptAnswer.Passed)

        assertEquals(Gate.Voided, gate.state.value)
    }

    @Test
    fun `ignores an answer nobody asked for`() {
        gate.settingIs(false)

        gate.answered(PromptAnswer.Passed)
        gate.answered(PromptAnswer.Password)

        assertEquals(Gate.Open, gate.state.value)
    }

    @Test
    fun `lets the water shortcut through only while it is open and would stay open on a return`() {
        assertEquals(false, gate.lets())
        gate.settingIs(true)
        assertEquals(false, gate.lets())

        passed()
        assertEquals(true, gate.lets())

        gate.wentAway()
        clock += UnlockGate.AWAY_MILLIS - 1
        assertEquals(true, gate.lets())
        clock += 1
        assertEquals(false, gate.lets())
    }

    @Test
    fun `lets everything through where the unlock is off, however long the application was away`() {
        gate.settingIs(false)
        gate.wentAway()
        clock += 10 * UnlockGate.AWAY_MILLIS

        assertEquals(true, gate.lets())
    }
}
