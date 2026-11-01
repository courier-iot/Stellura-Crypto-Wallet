package com.stellura.wallet

import com.stellura.wallet.security.TotpManager
import com.stellura.wallet.wallet.Bech32
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
