package com.stellura.wallet.data

import com.stellura.wallet.net.Http
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient

/**
 * Public, key-less price data. All prices are fetched in USD and converted on-device
 * with [FxRepository], which is how we support 75 fiat currencies.
 *
 * - Top-100 list + 7-day sparklines: CoinGecko public API
 * - History up to 1 year: CoinGecko (its free tier stops at 365 days)
 * - History for 2 and 3 years: Coinbase Exchange public daily candles
 */
class PriceRepository(private val http: OkHttpClient, val fx: FxRepository) {

    private val mutex = Mutex()
    private var cachedMarkets: List<CoinMarket> = emptyList()
    private var cachedAt = 0L

    suspend fun topMarkets(force: Boolean = false): List<CoinMarket> = mutex.withLock {
        val fresh = System.currentTimeMillis() - cachedAt < 60_000
        if (!force && fresh && cachedMarkets.isNotEmpty()) return cachedMarkets
        val url = "$GECKO/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1" +
            "&sparkline=true&price_change_percentage=24h"
        val arr = Http.getJson(http, url).jsonArray
        cachedMarkets = arr.mapNotNull { el ->
            val o = el.jsonObject
            runCatching {
                CoinMarket(
                    id = o.str("id"),
                    symbol = o.str("symbol").uppercase(),
                    name = o.str("name"),
                    rank = o["market_cap_rank"]?.jsonPrimitive?.intOrNull ?: 0,
                    priceUsd = o["current_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    change24hPct = o["price_change_percentage_24h"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    sparkline7d = (o["sparkline_in_7d"] as? JsonObject)?.get("price")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList(),
                )
            }.getOrNull()
        }
        cachedAt = System.currentTimeMillis()
        cachedMarkets
    }

    data class History(val points: List<PricePoint>, val truncatedTo: Timeframe? = null)

    suspend fun history(coin: CoinMarket, tf: Timeframe): History {
        if (tf.days <= 365) return History(gecko(coin.id, tf.days))
        return runCatching { History(coinbase(coin.symbol, tf.days)) }
            .getOrElse { History(gecko(coin.id, 365), truncatedTo = Timeframe.Y1) }
    }

    private suspend fun gecko(id: String, days: Int): List<PricePoint> {
        val o = Http.getJson(http, "$GECKO/coins/$id/market_chart?vs_currency=usd&days=$days").jsonObject
        return o["prices"]!!.jsonArray.map {
            val p = it.jsonArray
            PricePoint(p[0].jsonPrimitive.long, p[1].jsonPrimitive.double)
        }
    }

    /** Coinbase returns at most 300 candles per call: page backwards in 300-day windows. */
    private suspend fun coinbase(symbol: String, days: Int): List<PricePoint> {
        val out = ArrayList<PricePoint>()
        val dayS = 86_400L
        var end = System.currentTimeMillis() / 1000
        val stop = end - days * dayS
        while (end > stop) {
            val start = maxOf(stop, end - 299 * dayS)
            val url = "https://api.exchange.coinbase.com/products/$symbol-USD/candles" +
                "?granularity=86400&start=${iso(start)}&end=${iso(end)}"
            val arr = Http.getJson(http, url) as JsonArray
            if (arr.isEmpty()) break
            // [time, low, high, open, close, volume]
            arr.forEach { c -> val a = c.jsonArray; out += PricePoint(a[0].jsonPrimitive.long * 1000, a[4].jsonPrimitive.double) }
            end = start - dayS
        }
        if (out.size < days / 2) error("Not enough Coinbase history for $symbol")
        return out.sortedBy { it.timeMs }
    }

    private fun iso(epochS: Long) = java.time.Instant.ofEpochSecond(epochS).toString()

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content

    companion object { private const val GECKO = "https://api.coingecko.com/api/v3" }
}

/** USD → any fiat. Uses the free open.er-api.com endpoint; cached for an hour. */
class FxRepository(private val http: OkHttpClient) {
    private val mutex = Mutex()
    private var rates: Map<String, Double> = mapOf("USD" to 1.0)
    private var fetchedAt = 0L

    suspend fun refresh() {
        mutex.withLock {
            if (System.currentTimeMillis() - fetchedAt < 3_600_000 && rates.size > 1) return
            try {
                val o = Http.getJson(http, "https://open.er-api.com/v6/latest/USD").jsonObject
                rates = o["rates"]!!.jsonObject.mapValues { it.value.jsonPrimitive.double }
                fetchedAt = System.currentTimeMillis()
            } catch (e: Exception) {
                // Keep the old rates; the UI falls back to USD if none have loaded.
            }
        }
    }

    fun hasRate(code: String) = rates.containsKey(code)

    /** Returns USD unchanged if the rate isn't available yet; check [hasRate]. */
    fun convert(usd: Double, code: String): Double = usd * (rates[code] ?: 1.0)
}
