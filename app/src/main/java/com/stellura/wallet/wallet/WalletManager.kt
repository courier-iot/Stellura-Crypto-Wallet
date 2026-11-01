package com.stellura.wallet.wallet

import com.stellura.wallet.security.SecureStore
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet
import wallet.core.jni.Hash
import wallet.core.jni.Mnemonic
import wallet.core.jni.PrivateKey
import com.example.stellariteminer.KeySigner
import com.stellura.wallet.stellarite.StelCoin

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

    /**
     * The Stellarite test coins' key: ONE secp256k1 key for both Pulsarite and Nebulite (scout
     * rewards are paid to the other coin's address of the same key). Derived from the recovery
     * phrase on its own path, so it shares nothing with the Bitcoin/Ethereum/Solana keys.
     * Only call after the user has unlocked the app; use it and drop it.
     */
    fun stellariteSigner(): KeySigner {
        val raw = hd().getKey(CoinType.BITCOIN, STELLARITE_PATH).data()   // BITCOIN = secp256k1 BIP-32 derivation
        return KeySigner(raw.joinToString("") { "%02x".format(it.toInt() and 0xff) })
    }

    /** Public Stellarite address (plsr… or nblt…) of [stellariteSigner]'s key. */
    fun stellariteAddress(coin: StelCoin): String = stellariteSigner().address(coin.prefix)

    fun deleteWallet() = store.delete(SecureStore.SEED)

    companion object {
        /**
         * BIP-44 path for the Stellarite coins. 7827 is not a registered SLIP-44 coin type
         * (checked against satoshilabs/slips, November 2026), so no other wallet derives this key
         * from the same phrase. Register it before a public launch.
         */
        const val STELLARITE_PATH = "m/44'/7827'/0'/0/0"
    }
}
