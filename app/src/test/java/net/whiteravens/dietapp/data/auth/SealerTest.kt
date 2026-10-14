// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** AES-GCM sealing, under a key of the test's own in place of the keystore's, which a JVM has not got. */
class SealerTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val sealer = AesGcmSealer({ key }, { key })

    @Test
    fun `a sealed value opens for its purpose, and for no other`() {
        val sealed = sealer.seal("refresh_token", TOKEN)

        assertFalse(sealed.contains(TOKEN))
        assertEquals(TOKEN, sealer.open("refresh_token", sealed))
        assertNull(sealer.open("access_token", sealed))
    }

    @Test
    fun `a value sealed twice reads otherwise each time`() {
        assertNotEquals(sealer.seal("refresh_token", TOKEN), sealer.seal("refresh_token", TOKEN))
    }

    @Test
    fun `a value whose key is gone or another opens to nothing`() {
        val sealed = sealer.seal("refresh_token", TOKEN)
        val other = newKey()

        assertNull(AesGcmSealer({ null }, { other }).open("refresh_token", sealed))
        assertNull(AesGcmSealer({ other }, { other }).open("refresh_token", sealed))
    }

    @Test
    fun `what was never sealed, or was changed after, opens to nothing`() {
        val sealed = Base64.getDecoder().decode(sealer.seal("refresh_token", TOKEN))
        sealed[sealed.size - 1] = (sealed.last().toInt() xor 1).toByte()

        assertNull(sealer.open("refresh_token", TOKEN))
        assertNull(sealer.open("refresh_token", ""))
        assertNull(sealer.open("refresh_token", Base64.getEncoder().encodeToString(ByteArray(5))))
        assertNull(sealer.open("refresh_token", Base64.getEncoder().encodeToString(sealed)))
    }

    private companion object {
        const val TOKEN = "e4c1f0b2-refresh-token-of-a-session"
    }
}
