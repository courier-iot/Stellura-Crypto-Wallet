package com.stellura.wallet.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (recovery phrase, PIN hash, authenticator secret) with an
 * AES-256-GCM key that lives inside the Android Keystore. The key never leaves the
 * secure hardware (StrongBox when the phone has it, otherwise TEE), so the encrypted
 * files are useless if copied off the device.
 */
class SecureStore(context: Context) {

    private val dir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }

    fun put(name: String, plain: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        val out = ByteBuffer.allocate(1 + iv.size + ct.size).put(iv.size.toByte()).put(iv).put(ct).array()
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(out)
        if (!tmp.renameTo(File(dir, name))) {
            File(dir, name).writeBytes(out); tmp.delete()
        }
    }

    fun get(name: String): ByteArray? {
        val f = File(dir, name)
        if (!f.exists()) return null
        val buf = ByteBuffer.wrap(f.readBytes())
        val iv = ByteArray(buf.get().toInt()).also { buf.get(it) }
        val ct = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    fun has(name: String) = File(dir, name).exists()

    fun delete(name: String) {
        val f = File(dir, name)
        if (f.exists()) {
            // Overwrite before delete; best effort on flash storage.
            runCatching { f.writeBytes(ByteArray(f.length().toInt())) }
            f.delete()
        }
    }

    fun wipeAll() {
        dir.listFiles()?.forEach { delete(it.name) }
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return generate(strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        return try {
            gen.init(spec); gen.generateKey()
        } catch (e: Exception) {
            // No StrongBox chip on this phone: fall back to the TEE-backed keystore.
            if (strongBox) generate(strongBox = false) else throw e
        }
    }

    companion object {
        private const val ALIAS = "stellura_vault_key_v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        const val SEED = "seed.bin"
        const val PIN = "pin.bin"
        const val TOTP = "totp.bin"
    }
}
