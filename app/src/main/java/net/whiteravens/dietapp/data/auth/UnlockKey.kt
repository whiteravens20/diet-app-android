// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/** What the unlock asks of the phone: whether it can ask, and the key that watches its fingerprints. */
interface UnlockDevice {
    /** Whether the phone can show a prompt now: a strong biometric, or from Android 11 its screen lock. */
    fun canAsk(): Boolean

    /** Whether the unlock can be turned on: a strong biometric is enrolled, which the key needs. */
    fun canTurnOn(): Boolean

    /** Makes the key that watches the fingerprints; throws where none is enrolled. */
    fun makeKey()

    /** Whether the key was voided by a change of the fingerprints or of the screen lock; a missing key counts as one. */
    fun voided(): Boolean

    fun deleteKey()
}

/**
 * The phone itself. The key needs a strong biometric for every use and is voided by Android when a fingerprint is
 * added or all are removed, and when the screen lock goes ("irreversibly invalidated", `KeyGenParameterSpec.Builder`).
 * It seals nothing: a try at starting a cipher with it is all it is for, and it throws once the key is voided. Any
 * other refusal of the Keystore counts as voided as well: the session ends rather than the screens open.
 */
@Singleton
class PhoneUnlockDevice @Inject constructor(@ApplicationContext private val context: Context) : UnlockDevice {
    override fun canAsk(): Boolean =
        UnlockStandIn.device()?.canAsk() ?: (BiometricManager.from(context).canAuthenticate(asked()) == BiometricManager.BIOMETRIC_SUCCESS)

    override fun canTurnOn(): Boolean =
        UnlockStandIn.device()?.canTurnOn() ?: (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS)

    override fun makeKey() {
        UnlockStandIn.device()?.let { return it.makeKey() }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // A key a screen lock could open as well would not be voided by a new fingerprint: the biometric alone.
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(spec)
            generateKey()
        }
    }

    override fun voided(): Boolean {
        UnlockStandIn.device()?.let { return it.voided() }
        return try {
            val key = KeyStore.getInstance(PROVIDER).apply { load(null) }.getKey(ALIAS, null) as? SecretKey ?: return true
            Cipher.getInstance(CIPHER).init(Cipher.ENCRYPT_MODE, key)
            false
        } catch (e: GeneralSecurityException) {
            true
        } catch (e: ProviderException) {
            true
        }
    }

    override fun deleteKey() {
        UnlockStandIn.device()?.let { return it.deleteKey() }
        try {
            KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS)
        } catch (e: GeneralSecurityException) {
            // A keystore that cannot be read holds no key to leave behind.
        }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "unlock-fingerprints"
        const val CIPHER = "AES/GCM/NoPadding"
    }
}

/** What a prompt of the unlock accepts: from Android 11 a strong biometric or the screen lock, on Android 10 the biometric alone. */
fun asked(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG
