package com.stellura.wallet.security

import android.net.Uri
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Authenticator-app unlock (RFC 6238 TOTP, compatible with Aegis, 2FAS, Google
 * Authenticator, etc). Fully offline: the shared secret is stored in the keystore vault.
 */
class TotpManager(private val store: SecureStore) {

    fun isEnabled() = store.has(SecureStore.TOTP)

    /** Creates a pending secret; call [confirm] with a valid code to enable it. */
    fun newSecret(): ByteArray = ByteArray(20).also { SecureRandom().nextBytes(it) }

    fun otpauthUri(secret: ByteArray, account: String): String =
        "otpauth://totp/" + Uri.encode("Stellura:${account.ifBlank { "wallet" }}") +
            "?secret=${base32(secret)}&issuer=Stellura&algorithm=SHA1&digits=6&period=30"

    fun confirm(secret: ByteArray, code: String): Boolean {
        if (!check(secret, code)) return false
        store.put(SecureStore.TOTP, secret)
        return true
    }

    fun verify(code: String): Boolean {
        val secret = store.get(SecureStore.TOTP) ?: return false
        return check(secret, code)
    }

    fun disable() = store.delete(SecureStore.TOTP)

    private fun check(secret: ByteArray, code: String): Boolean {
        val c = code.trim()
        if (c.length != 6 || !c.all { it.isDigit() }) return false
        val step = System.currentTimeMillis() / 1000 / 30
        return (-1..1).any { generate(secret, step + it) == c }
    }

    private fun generate(secret: ByteArray, counter: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val o = h.last().toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or
            ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        return (bin % 10.0.pow(6).toInt()).toString().padStart(6, '0')
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        fun base32(data: ByteArray): String {
            val sb = StringBuilder()
            var buffer = 0; var bits = 0
            for (b in data) {
                buffer = (buffer shl 8) or (b.toInt() and 0xff); bits += 8
                while (bits >= 5) { sb.append(ALPHABET[(buffer shr (bits - 5)) and 31]); bits -= 5 }
            }
            if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
            return sb.toString()
        }
    }
}
