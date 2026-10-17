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
class AccountDeletionViewModelTest {
    private val address = "Ala@Example.test"
    private val users = mockk<UserRepository>()
    private val auth = mockk<AuthRepository>(relaxed = true)

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `nothing is deleted until the address is typed again, whatever its case, and a password given`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        coEvery { users.deleteAccount(any()) } returns Unit
        val model = AccountDeletionViewModel(users, auth, this)

        model.open()
        model.typePassword("Str0ng-Passw0rd-1!")
        model.typeEmail("ola@example.test")
        model.delete(address)
        model.typeEmail(" ala@example.TEST ")
        model.typePassword("")
        model.delete(address)
        coVerify(exactly = 0) { users.deleteAccount(any()) }
        assertFalse(model.state.value.confirms(address))

        model.typePassword("Str0ng-Passw0rd-1!")
        assertTrue(model.state.value.confirms(address))
        model.delete(address)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { users.deleteAccount("Str0ng-Passw0rd-1!") }
        coVerify(exactly = 1) { auth.leave() }
    }

    @Test
    fun `a wrong password is said, the phone stays signed in, and cancelling forgets what was typed`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val wrong = ApiException(401, "INVALID_PASSWORD", "Current password is incorrect.")
        coEvery { users.deleteAccount(any()) } throws wrong
        val model = AccountDeletionViewModel(users, auth, this)

        model.open()
        model.typeEmail(address)
        model.typePassword("Wr0ng-Passw0rd-9?")
        model.delete(address)
        testScheduler.advanceUntilIdle()

        assertSame(wrong, model.state.value.error)
        assertFalse(model.state.value.deleting)
        coVerify(exactly = 0) { auth.leave() }
        model.cancel()
        assertFalse(model.state.value.confirming)
        assertEquals("", model.state.value.email)
        assertEquals("", model.state.value.password)
    }
}
