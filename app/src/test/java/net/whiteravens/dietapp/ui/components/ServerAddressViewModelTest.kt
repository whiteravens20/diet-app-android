// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.components

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.config.LocalNetworkAccess
import net.whiteravens.dietapp.data.repository.ServerRepository
import net.whiteravens.dietapp.domain.ServerProbe
import net.whiteravens.dietapp.ui.components.ServerAddressViewModel.State
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A server on the local network hears nothing from the app before the app may reach it (Android 17). */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerAddressViewModelTest {
    @Before
    fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    /** The phone's local network as the test has it: [local] hosts, and whether the app may reach them. */
    private class Network(val local: Set<String>, var allowed: Boolean) : LocalNetworkAccess {
        override suspend fun isLocal(host: String) = host in local

        override fun allowed() = allowed
    }

    private val servers = mockk<ServerRepository>().also { coEvery { it.probe(any()) } returns ServerProbe.Answers }

    @Test
    fun `a server on the local network is asked about only once the permission is given`() {
        val model = ServerAddressViewModel(servers, Network(setOf("192.168.1.20"), allowed = false))

        model.check("https://192.168.1.20")

        assertEquals(State.NeedsLocalNetwork("https://192.168.1.20/api/"), model.state.value)
        coVerify(exactly = 0) { servers.probe(any()) }

        model.localNetworkAnswered(granted = true)

        assertEquals(State.Answers("https://192.168.1.20/api/"), model.state.value)
        coVerify(exactly = 1) { servers.probe("https://192.168.1.20/api/") }
    }

    @Test
    fun `a refused permission is named, and nothing is sent to the server`() {
        val model = ServerAddressViewModel(servers, Network(setOf("192.168.1.20"), allowed = false))

        model.check("https://192.168.1.20")
        model.localNetworkAnswered(granted = false)

        assertEquals(State.LocalNetworkRefused("https://192.168.1.20/api/"), model.state.value)
        coVerify(exactly = 0) { servers.probe(any()) }
    }

    @Test
    fun `a server elsewhere, or one the app may reach, is asked at once`() {
        val elsewhere = ServerAddressViewModel(servers, Network(setOf("192.168.1.20"), allowed = false))
        val reachable = ServerAddressViewModel(servers, Network(setOf("192.168.1.20"), allowed = true))

        elsewhere.check("https://diet.example.org")
        reachable.check("https://192.168.1.20")

        assertEquals(State.Answers("https://diet.example.org/api/"), elsewhere.state.value)
        assertEquals(State.Answers("https://192.168.1.20/api/"), reachable.state.value)
    }
}
