package com.stellura.wallet.security

import android.content.Context
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 6-digit PIN. Stored as a salted PBKDF2-SHA256 hash, and that hash is itself encrypted
 * by the hardware keystore (SecureStore), so it can't be brute-forced off-device.
 * Wrong attempts trigger growing lockouts.
 */
class PinManager(context: Context, private val store: SecureStore) {

    private val prefs = context.getSharedPreferences("pin_state", Context.MODE_PRIVATE)

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val attemptsLeftBeforeLockout: Int) : Result
        data class LockedOut(val secondsRemaining: Long) : Result
    }

    fun isSet(): Boolean = store.has(SecureStore.PIN)

    fun set(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all { it.isDigit() }) { "PIN must be $PIN_LENGTH digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt, ITERATIONS)
        val blob = ByteBuffer.allocate(16 + 4 + hash.size).put(salt).putInt(ITERATIONS).put(hash).array()
        store.put(SecureStore.PIN, blob)
        resetFailures()
    }

    fun verify(pin: String): Result {
        val now = System.currentTimeMillis()
        val until = prefs.getLong(KEY_LOCK_UNTIL, 0)
        if (now < until) return Result.LockedOut((until - now + 999) / 1000)

        val blob = store.get(SecureStore.PIN) ?: return Result.Wrong(0)
        val buf = ByteBuffer.wrap(blob)
        val salt = ByteArray(16).also { buf.get(it) }
        val iters = buf.int
        val expected = ByteArray(buf.remaining()).also { buf.get(it) }
        val ok = MessageDigest.isEqual(expected, derive(pin, salt, iters))
        if (ok) { resetFailures(); return Result.Ok }

        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        prefs.edit().putInt(KEY_FAILS, fails).apply()
        if (fails >= FREE_ATTEMPTS) {
            // 30s, 60s, 120s ... capped at 1 hour.
            val step = (fails - FREE_ATTEMPTS).coerceAtMost(7)
            val seconds = (30L shl step).coerceAtMost(3600L)
            prefs.edit().putLong(KEY_LOCK_UNTIL, now + seconds * 1000).apply()
            return Result.LockedOut(seconds)
        }
        return Result.Wrong(FREE_ATTEMPTS - fails)
    }

    private fun resetFailures() = prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0).apply()

    private fun derive(pin: String, salt: ByteArray, iters: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iters, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }

    fun clear() { store.delete(SecureStore.PIN); resetFailures() }

    companion object {
        const val PIN_LENGTH = 6
        private const val ITERATIONS = 210_000
        private const val FREE_ATTEMPTS = 5
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
    }
}
