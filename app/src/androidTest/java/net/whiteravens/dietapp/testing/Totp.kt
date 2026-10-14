// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The code an authenticator app shows for [secret] (base 32) at [at]: SHA-1, thirty seconds, six digits. */
fun totp(secret: String, at: Long = System.currentTimeMillis()): String {
    val step = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(at / 30_000).array()
    val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(base32(secret), "HmacSHA1")) }.doFinal(step)
    val offset = mac.last().toInt() and 0x0f
    val number = ((mac[offset].toInt() and 0x7f) shl 24) or
        ((mac[offset + 1].toInt() and 0xff) shl 16) or
        ((mac[offset + 2].toInt() and 0xff) shl 8) or
        (mac[offset + 3].toInt() and 0xff)
    return (number % 1_000_000).toString().padStart(6, '0')
}

private fun base32(text: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val out = ByteArrayOutputStream()
    var buffer = 0
    var bits = 0
    for (c in text.uppercase().filter { it != '=' && !it.isWhitespace() }) {
        buffer = (buffer shl 5) or alphabet.indexOf(c)
        bits += 5
        if (bits >= 8) {
            out.write((buffer shr (bits - 8)) and 0xff)
            bits -= 8
        }
    }
    return out.toByteArray()
}
