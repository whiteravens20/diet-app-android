// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.whiteravens.dietapp.data.auth.KeystoreSealer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.SecretKey

/** The tokens' key, as the device's keystore holds it. */
@RunWith(AndroidJUnit4::class)
class KeystoreSealerTest {
    private fun keystore() = KeyStore.getInstance(KeystoreSealer.PROVIDER).apply { load(null) }

    @After
    fun forgetTheKey() {
        keystore().deleteEntry(KeystoreSealer.ALIAS)
    }

    @Test
    fun aValueSealedUnderTheKeystoresKeyOpensAgainAndTheKeyNeverLeavesTheKeystore() {
        val sealed = KeystoreSealer().seal("refresh_token", TOKEN)

        // Another sealer opens it: the key is the keystore's, not the object's.
        assertEquals(TOKEN, KeystoreSealer().open("refresh_token", sealed))
        assertNull((keystore().getKey(KeystoreSealer.ALIAS, null) as SecretKey).encoded)
    }

    @Test
    fun aValueSealedBeforeTheKeyWasGoneOpensToNothingAndANewKeySealsAgain() {
        val sealer = KeystoreSealer()
        val sealed = sealer.seal("refresh_token", TOKEN)

        keystore().deleteEntry(KeystoreSealer.ALIAS)

        assertNull(sealer.open("refresh_token", sealed))
        assertEquals(TOKEN, sealer.open("refresh_token", sealer.seal("refresh_token", TOKEN)))
    }

    private companion object {
        const val TOKEN = "7d0c5e2a-refresh-token-of-a-session"
    }
}
