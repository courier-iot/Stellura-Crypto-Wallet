package com.stellura.wallet.wallet

import wallet.core.jni.CoinType
import wallet.core.jni.StoredKey

/**
 * Serverless device transfer. The recovery phrase is sealed into a standard Web3 keystore
 * JSON (scrypt key-stretching + AES), so the .stellura file is safe to move by USB, email
 * or cloud drive: without the backup password it is useless. Nothing touches our servers,
 * because there are none.
 */
class BackupManager {

    fun export(mnemonic: String, password: String): ByteArray {
        require(password.length >= MIN_PASSWORD) { "Use at least $MIN_PASSWORD characters" }
        val key = StoredKey.importHDWallet(mnemonic, "Stellura", password.toByteArray(), CoinType.ETHEREUM)
            ?: error("Could not seal backup")
        return key.exportJSON() ?: error("Could not export backup")
    }

    /** Returns the recovery phrase, or null if the password is wrong / file is invalid. */
    fun import(file: ByteArray, password: String): String? = runCatching {
        val key = StoredKey.importJSON(file) ?: return null
        if (!key.isMnemonic) return null
        key.decryptMnemonic(password.toByteArray())
    }.getOrNull()

    companion object {
        const val MIN_PASSWORD = 10
        const val MIME = "application/octet-stream"
        const val FILE_NAME = "wallet-backup.stellura"
    }
}
