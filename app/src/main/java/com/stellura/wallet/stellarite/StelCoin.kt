package com.stellura.wallet.stellarite

import com.example.stellariteminer.Coin
import com.example.stellariteminer.CoinProfile
import com.example.stellariteminer.NebuliteCoin

/**
 * The two Stellarite learning coins. They run on their own test networks (your own
 * nodes), have no market value, and are kept apart from the real-asset chains in [com.stellura.wallet.wallet.Chain]:
 * own key path, own screens, never counted in the portfolio total.
 */
enum class StelCoin(
    val id: String,              // the miner's coin id (CoinProfile)
    val label: String,
    val ticker: String,
    val prefix: String,          // address prefix
    val work: String,            // what mining a block involves
) {
    PLSR("plsr", "Pulsarite", Coin.TICKER, Coin.ADDRESS_PREFIX, "star routes between nearby stars"),
    NBLT("nblt", "Nebulite", NebuliteCoin.TICKER, NebuliteCoin.ADDRESS_PREFIX, "spacecraft trajectories through the solar system");

    val profile: CoinProfile get() = CoinProfile.byId(id)

    companion object {
        const val DECIMALS = 8                 // 1 coin = 100,000,000 units, like satoshis
        const val UNITS_PER_COIN = 100_000_000L
        const val DEFAULT_FEE_UNITS = 1_000L   // the nodes' MIN_FEE (0.00001); /info's min_fee wins when present
        const val NETWORK = "Stellarite test network"

        fun byId(id: String) = entries.firstOrNull { it.id == id || it.name == id }
    }
}
