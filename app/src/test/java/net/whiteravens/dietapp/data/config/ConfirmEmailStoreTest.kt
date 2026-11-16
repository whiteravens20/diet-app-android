// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/** The notice of an unconfirmed address put aside on the phone, for the account it was put aside for. */
class ConfirmEmailStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun stop() = scopes.forEach { it.cancel() }

    private fun store(): ConfirmEmailStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return ConfirmEmailStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "confirm_email.preferences_pb") }))
    }

    @Test
    fun `the account the notice was put aside for is kept until its session ends, and then nothing of it is`() = runBlocking {
        val store = store()
        val now = Instant.parse("2026-11-16T08:00:00Z")
        store.putAside("account-1", now)
        assertEquals(PutAside("account-1", now.plus(ConfirmEmailStore.PUT_ASIDE_FOR)), store.aside.first())

        store.forget()

        assertNull(store.aside.first())
    }
}
