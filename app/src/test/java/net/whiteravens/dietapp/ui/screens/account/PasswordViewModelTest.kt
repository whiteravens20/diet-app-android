// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordViewModelTest {
    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `a wrong password is refused with the reason, and what was typed stays to be put right`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val users = mockk<UserRepository>()
        val wrong = ApiException(401, "INVALID_PASSWORD", "Current password is incorrect.")
        coEvery { users.changePassword("Wr0ng-Passw0rd-9?", "An0ther-Passw0rd-2?") } throws wrong
        val auth = mockk<AuthRepository>(relaxed = true)
        val model = PasswordViewModel(users, auth, this)

        model.typeCurrent("Wr0ng-Passw0rd-9?")
        model.typeNew("An0ther-Passw0rd-2?")
        model.change()
        testScheduler.advanceUntilIdle()

        assertSame(wrong, model.state.value.error)
        assertFalse(model.state.value.changing)
        assertFalse(model.state.value.changed)
        assertEquals("Wr0ng-Passw0rd-9?", model.state.value.current)
        assertEquals("An0ther-Passw0rd-2?", model.state.value.new)
        coVerify(exactly = 0) { auth.leave() }
    }

    @Test
    fun `a changed password is said, then the phone leaves the session the change ended`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val users = mockk<UserRepository>()
        coEvery { users.changePassword(any(), any()) } returns Unit
        val auth = mockk<AuthRepository>(relaxed = true)
        val model = PasswordViewModel(users, auth, this)

        model.change()
        coVerify(exactly = 0) { users.changePassword(any(), any()) }
        model.typeCurrent("Str0ng-Passw0rd-1!")
        model.typeNew("An0ther-Passw0rd-2?")
        model.change()

        coVerify(exactly = 1) { users.changePassword("Str0ng-Passw0rd-1!", "An0ther-Passw0rd-2?") }
        assertTrue(model.state.value.changed)
        assertEquals("", model.state.value.current)
        assertEquals("", model.state.value.new)
        coVerify(exactly = 0) { auth.leave() }
        model.change()
        testScheduler.advanceTimeBy(2_000)
        coVerify(exactly = 1) { auth.leave() }
        coVerify(exactly = 1) { users.changePassword(any(), any()) }
    }
}
