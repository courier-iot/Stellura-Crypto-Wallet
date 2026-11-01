package com.stellura.wallet.stellarite

import android.content.Context

/**
 * Non-secret Stellarite settings: node addresses, mining options and the wallet's two public
 * addresses (cached so mining can run while the wallet is locked). Plain SharedPreferences so
 * the mining service can read them synchronously. No keys are ever stored here.
 */
class StelSettings(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("stellarite", Context.MODE_PRIVATE)

    fun node(c: StelCoin): String = sp.getString("node_${c.id}", "")!!
    fun setNode(c: StelCoin, url: String) = sp.edit().putString("node_${c.id}", url.trim().trimEnd('/')).apply()

    fun address(c: StelCoin): String? = sp.getString("addr_${c.id}", null)
    fun setAddress(c: StelCoin, a: String) = sp.edit().putString("addr_${c.id}", a).apply()

    var mineCoin: StelCoin
        get() = StelCoin.byId(sp.getString("mine_coin", "plsr")!!) ?: StelCoin.PLSR
        set(v) = sp.edit().putString("mine_coin", v.id).apply()

    /** Solo mode: mine a blockchain kept on this phone instead of a node. */
    fun solo(c: StelCoin): Boolean = sp.getBoolean("solo_${c.id}", true)
    fun setSolo(c: StelCoin, v: Boolean) = sp.edit().putBoolean("solo_${c.id}", v).apply()

    var threads: Int
        get() = sp.getInt("threads", defaultThreads()).coerceIn(1, maxThreads())
        set(v) = sp.edit().putInt("threads", v.coerceIn(1, maxThreads())).apply()

    var chargingOnly: Boolean
        get() = sp.getBoolean("charging_only", true)
        set(v) = sp.edit().putBoolean("charging_only", v).apply()

    /** Username written into mined blocks (claims it on that network). Empty = none. */
    var miningName: String
        get() = sp.getString("mining_name", "")!!
        set(v) = sp.edit().putString("mining_name", v.trim().uppercase()).apply()

    fun clear() = sp.edit().clear().apply()

    companion object {
        fun maxThreads() = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        fun defaultThreads() = (maxThreads() / 2).coerceAtLeast(1)

        /** The wallet only talks to nodes over HTTPS (see network_security_config.xml). */
        fun nodeProblem(url: String): String? = when {
            url.isBlank() -> "No node set"
            !url.startsWith("https://") -> "Use an https:// address. Stellura only connects over HTTPS (see DEPLOY.md in Stellarite Miner for a cloud node with HTTPS)."
            else -> null
        }
    }
}
