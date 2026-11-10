// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The note on the water card, that it can be turned off in the profile: shown until it is closed on the phone. */
class WaterNoteStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun stop() = scopes.forEach { it.cancel() }

    /** A store over the phone's one file, as the app opens it again after a restart. */
    private fun store(): WaterNoteStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return WaterNoteStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "water_note.preferences_pb") }))
    }

    @Test
    fun `the note is not closed until it is, and stays closed when the app opens again`() = runBlocking {
        val first = store()
        assertFalse(first.closed.first())

        first.close()
        assertTrue(first.closed.first())
        // The first store has ended, as the app's has after a restart, before the second opens its file: DataStore
        // lets one store at a time have a file, and frees it only once the scope that held it has finished.
        scopes.single().coroutineContext[Job]!!.cancelAndJoin()
        assertTrue(store().closed.first())
    }
}
