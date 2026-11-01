package com.stellura.wallet.stellarite

import com.example.stellariteminer.KeySigner
import com.example.stellariteminer.NodeClient
import com.example.stellariteminer.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** An address's state on a Stellarite node. */
data class StelAccount(val balanceUnits: Long, val name: String?, val nextNonce: Long)

/**
 * Talks to a Pulsarite or Nebulite node (the Flask API in Stellarite Miner) through the
 * shared core's [NodeClient]. Every call checks the node really is that coin's node, so
 * PLSR can't be sent to a Nebulite network by mistake.
 */
class StelClient(private val settings: StelSettings) {

    private fun node(c: StelCoin): NodeClient {
        val url = settings.node(c)
        StelSettings.nodeProblem(url)?.let { throw IOException(it) }
        return NodeClient(url)
    }

    private suspend fun get(c: StelCoin, path: String): JSONObject = withContext(Dispatchers.IO) {
        val (code, body) = node(c).request("GET", path, null)
        if (code !in 200..299) throw IOException(body.optString("error", "HTTP $code from the ${c.label} node"))
        body
    }

    /** /info, after checking the node's ticker. */
    suspend fun info(c: StelCoin): JSONObject {
        val info = get(c, "/info")
        val ticker = info.optString("ticker")
        if (ticker != c.ticker) throw IOException("That node is a $ticker node, not ${c.ticker}")
        return info
    }

    suspend fun account(c: StelCoin, address: String): StelAccount {
        val b = get(c, "/balance/" + URLEncoder.encode(address, "UTF-8"))
        return StelAccount(b.getLong("balance"), b.optString("name").takeIf { it.isNotBlank() && it != "null" }, b.getLong("next_nonce"))
    }

    /** An address, or "@NAME" / "NAME" looked up on the node. Returns the address. */
    suspend fun resolve(c: StelCoin, recipient: String): String {
        val r = recipient.trim()
        if (Wallet.isValidAddress(r, c.prefix)) return r
        val name = r.removePrefix("@").uppercase()
        if (name.isEmpty() || name.startsWith(c.prefix)) throw IOException("That isn't a valid ${c.ticker} address")
        val o = get(c, "/name/" + URLEncoder.encode(name, "UTF-8"))
        return o.getString("address")
    }

    /** Signs and submits a payment. Returns the transaction id. */
    suspend fun send(c: StelCoin, signer: KeySigner, to: String, amountUnits: Long, feeUnits: Long): String {
        require(Wallet.isValidAddress(to, c.prefix)) { "That isn't a valid ${c.ticker} address" }
        require(amountUnits > 0) { "Amount must be more than zero" }
        info(c)
        val me = signer.address(c.prefix)
        val nonce = account(c, me).nextNonce
        val tx = withContext(Dispatchers.Default) { signer.signTransaction(to, amountUnits, feeUnits, nonce) }
        return submit(c, tx).getString("txid")
    }

    /** Claims a username: a few seconds of anti-spam proof of work, then a signed, fee-free claim. */
    suspend fun claimName(c: StelCoin, signer: KeySigner, name: String, progress: (Long) -> Unit = {}) {
        val clean = name.trim().removePrefix("@").uppercase()
        require(NAME_RE.matches(clean)) { "Names are 3–16 letters, digits or _" }
        val info = info(c)
        val nonce = account(c, signer.address(c.prefix)).nextNonce
        val claim = withContext(Dispatchers.Default) {
            signer.signNameClaim(clean, nonce, info.optInt("name_pow_bits", 22), progress)
        } ?: throw IOException("Cancelled")
        submit(c, claim)
    }

    /** The node's minimum fee (units). */
    suspend fun minFee(c: StelCoin): Long = info(c).optLong("min_fee", StelCoin.DEFAULT_FEE_UNITS)

    private suspend fun submit(c: StelCoin, tx: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val (code, body) = node(c).request("POST", "/tx", tx)
        if (code !in 200..299 || !body.optBoolean("ok")) throw IOException(body.optString("error", "HTTP $code"))
        body
    }

    companion object {
        val NAME_RE = Regex("[A-Z0-9_]{3,16}")
    }
}
