// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.remote.ApiException
import net.whiteravens.dietapp.data.repository.SessionRepository
import net.whiteravens.dietapp.domain.AccountSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionsViewModelTest {
    private val at = Instant.parse("2026-10-17T08:42:55Z")
    private val phone = AccountSession("phone", "DietApp-Android · Android", at, at, at.plusSeconds(7_776_000), current = true)
    private val browser = AccountSession("browser", "Firefox · Linux", at, at, at.plusSeconds(7_776_000), current = false)
    private val sessions = mockk<SessionRepository>()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `the sessions are read on opening, and another's sign-out reads them again`() = runTest {
        coEvery { sessions.sessions() } returnsMany listOf(listOf(phone, browser), listOf(phone))
        coEvery { sessions.end("browser") } returns Unit
        val model = SessionsViewModel(sessions)
        assertEquals(listOf(phone, browser), model.state.value.sessions)

        model.end("browser")

        assertEquals(listOf(phone), model.state.value.sessions)
        assertFalse(model.state.value.ending)
        assertNull(model.state.value.endFailed)
    }

    @Test
    fun `after the phone's own sign-out nothing more is read, the phone being signed out`() = runTest {
        coEvery { sessions.sessions() } returns listOf(phone, browser)
        coEvery { sessions.end("phone") } returns Unit
        val model = SessionsViewModel(sessions)

        model.end("phone")

        coVerify(exactly = 1) { sessions.sessions() }
        coVerify { sessions.end("phone") }
    }

    @Test
    fun `a refusal is said, and the list read again leaves out a session gone already`() = runTest {
        val gone = ApiException(404, "SESSION_NOT_FOUND", "That session is not open.")
        coEvery { sessions.sessions() } returnsMany listOf(listOf(phone, browser), listOf(phone))
        coEvery { sessions.end("browser") } throws gone
        val model = SessionsViewModel(sessions)

        model.end("browser")

        assertSame(gone, model.state.value.endFailed)
        assertEquals(listOf(phone), model.state.value.sessions)
    }

    @Test
    fun `one sign-out at a time, and signing out everywhere says a refusal`() = runTest {
        coEvery { sessions.sessions() } returns listOf(phone, browser)
        val answer = CompletableDeferred<Unit>()
        coEvery { sessions.end("browser") } coAnswers { answer.await() }
        val held = ApiException(429, "RATE_LIMITED", "Too many requests.", retryAfterSeconds = 30)
        coEvery { sessions.endAll() } throws held
        val model = SessionsViewModel(sessions)

        model.end("browser")
        assertTrue(model.state.value.ending)
        model.endAll()
        model.end("phone")
        coVerify(exactly = 0) { sessions.endAll() }
        coVerify(exactly = 0) { sessions.end("phone") }
        answer.complete(Unit)

        model.endAll()
        assertSame(held, model.state.value.endFailed)
        assertFalse(model.state.value.ending)
    }

    @Test
    fun `a list that cannot be read is said, and read again on asking`() = runTest {
        coEvery { sessions.sessions() } throws IOException("no network")
        val model = SessionsViewModel(sessions)
        assertTrue(model.state.value.readFailed is IOException)
        assertNull(model.state.value.sessions)

        coEvery { sessions.sessions() } returns listOf(phone)
        model.load()

        assertEquals(listOf(phone), model.state.value.sessions)
        assertNull(model.state.value.readFailed)
    }
}
