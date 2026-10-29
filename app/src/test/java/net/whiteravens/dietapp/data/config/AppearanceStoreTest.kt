// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.domain.Palette
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The application's colour scheme: the account's until one is chosen on the phone, which stays. */
class AppearanceStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun stop() = scope.cancel()

    private val store by lazy {
        AppearanceStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "appearance.preferences_pb") }))
    }

    @Test
    fun `before anything is heard or chosen the default scheme is drawn`() = runBlocking {
        assertEquals(Palette.DEFAULT, store.palette.first())
        assertNull(store.chosen.first())
        assertNull(store.account.first())
    }

    @Test
    fun `the account's scheme is followed until one is chosen, and again once the choice is given back`() = runBlocking {
        store.heard("ocean")
        assertEquals(Palette.OCEAN, store.palette.first())

        store.choose(Palette.FOREST)
        store.heard("rose")
        assertEquals(Palette.FOREST, store.palette.first())
        assertEquals(Palette.ROSE, store.account.first())

        store.choose(null)
        assertEquals(Palette.ROSE, store.palette.first())
    }

    @Test
    fun `signing out forgets the account's scheme and keeps the one chosen on the phone`() = runBlocking {
        store.heard("sunset")
        store.forgetAccount()
        assertEquals(Palette.DEFAULT, store.palette.first())

        store.choose(Palette.MONO)
        store.heard("ocean")
        store.forgetAccount()
        assertEquals(Palette.MONO, store.palette.first())
    }

    @Test
    fun `a scheme of the account this version does not know is drawn as the default`() = runBlocking {
        store.heard("lavender")
        assertEquals(Palette.DEFAULT, store.palette.first())
        assertEquals(Palette.DEFAULT, store.account.first())
    }
}
