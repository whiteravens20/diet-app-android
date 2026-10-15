// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import net.whiteravens.dietapp.domain.InstanceRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    @After
    fun reset() = Dispatchers.resetMain()

    private fun instance(mailsSent: Boolean = true) = mockk<ServerRepository>().also {
        every { it.rules } returns MutableStateFlow<InstanceRules?>(InstanceRules(2, emptyMap(), mailsSent = mailsSent))
    }

    private fun servers() = mockk<ServerConfigStore>().also { every { it.baseUrl } returns flowOf("https://diet.example.test/api/") }

    @Test
    fun `the mail that confirms the address is sent again, and a refusal is said`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val users = mockk<UserRepository>()
        coEvery { users.me() } throws IOException("no network")
        coEvery { users.resendVerification() } returns Unit
        val model = AccountViewModel(users, mockk(), servers(), instance(), this)

        model.resendVerification()
        assertTrue(model.state.value.resent)

        val held = ApiException(429, "VERIFICATION_SENT_RECENTLY", "A moment ago.", retryAfterSeconds = 120)
        val again = mockk<UserRepository>()
        coEvery { again.me() } throws IOException("no network")
        coEvery { again.resendVerification() } throws held
        val refused = AccountViewModel(again, mockk(), servers(), instance(), this)
        refused.resendVerification()
        assertFalse(refused.state.value.resent)
        assertEquals(held, refused.state.value.resendError)
    }

    @Test
    fun `a change of server keeps the new address though the sign-out takes the screen away`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        // The application's scope: the test's own, which outlives the screen.
        val application: CoroutineScope = this
        val screen = ViewModelStore()
        var saved: String? = null
        val servers = mockk<ServerConfigStore>()
        every { servers.baseUrl } returns flowOf("https://old.example.test/api/")
        // Saved as the store saves it, in a step that a cancelled coroutine does not get past.
        coEvery { servers.save(any()) } coAnswers {
            yield()
            saved = firstArg()
        }
        val auth = mockk<AuthRepository>()
        // The sign-out ends the screen, as the root's switch to the sign-in screen does.
        coEvery { auth.signOut() } coAnswers { screen.clear() }
        val users = mockk<UserRepository>()
        coEvery { users.me() } throws IOException("no network")
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AccountViewModel(users, auth, servers, instance(), application) as T
        }
        val model = ViewModelProvider(screen, factory)[AccountViewModel::class.java]

        model.changeServer("https://new.example.test/api/")
        testScheduler.advanceUntilIdle()

        assertEquals("https://new.example.test/api/", saved)
    }
}
