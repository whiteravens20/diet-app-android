// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.InstanceRules
import net.whiteravens.dietapp.domain.SessionUser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmEmailViewModelTest {
    private val users = mockk<UserRepository>()
    private val rules = MutableStateFlow<InstanceRules?>(InstanceRules(maxProfiles = 2, catalogueDepth = emptyMap(), mailsSent = true))
    private val instance = mockk<ServerRepository> { every { rules } returns this@ConfirmEmailViewModelTest.rules }

    private fun account(confirmed: Boolean) = SessionUser("u", "ala@example.test", "Ala", confirmed, "pl", aiMode = "none")

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `names the address that waits to be confirmed, and nothing once it is`() = runTest {
        coEvery { users.me() } returnsMany listOf(account(confirmed = false), account(confirmed = true))
        val model = ConfirmEmailViewModel(users, instance)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }

        model.load()
        assertEquals("ala@example.test", model.state.value.waiting)

        // Confirmed in the browser, and read again as the app comes to the front.
        model.load()
        assertNull(model.state.value.waiting)
    }

    @Test
    fun `says nothing where the instance sends no mail, which confirms every address when its account is made`() = runTest {
        coEvery { users.me() } returns account(confirmed = false)
        rules.value = rules.value!!.copy(mailsSent = false)
        val model = ConfirmEmailViewModel(users, instance)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }

        model.load()

        assertNull(model.state.value.waiting)
    }

    @Test
    fun `keeps the notice as it was when the account cannot be read`() = runTest {
        coEvery { users.me() } returns account(confirmed = false) andThenThrows IOException("offline")
        val model = ConfirmEmailViewModel(users, instance)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }

        model.load()
        model.load()

        assertEquals("ala@example.test", model.state.value.waiting)
    }

    @Test
    fun `sends the link again, and says why where the API refused`() = runTest {
        coEvery { users.me() } returns account(confirmed = false)
        val refused = ApiException(429, "VERIFICATION_SENT_RECENTLY", "A verification mail was sent a moment ago.")
        coEvery { users.resendVerification() } returns Unit andThenThrows refused
        val model = ConfirmEmailViewModel(users, instance)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        model.load()

        model.resend()
        assertTrue(model.state.value.sent)
        assertFalse(model.state.value.sending)

        model.resend()
        assertSame(refused, model.state.value.error)
        coVerify(exactly = 2) { users.resendVerification() }
    }
}
