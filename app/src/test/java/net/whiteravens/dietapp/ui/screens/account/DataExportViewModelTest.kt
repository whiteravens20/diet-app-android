// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.ui.screens.account

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.whiteravens.dietapp.data.repository.IncompleteExportException
import net.whiteravens.dietapp.data.repository.UserRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DataExportViewModelTest {
    private val users = mockk<UserRepository>()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `the file is offered under the web's name, with the phone's day`() {
        assertEquals("diet-app-account-2026-10-17.json", DataExportViewModel.fileName(LocalDate.parse("2026-10-17")))
    }

    @Test
    fun `the account's file is written into the place chosen, which is kept and closed`() = runTest {
        coEvery { users.export(any()) } coAnswers { firstArg<OutputStream>().write("{\"complete\":true}".toByteArray()) }
        val model = DataExportViewModel(users)
        var closed = false
        val place = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
            }
        }
        var discarded = false

        model.export({ place }, { discarded = true })

        assertTrue(model.state.value.saved)
        assertFalse(model.state.value.preparing)
        assertNull(model.state.value.error)
        assertEquals("{\"complete\":true}", place.toString(Charsets.UTF_8))
        assertTrue(closed)
        assertFalse(discarded)
    }

    @Test
    fun `a file that did not arrive whole is taken away again, and the reason said`() = runTest {
        val cut = IncompleteExportException()
        coEvery { users.export(any()) } throws cut
        val model = DataExportViewModel(users)
        var discarded = false

        model.export({ ByteArrayOutputStream() }, { discarded = true })

        assertTrue(discarded)
        assertFalse(model.state.value.saved)
        assertEquals(cut, model.state.value.error)
    }

    @Test
    fun `a place that cannot be written leaves nothing behind either`() = runTest {
        val model = DataExportViewModel(users)
        var discarded = false

        model.export({ throw java.io.FileNotFoundException("gone") }, { discarded = true })

        assertTrue(discarded)
        assertTrue(model.state.value.error is java.io.FileNotFoundException)
    }
}
