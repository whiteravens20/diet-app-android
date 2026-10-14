// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttemptTest {
    @Test
    fun `a failure is the result`() = runTest {
        val failure = IllegalStateException("refused")

        assertEquals(failure, attempt<Unit> { throw failure }.exceptionOrNull())
        assertEquals(42, attempt { 42 }.getOrNull())
    }

    @Test
    fun `a coroutine cancelled while it waits goes no further, as it would after a failure`() = runTest {
        var wentOn = false
        val screen = launch {
            attempt { awaitCancellation() }
            wentOn = true
        }
        runCurrent()

        screen.cancelAndJoin()

        assertFalse(wentOn)
    }
}
