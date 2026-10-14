// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seals under a key the Android Keystore holds and never lets out. A key that is gone, after a
 * restore or a reset of the keystore, opens nothing it sealed: the session is then no session,
 * and the user signs in again.
 */
@Singleton
class KeystoreSealer @Inject constructor() : AesGcmSealer(existingKey = ::storedKey, key = ::storedOrNewKey) {
    internal companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "session-tokens"
    }
}

private fun storedKey(): SecretKey? =
    KeyStore.getInstance(KeystoreSealer.PROVIDER).apply { load(null) }.getKey(KeystoreSealer.ALIAS, null) as? SecretKey

private val keyMaking = Any()

private fun storedOrNewKey(): SecretKey = synchronized(keyMaking) {
    storedKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeystoreSealer.PROVIDER).run {
        init(
            KeyGenParameterSpec.Builder(KeystoreSealer.ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }
}
