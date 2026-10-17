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
import net.whiteravens.dietapp.data.repository.UserRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailChangeViewModelTest {
    private val users = mockk<UserRepository>()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `an address taken already is refused with the reason, and what was typed stays to be put right`() = runTest {
        val taken = ApiException(400, "EMAIL_TAKEN", "Email already registered.")
        coEvery { users.changeEmail("ola@example.test", "Str0ng-Passw0rd-1!") } throws taken
        val model = EmailChangeViewModel(users)

        model.open()
        model.typeEmail(" ola@example.test ")
        model.typePassword("Str0ng-Passw0rd-1!")
        model.send()

        assertSame(taken, model.state.value.error)
        assertTrue(model.state.value.editing)
        assertFalse(model.state.value.sending)
        assertEquals(" ola@example.test ", model.state.value.newEmail)
        assertEquals("Str0ng-Passw0rd-1!", model.state.value.password)
    }

    @Test
    fun `the new address is asked for with the password, the form closes and says where the link went`() = runTest {
        coEvery { users.changeEmail(any(), any()) } returns Unit
        val model = EmailChangeViewModel(users)

        model.open()
        model.typeEmail("ola@example.test")
        model.send()
        coVerify(exactly = 0) { users.changeEmail(any(), any()) }
        model.typePassword("Str0ng-Passw0rd-1!")
        model.send()

        coVerify(exactly = 1) { users.changeEmail("ola@example.test", "Str0ng-Passw0rd-1!") }
        assertTrue(model.state.value.sent)
        assertFalse(model.state.value.editing)
        assertEquals("", model.state.value.password)
    }

    @Test
    fun `a form closed forgets what was typed in it`() = runTest {
        val model = EmailChangeViewModel(users)

        model.open()
        model.typeEmail("ola@example.test")
        model.typePassword("Str0ng-Passw0rd-1!")
        model.cancel()

        assertFalse(model.state.value.editing)
        assertEquals("", model.state.value.newEmail)
        assertEquals("", model.state.value.password)
    }
}
