package com.stellura.wallet.wallet

import com.google.protobuf.ByteString
import com.stellura.wallet.net.Http
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import wallet.core.java.AnySigner
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.proto.Ethereum
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

/** Talks to public testnet nodes. No Stellura server is involved. */
class ChainClients(private val http: OkHttpClient) {

    /** Balance in the chain's smallest unit (wei / sats / lamports). */
    suspend fun balance(chain: Chain, address: String): BigInteger = when (chain) {
        Chain.ETHEREUM -> hexToBig(rpc(Endpoints.SEPOLIA_RPC, "eth_getBalance", "[\"$address\",\"latest\"]").jsonPrimitive.content)
        Chain.SOLANA -> BigInteger.valueOf(
            rpc(Endpoints.SOLANA_DEVNET_RPC, "getBalance", "[\"$address\"]").jsonObject["value"]!!.jsonPrimitive.long
        )
        Chain.BITCOIN -> {
            val o = Http.getJson(http, "${Endpoints.BTC_TESTNET4_API}/address/$address").jsonObject
            fun sum(k: String): Long {
                val s = o[k]!!.jsonObject
                return s["funded_txo_sum"]!!.jsonPrimitive.long - s["spent_txo_sum"]!!.jsonPrimitive.long
            }
            BigInteger.valueOf(sum("chain_stats") + sum("mempool_stats"))
        }
    }

    fun isValidAddress(chain: Chain, address: String): Boolean = when (chain) {
        Chain.ETHEREUM -> AnyAddress.isValid(address, CoinType.ETHEREUM)
        Chain.SOLANA -> AnyAddress.isValid(address, CoinType.SOLANA)
        Chain.BITCOIN -> address.lowercase().startsWith("tb1")
    }

    /** Legacy (type-0) ETH transfer on Sepolia. Returns the transaction hash. */
    suspend fun sendEth(key: PrivateKey, to: String, amountWei: BigInteger): String {
        require(isValidAddress(Chain.ETHEREUM, to)) { "That isn't a valid Ethereum address" }
        val from = AnyAddress(key.getPublicKeySecp256k1(false), CoinType.ETHEREUM).description()
        val nonce = hexToBig(rpc(Endpoints.SEPOLIA_RPC, "eth_getTransactionCount", "[\"$from\",\"pending\"]").jsonPrimitive.content)
        val gasPrice = hexToBig(rpc(Endpoints.SEPOLIA_RPC, "eth_gasPrice", "[]").jsonPrimitive.content)
            .multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN) // +20% headroom
        val gasLimit = BigInteger.valueOf(21_000)

        val input = Ethereum.SigningInput.newBuilder()
            .setChainId(BigInteger.valueOf(Endpoints.SEPOLIA_CHAIN_ID).bs())
            .setNonce(nonce.bs())
            .setGasPrice(gasPrice.bs())
            .setGasLimit(gasLimit.bs())
            .setToAddress(to)
            .setTransaction(
                Ethereum.Transaction.newBuilder().setTransfer(
                    Ethereum.Transaction.Transfer.newBuilder().setAmount(amountWei.bs()).build()
                ).build()
            )
            .setPrivateKey(ByteString.copyFrom(key.data()))
            .build()
        val out = AnySigner.sign(input, CoinType.ETHEREUM, Ethereum.SigningOutput.parser())
        if (out.encoded.isEmpty) throw IOException("Signing failed")
        val raw = "0x" + out.encoded.toByteArray().joinToString("") { "%02x".format(it) }
        return rpc(Endpoints.SEPOLIA_RPC, "eth_sendRawTransaction", "[\"$raw\"]").jsonPrimitive.content
    }

    suspend fun ethFeeEstimateWei(): BigInteger =
        hexToBig(rpc(Endpoints.SEPOLIA_RPC, "eth_gasPrice", "[]").jsonPrimitive.content)
            .multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN).multiply(BigInteger.valueOf(21_000))

    private suspend fun rpc(url: String, method: String, params: String) =
        (Http.postJson(http, url, """{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}""") as JsonObject).let { o ->
            o["error"]?.let { throw IOException(it.jsonObject["message"]?.jsonPrimitive?.content ?: "RPC error") }
            o["result"] ?: throw IOException("Empty RPC result")
        }

    companion object {
        fun hexToBig(hex: String): BigInteger = BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)

        /** Minimal big-endian bytes, as Wallet Core expects. */
        fun BigInteger.bs(): ByteString {
            var b = toByteArray()
            if (b.size > 1 && b[0] == 0.toByte()) b = b.copyOfRange(1, b.size)
            return ByteString.copyFrom(b)
        }

        fun toUnits(amount: BigDecimal, decimals: Int): BigInteger = amount.movePointRight(decimals).toBigInteger()
        fun fromUnits(value: BigInteger, decimals: Int): BigDecimal = BigDecimal(value).movePointLeft(decimals).stripTrailingZeros()
    }
}
