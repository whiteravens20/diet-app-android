// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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

        store.save(tokens(ACCESS, REFRESH), SESSION)

        assertFalse(file.holds(ACCESS))
        assertFalse(file.holds(REFRESH))
        assertEquals(Session(ACCESS, REFRESH), store.current())
        assertEquals(Session(ACCESS, REFRESH), TokenStore(data, AesGcmSealer({ key }, { key })).current())
    }

    @Test
    fun `a new pair is the session as soon as it is saved`() = runBlocking {
        val store = TokenStore(data, sealer)
        store.save(tokens(ACCESS, REFRESH), SESSION)
        assertEquals(Session(ACCESS, REFRESH), store.current())

        store.save(tokens("access-2", "refresh-2"), SESSION)

        assertEquals(Session("access-2", "refresh-2"), store.current())
    }

    @Test
    fun `a pair that the key cannot open, or that was never sealed, is no session`() = runBlocking {
        TokenStore(data, sealer).save(tokens(ACCESS, REFRESH), SESSION)
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
    fun `the id of the session is kept beside its pair, through a refresh, and goes with it`() = runBlocking {
        val store = TokenStore(data, sealer)

        store.save(tokens(ACCESS, REFRESH), SESSION)
        assertEquals(SESSION, store.sessionId())
        store.save(tokens("access-2", "refresh-2"), SESSION)
        assertEquals(SESSION, store.sessionId())

        store.clear()
        assertNull(store.sessionId())

        // A server that does not name the session leaves none named, not the one before.
        store.save(tokens(ACCESS, REFRESH), SESSION)
        store.save(tokens("access-3", "refresh-3"), null)
        assertNull(store.sessionId())
    }

    @Test
    fun `the refresh tokens waiting to be revoked are kept sealed too, with the servers that issued them`() = runBlocking {
        val store = TokenStore(data, sealer)
        val pending = listOf(
            PendingRevocation(REFRESH, "https://diet.example.test/api/"),
            PendingRevocation("refresh-2", "https://another.example.test/api/"),
        )

        store.keepPendingRevocations(pending)

        assertFalse(file.holds(REFRESH))
        assertEquals(pending, store.pendingRevocations())
        val other = newKey()
        assertEquals(emptyList<PendingRevocation>(), TokenStore(data, AesGcmSealer({ other }, { other })).pendingRevocations())
        store.keepPendingRevocations(emptyList())
        assertEquals(emptyList<PendingRevocation>(), store.pendingRevocations())
    }

    @Test
    fun `no more than the newest few sign-outs are kept to revoke`() = runBlocking {
        val store = TokenStore(data, sealer)
        val many = (1..TokenStore.MOST_PENDING + 2).map { PendingRevocation("refresh-$it", "https://diet.example.test/api/") }

        store.keepPendingRevocations(many)

        assertEquals(many.takeLast(TokenStore.MOST_PENDING), store.pendingRevocations())
    }

    @Test
    fun `the one revocation an earlier version kept is read, and kept with the others from then on`() = runBlocking {
        val store = TokenStore(data, sealer)
        data.edit { prefs ->
            prefs[stringPreferencesKey("pending_revocation")] = sealer.seal("pending_revocation", REFRESH)
            prefs[stringPreferencesKey("pending_revocation_server")] = "https://diet.example.test/api/"
        }
        val earlier = PendingRevocation(REFRESH, "https://diet.example.test/api/")

        assertEquals(listOf(earlier), store.pendingRevocations())
        store.keepPendingRevocations(store.pendingRevocations() + PendingRevocation("refresh-2", "https://diet.example.test/api/"))
        assertEquals(listOf(earlier, PendingRevocation("refresh-2", "https://diet.example.test/api/")), store.pendingRevocations())
        assertFalse(data.data.first().contains(stringPreferencesKey("pending_revocation")))
    }

    private companion object {
        const val ACCESS = "eyJhbGciOiJIUzI1NiJ9.access-token-of-a-session"
        const val REFRESH = "7d0c5e2a-refresh-token-of-a-session"
        const val SESSION = "83bc3b9b-0c12-47f6-a4f2-d4ad0b806338"
    }
}
