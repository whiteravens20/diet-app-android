// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.whiteravens.dietapp.data.remote.AuthTokens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The session as the phone keeps it: every token sealed, in a file that holds none of them, and
 * opened again only by the key that sealed it. A key of the test's own stands in for the keystore's.
 */
class TokenStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun stop() = scope.cancel()

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val sealer = AesGcmSealer({ key }, { key })
    private val file by lazy { File(folder.root, "auth.preferences_pb") }
    private val data by lazy { PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }) }

    private fun tokens(access: String, refresh: String) = AuthTokens(access, refresh, 900.0)

    private fun File.holds(text: String) = readBytes().toString(Charsets.ISO_8859_1).contains(text)

    @Test
    fun `a session is kept sealed, and the pair opens again`() = runBlocking {
        val store = TokenStore(data, sealer)

        store.save(tokens(ACCESS, REFRESH))

        assertFalse(file.holds(ACCESS))
        assertFalse(file.holds(REFRESH))
        assertEquals(Session(ACCESS, REFRESH), store.current())
        assertEquals(Session(ACCESS, REFRESH), TokenStore(data, AesGcmSealer({ key }, { key })).current())
    }

    @Test
    fun `a new pair is the session as soon as it is saved`() = runBlocking {
        val store = TokenStore(data, sealer)
        store.save(tokens(ACCESS, REFRESH))
        assertEquals(Session(ACCESS, REFRESH), store.current())

        store.save(tokens("access-2", "refresh-2"))

        assertEquals(Session("access-2", "refresh-2"), store.current())
    }

    @Test
    fun `a pair that the key cannot open, or that was never sealed, is no session`() = runBlocking {
        TokenStore(data, sealer).save(tokens(ACCESS, REFRESH))
        val other = newKey()

        assertNull(TokenStore(data, AesGcmSealer({ null }, { other })).current())
        assertNull(TokenStore(data, AesGcmSealer({ other }, { other })).current())

        data.edit { prefs ->
            prefs[stringPreferencesKey("access_token")] = ACCESS
            prefs[stringPreferencesKey("refresh_token")] = REFRESH
        }
        assertNull(TokenStore(data, sealer).current())
    }

    @Test
    fun `the refresh token waiting to be revoked is kept sealed too`() = runBlocking {
        val store = TokenStore(data, sealer)

        store.setPendingRevocation(REFRESH)

        assertFalse(file.holds(REFRESH))
        assertEquals(REFRESH, store.pendingRevocation())
        val other = newKey()
        assertNull(TokenStore(data, AesGcmSealer({ other }, { other })).pendingRevocation())
        store.setPendingRevocation(null)
        assertNull(store.pendingRevocation())
    }

    private companion object {
        const val ACCESS = "eyJhbGciOiJIUzI1NiJ9.access-token-of-a-session"
        const val REFRESH = "7d0c5e2a-refresh-token-of-a-session"
    }
}
