package com.stellura.wallet.data

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class FavLayout { LIST, TILES }

/** Chart ranges offered in the coin-detail dropdown, longest first. */
enum class Timeframe(val label: String, val days: Int) {
    Y3("3 years", 1095),
    Y2("2 years", 730),
    Y1("1 year", 365),
    M6("6 months", 180),
    M3("3 months", 90),
    M2("2 months", 60),
    M1("1 month", 30),
    W4("4 weeks", 28),
    W3("3 weeks", 21),
    W2("2 weeks", 14),
    W1("1 week", 7),
}

data class AppSettings(
    val onboarded: Boolean = false,
    val username: String = "",
    val theme: ThemeMode = ThemeMode.DARK,
    val layout: FavLayout = FavLayout.LIST,
    val currency: String = "CAD",
    val timeframe: Timeframe = Timeframe.M1,
    val autoLockSeconds: Int = 60,
    val lockOnScreenOff: Boolean = true,
    val biometricUnlock: Boolean = false,
    val hideBalances: Boolean = false,
    val blockScreenshots: Boolean = true,
    /** CoinGecko ids, in the user's order. */
    val favourites: List<String> = listOf("bitcoin", "ethereum", "solana", "ripple", "cardano"),
)

data class CoinMarket(
    val id: String,
    val symbol: String,
    val name: String,
    val rank: Int,
    val priceUsd: Double,
    val change24hPct: Double,
    val sparkline7d: List<Double>,
)

data class PricePoint(val timeMs: Long, val priceUsd: Double)
