package com.stellura.wallet.wallet

import com.stellura.wallet.security.SecureStore
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet
import wallet.core.jni.Hash
import wallet.core.jni.Mnemonic
import wallet.core.jni.PrivateKey

/**
 * Self-custody wallet. One BIP-39 recovery phrase derives keys for every supported chain
 * (BIP-44/84 standard paths, so the same phrase works in other major wallets).
 * The phrase is only ever held decrypted in memory while needed.
 */
class WalletManager(private val store: SecureStore) {

    fun hasWallet() = store.has(SecureStore.SEED)

    /** 12 words = 128 bits of entropy, 24 words = 256 bits. */
    fun generateMnemonic(words: Int = 12): String {
        require(words == 12 || words == 24)
        return HDWallet(if (words == 12) 128 else 256, "").mnemonic()
    }

    fun normalise(phrase: String) = phrase.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

    fun isValidMnemonic(phrase: String): Boolean {
        val n = normalise(phrase)
        val count = n.split(' ').size
        return (count == 12 || count == 24) && Mnemonic.isValid(n)
    }

    fun isValidWord(word: String) = Mnemonic.isValidWord(word.lowercase())

    fun save(phrase: String) {
        require(isValidMnemonic(phrase)) { "Invalid recovery phrase" }
        store.put(SecureStore.SEED, normalise(phrase).toByteArray(Charsets.UTF_8))
    }

    /** Only call after the user has unlocked the app. */
    fun revealMnemonic(): String = String(store.get(SecureStore.SEED) ?: error("No wallet"), Charsets.UTF_8)

    private fun hd(): HDWallet = HDWallet(revealMnemonic(), "")

    fun address(chain: Chain): String = when (chain) {
        // EVM testnets share mainnet address format.
        Chain.ETHEREUM -> hd().getAddressForCoin(CoinType.ETHEREUM)
        // Solana devnet shares mainnet address format.
        Chain.SOLANA -> hd().getAddressForCoin(CoinType.SOLANA)
        // BIP-84 testnet path (coin type 1), native SegWit "tb1…" address.
        Chain.BITCOIN -> {
            val key = hd().getKey(CoinType.BITCOIN, "m/84'/1'/0'/0/0")
            val pub = key.getPublicKeySecp256k1(true).data()
            Bech32.segwitV0("tb", Hash.sha256RIPEMD(pub))
        }
    }

    fun ethPrivateKey(): PrivateKey = hd().getKeyForCoin(CoinType.ETHEREUM)

    fun deleteWallet() = store.delete(SecureStore.SEED)
}
