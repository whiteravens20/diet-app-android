// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals the values the token store keeps, and opens them again. A value sealed for one purpose
 * (an access token, a refresh token) opens for that purpose only.
 */
interface Sealer {
    fun seal(purpose: String, plain: String): String

    /**
     * What [sealed] holds, or null when it cannot be opened: the key that sealed it is gone, or it
     * was sealed for another purpose, or it was never sealed by this sealer.
     */
    fun open(purpose: String, sealed: String): String?
}

/**
 * Seals with AES-GCM under the key [key] gives, making one when there is none, the purpose bound
 * to the value as associated data; [existingKey] is the key there is, or null.
 */
open class AesGcmSealer(
    private val existingKey: () -> SecretKey?,
    private val key: () -> SecretKey,
) : Sealer {

    override fun seal(purpose: String, plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(purpose.toByteArray())
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()))
    }

    override fun open(purpose: String, sealed: String): String? =
        try {
            val bytes = Base64.getDecoder().decode(sealed)
            val key = existingKey()
            if (key == null || bytes.size <= IV_BYTES) {
                null
            } else {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
                cipher.updateAAD(purpose.toByteArray())
                String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
            }
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: ProviderException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
