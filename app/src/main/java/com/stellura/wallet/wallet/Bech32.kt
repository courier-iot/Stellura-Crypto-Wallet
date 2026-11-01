package com.stellura.wallet.wallet

/**
 * BIP-173 SegWit v0 address encoding. Used to build Bitcoin *testnet* (tb1…) addresses,
 * since Wallet Core's default derivation targets mainnet.
 */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val b = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) if ((b ushr i) and 1 == 1) chk = chk xor GEN[i]
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray =
        IntArray(hrp.length) { hrp[it].code ushr 5 } + intArrayOf(0) + IntArray(hrp.length) { hrp[it].code and 31 }

    private fun checksum(hrp: String, data: IntArray): IntArray {
        val pm = polymod(hrpExpand(hrp) + data + IntArray(6)) xor 1
        return IntArray(6) { (pm ushr (5 * (5 - it))) and 31 }
    }

    private fun convertBits(data: ByteArray, from: Int, to: Int): IntArray {
        var acc = 0; var bits = 0
        val out = ArrayList<Int>(); val maxv = (1 shl to) - 1
        for (b in data) {
            acc = (acc shl from) or (b.toInt() and 0xff); bits += from
            while (bits >= to) { bits -= to; out += (acc ushr bits) and maxv }
        }
        if (bits > 0) out += (acc shl (to - bits)) and maxv
        return out.toIntArray()
    }

    fun segwitV0(hrp: String, program: ByteArray): String {
        val data = intArrayOf(0) + convertBits(program, 8, 5)
        val combined = data + checksum(hrp, data)
        return hrp + "1" + combined.joinToString("") { CHARSET[it].toString() }
    }
}
