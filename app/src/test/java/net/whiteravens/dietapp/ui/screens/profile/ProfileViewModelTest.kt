// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import net.whiteravens.dietapp.data.config.ServerConfigStore
import net.whiteravens.dietapp.data.repository.AuthRepository
import net.whiteravens.dietapp.data.repository.UserRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @After
    fun reset() = Dispatchers.resetMain()

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
                ProfileViewModel(users, auth, servers, application) as T
        }
        val model = ViewModelProvider(screen, factory)[ProfileViewModel::class.java]

        model.changeServer("https://new.example.test/api/")
        testScheduler.advanceUntilIdle()

        assertEquals("https://new.example.test/api/", saved)
    }
}
