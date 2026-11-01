package com.stellura.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.stellura.wallet.data.AppSettings
import com.stellura.wallet.data.CoinMarket
import com.stellura.wallet.data.Currencies
import com.stellura.wallet.data.FavLayout
import com.stellura.wallet.data.ThemeMode
import com.stellura.wallet.ui.AppViewModel
import com.stellura.wallet.ui.Routes
import com.stellura.wallet.ui.components.HexMark
import com.stellura.wallet.ui.components.SecondaryButton
import com.stellura.wallet.ui.components.PrimaryButton
import com.stellura.wallet.ui.components.Sparkline
import com.stellura.wallet.ui.components.SurfaceCard
import com.stellura.wallet.ui.components.TickerBadge
import com.stellura.wallet.ui.components.VSpace
import com.stellura.wallet.ui.theme.Mono
import com.stellura.wallet.ui.theme.S
import com.stellura.wallet.wallet.ChainClients
import java.math.BigDecimal

/** Total of on-chain testnet balances valued at mainnet prices (indicative only). */
private fun portfolioUsd(vm: AppViewModel): Double? {
    val bal = vm.balances.value
    if (bal.isEmpty()) return null
    return bal.entries.sumOf { (chain, units) ->
        val price = vm.coin(chain.priceId)?.priceUsd ?: 0.0
        ChainClients.fromUnits(units, chain.decimals).multiply(BigDecimal(price)).toDouble()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    val markets by vm.markets.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val error by vm.marketError.collectAsStateWithLifecycle()
    vm.fxTick.collectAsStateWithLifecycle().value // recompose when FX rates arrive
    var refreshing by remember { mutableStateOf(false) }

    val favs = settings.favourites.mapNotNull { id -> markets.firstOrNull { it.id == id } }
    val cur = vm.displayCurrency(settings)
    val totalUsd = remember(balances, markets) { portfolioUsd(vm) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; vm.refreshMarkets(force = true); vm.refreshBalances().invokeOnCompletion { refreshing = false } },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HexMark(28.dp)
                    Text("  STELLURA", fontFamily = Mono, fontSize = 13.sp, letterSpacing = 3.sp, color = S.c.muted, modifier = Modifier.weight(1f))
                    val dark = S.c.isDark
                    IconButton(onClick = {
                        vm.update { this.settings.setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) }
                    }) {
                        Icon(if (S.c.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            if (S.c.isDark) "Switch to light mode" else "Switch to dark mode", tint = S.c.text)
                    }
                }
                VSpace(16.dp)
                Text(buildAnnotatedString {
                    append("Welcome, ")
                    withStyle(SpanStyle(color = S.c.accent)) { append(settings.username.ifBlank { "USER" }) }
                }, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, color = S.c.text)
                VSpace(18.dp)
                PortfolioCard(vm, settings, cur, totalUsd, nav)
                VSpace(22.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your favourites", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = S.c.text, modifier = Modifier.weight(1f))
                    LayoutToggle(settings.layout) { vm.update { this.settings.setLayout(it) } }
                }
                error?.let { Text(it, color = S.c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                if (favs.isEmpty() && error == null) {
                    Text(if (markets.isEmpty()) "Loading prices…" else "No favourites yet. Star coins in Markets.",
                        color = S.c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                }
                VSpace(8.dp)
            }
            if (settings.layout == FavLayout.LIST) {
                items(favs, key = { it.id }) { c -> CoinRow(c, vm, settings, cur) { nav.navigate(Routes.coin(c.id)) } }
            } else {
                items(favs.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { c -> CoinTile(c, vm, settings, cur, Modifier.weight(1f)) { nav.navigate(Routes.coin(c.id)) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioCard(vm: AppViewModel, s: AppSettings, cur: String, totalUsd: Double?, nav: NavHostController) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Portfolio · $cur", color = S.c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.update { this.settings.setHideBalances(!s.hideBalances) } }) {
                    Icon(if (s.hideBalances) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (s.hideBalances) "Show balances" else "Hide balances", tint = S.c.muted)
                }
            }
            Text(
                totalUsd?.let { Currencies.format(vm.toFiat(it, s), cur, s.hideBalances) } ?: "—",
                fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = S.c.text
            )
            Text("Testnet balances, valued at real prices for reference", color = S.c.muted, fontSize = 12.sp)
            VSpace(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Send", Modifier.weight(1f)) { nav.navigate(Routes.SEND) }
                SecondaryButton("Receive", Modifier.weight(1f)) { nav.navigate(Routes.WALLETS) }
            }
        }
    }
}

@Composable
private fun LayoutToggle(current: FavLayout, onChange: (FavLayout) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, S.c.line, RoundedCornerShape(10.dp)).background(S.c.surface).padding(2.dp)) {
        listOf(FavLayout.LIST to Icons.AutoMirrored.Filled.ViewList, FavLayout.TILES to Icons.Filled.GridView).forEach { (mode, icon) ->
            val on = current == mode
            Box(
                Modifier.size(44.dp, 36.dp).clip(RoundedCornerShape(8.dp)).background(if (on) S.c.accent else S.c.surface)
                    .clickable { onChange(mode) },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, if (mode == FavLayout.LIST) "List view" else "Tile view", tint = if (on) S.c.onAccent else S.c.muted, modifier = Modifier.size(18.dp)) }
        }
    }
}

private fun pct(v: Double) = (if (v >= 0) "+" else "−") + "%.2f%%".format(kotlin.math.abs(v))

@Composable
fun CoinRow(c: CoinMarket, vm: AppViewModel, s: AppSettings, cur: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val col = if (c.change24hPct >= 0) S.c.up else S.c.down
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickerBadge(c.symbol)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = S.c.text, fontSize = 15.sp, maxLines = 1)
            Text(c.symbol, fontFamily = Mono, fontSize = 12.sp, color = S.c.muted)
        }
        Sparkline(c.sparkline7d, col, Modifier.size(72.dp, 28.dp))
        Column(Modifier.width(100.dp), horizontalAlignment = Alignment.End) {
            Text(Currencies.format(vm.toFiat(c.priceUsd, s), cur), fontFamily = Mono, fontSize = 14.sp, color = S.c.text, maxLines = 1)
            Text(pct(c.change24hPct), fontFamily = Mono, fontSize = 12.sp, color = col)
        }
        trailing?.invoke()
    }
}

@Composable
private fun CoinTile(c: CoinMarket, vm: AppViewModel, s: AppSettings, cur: String, modifier: Modifier, onClick: () -> Unit) {
    val col = if (c.change24hPct >= 0) S.c.up else S.c.down
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(S.c.surface).border(1.dp, S.c.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Row {
            Text(c.symbol, fontFamily = Mono, fontSize = 13.sp, color = S.c.text, modifier = Modifier.weight(1f))
            Text(pct(c.change24hPct), fontFamily = Mono, fontSize = 11.sp, color = col)
        }
        Text(c.name, fontSize = 12.sp, color = S.c.muted, maxLines = 1)
        Sparkline(c.sparkline7d, col, Modifier.fillMaxWidth().height(36.dp).padding(vertical = 6.dp))
        Text(Currencies.format(vm.toFiat(c.priceUsd, s), cur), fontFamily = Mono, fontSize = 15.sp, color = S.c.text, maxLines = 1)
    }
}

/** Top 100 by market cap; star to add or remove favourites. */
@Composable
fun MarketsScreen(vm: AppViewModel, nav: NavHostController) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    val markets by vm.markets.collectAsStateWithLifecycle()
    vm.fxTick.collectAsStateWithLifecycle().value
    var q by remember { mutableStateOf("") }
    val cur = vm.displayCurrency(settings)
    val shown = markets.filter { q.isBlank() || it.name.contains(q, true) || it.symbol.contains(q, true) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("Markets", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text)
            Text("Top 100 by market cap · 7-day trend", color = S.c.muted, fontSize = 13.sp)
            VSpace(12.dp)
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Search coins") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
            )
            VSpace(8.dp)
            if (markets.isEmpty()) Text("Loading…", color = S.c.muted, modifier = Modifier.padding(top = 12.dp))
        }
        items(shown, key = { it.id }) { c ->
            val fav = c.id in settings.favourites
            CoinRow(c, vm, settings, cur, trailing = {
                IconButton(onClick = {
                    val next = if (fav) settings.favourites - c.id else settings.favourites + c.id
                    vm.update { this.settings.setFavourites(next) }
                }) {
                    Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (fav) "Remove ${c.name} from favourites" else "Add ${c.name} to favourites",
                        tint = if (fav) S.c.accent else S.c.muted)
                }
            }) { nav.navigate(Routes.coin(c.id)) }
        }
    }
}
