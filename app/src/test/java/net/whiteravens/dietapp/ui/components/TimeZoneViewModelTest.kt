// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.repository.UserRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TimeZoneViewModelTest {
    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `the zone is kept one ask at a time, and asked again once an ask has ended, a failed one too`() {
        val users = mockk<UserRepository>()
        val answer = CompletableDeferred<Unit>()
        coEvery { users.keepTimeZone(any()) } coAnswers { answer.await() } andThenThrows IOException("offline") andThen Unit
        val model = TimeZoneViewModel(users)

        model.keep()
        model.keep()
        coVerify(exactly = 1) { users.keepTimeZone(any()) }

        answer.complete(Unit)
        model.keep()
        model.keep()
        coVerify(exactly = 3) { users.keepTimeZone(any()) }
    }
}
