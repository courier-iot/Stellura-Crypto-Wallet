package com.stellura.wallet

import com.stellura.wallet.security.TotpManager
import com.stellura.wallet.wallet.Bech32
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.stellariteminer.KeySigner
import com.example.stellariteminer.Wallet
import com.stellura.wallet.wallet.WalletManager

class CryptoUnitTest {

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** BIP-173 test vectors for P2WPKH. */
    @Test fun bech32SegwitV0() {
        val program = hex("751e76e8199196d454941c45d1b3a323f1433bd6")
        assertEquals("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", Bech32.segwitV0("bc", program))
        assertEquals("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx", Bech32.segwitV0("tb", program))
    }

    /** RFC 4648 base32 vectors (padding stripped, as authenticator apps expect). */
    @Test fun base32() {
        assertEquals("MZXW6YTBOI", TotpManager.base32("foobar".toByteArray()))
        assertEquals("MY", TotpManager.base32("f".toByteArray()))
    }

    /**
     * Stellarite: the key at m/44'/7827'/0'/0/0 of the standard test phrase
     * "abandon abandon … about" (computed independently in Python with BIP-39/BIP-32, which also
     * reproduces that phrase's well-known Bitcoin key), and its addresses as the nodes compute them.
     * The same key signed a payment and a name claim that a Pulsarite node accepted.
     */
    @Test fun stellariteAddresses() {
        assertEquals("m/44'/7827'/0'/0/0", WalletManager.STELLARITE_PATH)
        val signer = KeySigner("239bb9886e455de95668dfe8acf13293a1d45e30aca70e9b1060f05643f26486")
        assertEquals("plsrf652c9745a92718926bc5ff2463f0d1ffd2b7c14f4a89fab", signer.address("plsr"))
        assertEquals("nbltf652c9745a92718926bc5ff2463f0d1ffd2b7c14f4a89fab", signer.address("nblt"))
        assertEquals(true, Wallet.isValidAddress(signer.address("plsr"), "plsr"))
        assertEquals(false, Wallet.isValidAddress("plsrf652c9745a92718926bc5ff2463f0d1ffd2b7c14f4a89fac", "plsr"))
    }
}
