package com.stellura.wallet.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.stellura.wallet.data.Currencies
import com.stellura.wallet.data.PricePoint
import com.stellura.wallet.data.PriceRepository
import com.stellura.wallet.data.Timeframe
import com.stellura.wallet.ui.AppViewModel
import com.stellura.wallet.ui.Routes
import com.stellura.wallet.ui.components.PrimaryButton
import com.stellura.wallet.ui.components.SecondaryButton
import com.stellura.wallet.ui.components.SurfaceCard
import com.stellura.wallet.ui.components.VSpace
import com.stellura.wallet.ui.theme.Mono
import com.stellura.wallet.ui.theme.S
import com.stellura.wallet.wallet.Chain
import com.stellura.wallet.wallet.ChainClients
import java.math.BigDecimal

@Composable
fun CoinDetailScreen(vm: AppViewModel, nav: NavHostController, id: String) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    val markets by vm.markets.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val coin = markets.firstOrNull { it.id == id }
    val cur = vm.displayCurrency(settings)
    val tf = settings.timeframe
    var hist by remember { mutableStateOf<PriceRepository.History?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(coin?.id, tf) {
        if (coin == null) return@LaunchedEffect
        hist = null; err = null
        runCatching { vm.app.prices.history(coin, tf) }
            .onSuccess { hist = it }
            .onFailure { err = "Couldn't load price history. Try again shortly." }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = S.c.text) }
            Column(Modifier.weight(1f)) {
                Text(coin?.name ?: id, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = S.c.text)
                coin?.let { Text("${it.symbol} · #${it.rank}", fontFamily = Mono, fontSize = 12.sp, color = S.c.muted) }
            }
            val fav = id in settings.favourites
            IconButton(onClick = {
                val next = if (fav) settings.favourites - id else settings.favourites + id
                vm.update { this.settings.setFavourites(next) }
            }) {
                Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                    if (fav) "Remove from favourites" else "Add to favourites", tint = if (fav) S.c.accent else S.c.muted)
            }
        }
        if (coin == null) { Text("Loading…", color = S.c.muted); return@Column }

        VSpace(12.dp)
        Text(Currencies.format(vm.toFiat(coin.priceUsd, settings), cur), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = S.c.text)
        val pts = hist?.points.orEmpty()
        if (pts.size >= 2) {
            val first = pts.first().priceUsd; val last = pts.last().priceUsd
            val chg = (last - first) / first * 100
            val shownTf = hist?.truncatedTo ?: tf
            Text(
                (if (chg >= 0) "▲ +" else "▼ −") + "%.1f%% · %s".format(kotlin.math.abs(chg), shownTf.label),
                fontFamily = Mono, fontSize = 13.sp, color = if (chg >= 0) S.c.up else S.c.down
            )
        }

        VSpace(16.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Price history", color = S.c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Box {
                OutlinedButton(
                    onClick = { menu = true }, shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = S.c.surface, contentColor = S.c.text),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (menu) S.c.accent else S.c.line),
                ) {
                    Text(tf.label); Spacer(Modifier.size(6.dp))
                    Icon(Icons.Filled.KeyboardArrowDown, "Choose timeframe", Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(S.c.surface2)) {
                    Timeframe.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.label, color = S.c.text) },
                            trailingIcon = { if (t == tf) Icon(Icons.Filled.Check, "Selected", tint = S.c.accent) },
                            onClick = { menu = false; vm.update { this.settings.setTimeframe(t) } },
                        )
                    }
                }
            }
        }
        VSpace(8.dp)
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            when {
                err != null -> Text(err!!, color = S.c.muted, fontSize = 13.sp)
                pts.size < 2 -> Text("Loading chart…", color = S.c.muted, fontSize = 13.sp)
                else -> PriceChart(pts)
            }
        }
        hist?.truncatedTo?.let {
            Text("Longer history isn't available for ${coin.symbol}; showing ${it.label}.", color = S.c.muted, fontSize = 12.sp)
        }

        // Holdings, for coins this build can hold.
        Chain.entries.firstOrNull { it.priceId == coin.id }?.let { chain ->
            val units = balances[chain]
            VSpace(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("You hold (${chain.network})", units?.let { "${ChainClients.fromUnits(it, chain.decimals).toPlainString()} ${chain.ticker}" } ?: "—", Modifier.weight(1f))
                StatBox("Indicative value", units?.let {
                    Currencies.format(vm.toFiat(ChainClients.fromUnits(it, chain.decimals).multiply(BigDecimal(coin.priceUsd)).toDouble(), settings), cur, settings.hideBalances)
                } ?: "—", Modifier.weight(1f))
            }
            VSpace(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chain == Chain.ETHEREUM) PrimaryButton("Send", Modifier.weight(1f)) { nav.navigate(Routes.SEND) }
                SecondaryButton("Receive", Modifier.weight(1f)) { nav.navigate(Routes.receive(chain.name)) }
            }
        }

        VSpace(16.dp)
        SurfaceCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = S.c.up)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Staking & price alerts", fontWeight = FontWeight.SemiBold, color = S.c.text)
                    Text("Coming in the next test build", fontSize = 12.sp, color = S.c.muted)
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(S.c.surface).border(1.dp, S.c.line, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(label, fontSize = 12.sp, color = S.c.muted, maxLines = 1)
        Text(value, fontFamily = Mono, fontSize = 14.sp, color = S.c.text, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PriceChart(points: List<PricePoint>) {
    val up = points.last().priceUsd >= points.first().priceUsd
    val color = if (up) S.c.up else S.c.down
    val grid = S.c.line
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = if (up) "Price chart, rising" else "Price chart, falling" }) {
        listOf(0.25f, 0.5f, 0.75f).forEach { f -> drawLine(grid, androidx.compose.ui.geometry.Offset(0f, size.height * f), androidx.compose.ui.geometry.Offset(size.width, size.height * f)) }
        val min = points.minOf { it.priceUsd }; val max = points.maxOf { it.priceUsd }
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val t0 = points.first().timeMs; val tr = (points.last().timeMs - t0).coerceAtLeast(1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = (p.timeMs - t0).toFloat() / tr * size.width
            val y = (size.height - (p.priceUsd - min) / range * size.height * 0.9 - size.height * 0.05).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    }
}
