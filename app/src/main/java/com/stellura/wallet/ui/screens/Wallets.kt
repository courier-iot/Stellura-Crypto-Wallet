package com.stellura.wallet.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.stellura.wallet.security.Biometrics
import com.stellura.wallet.ui.AppViewModel
import com.stellura.wallet.ui.Routes
import com.stellura.wallet.ui.components.PrimaryButton
import com.stellura.wallet.ui.components.QrCode
import com.stellura.wallet.ui.components.SecondaryButton
import com.stellura.wallet.ui.components.SurfaceCard
import com.stellura.wallet.ui.components.VSpace
import com.stellura.wallet.ui.theme.Mono
import com.stellura.wallet.ui.theme.S
import com.stellura.wallet.wallet.Chain
import com.stellura.wallet.wallet.ChainClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

private fun short(a: String) = if (a.length > 16) a.take(8) + "…" + a.takeLast(6) else a

@Composable
fun WalletsScreen(vm: AppViewModel, nav: NavHostController) {
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Wallets", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text)
        Text("One recovery phrase, every chain. Testnet coins only.", color = S.c.muted, fontSize = 13.sp)
        VSpace(16.dp)
        Chain.entries.forEach { chain ->
            SurfaceCard(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { nav.navigate(Routes.receive(chain.name)) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(chain.label, fontWeight = FontWeight.SemiBold, color = S.c.text)
                            Text(chain.network, fontFamily = Mono, fontSize = 12.sp, color = S.c.up)
                        }
                        Text(
                            balances[chain]?.let { "${ChainClients.fromUnits(it, chain.decimals).toPlainString()} ${chain.ticker}" } ?: "…",
                            fontFamily = Mono, fontSize = 15.sp, color = S.c.text
                        )
                    }
                    VSpace(6.dp)
                    Text(addresses[chain]?.let(::short) ?: "Deriving address…", fontFamily = Mono, fontSize = 12.sp, color = S.c.muted)
                }
            }
        }
        VSpace(6.dp)
        Text(
            "More chains (Polygon, Base, Arbitrum, BNB Chain, XRP, Cardano, Dogecoin and the rest of the top 100) " +
                "are added chain by chain. Swaps, buying and exchange accounts follow; see the roadmap.",
            color = S.c.muted, fontSize = 12.sp
        )
    }
}

@Composable
fun ReceiveScreen(vm: AppViewModel, nav: NavHostController, chainName: String) {
    val chain = runCatching { Chain.valueOf(chainName) }.getOrNull() ?: Chain.ETHEREUM
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val addr = addresses[chain]
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = S.c.text) }
            Text("Receive ${chain.ticker}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = S.c.text)
        }
        VSpace(8.dp)
        Text("${chain.label} · ${chain.network}", fontFamily = Mono, fontSize = 12.sp, color = S.c.up)
        VSpace(20.dp)
        if (addr == null) { Text("Deriving address…", color = S.c.muted); return@Column }
        QrCode(addr, 240.dp)
        VSpace(16.dp)
        Text(addr, fontFamily = Mono, fontSize = 14.sp, color = S.c.text, modifier = Modifier.padding(horizontal = 8.dp))
        VSpace(16.dp)
        SecondaryButton(if (copied) "Copied" else "Copy address", Modifier.fillMaxWidth()) {
            clip.setText(AnnotatedString(addr)); copied = true
        }
        VSpace(16.dp)
        Warning("Only send ${chain.network} ${chain.ticker} to this address. Real (mainnet) coins sent here would be lost.")
    }
}

/** ETH on Sepolia in this build. Confirms with biometrics/PIN-backed lock state. */
@Composable
fun SendScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val chain = Chain.ETHEREUM
    var to by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf<java.math.BigInteger?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var txHash by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { fee = runCatching { vm.app.chains.ethFeeEstimateWei() }.getOrNull() }

    val bal = balances[chain]
    val amt = amount.toBigDecimalOrNull()
    val wei = amt?.let { ChainClients.toUnits(it, chain.decimals) }
    val addrOk = to.isNotBlank() && vm.app.chains.isValidAddress(chain, to.trim())
    val enough = wei != null && bal != null && fee != null && wei.signum() > 0 && wei + fee!! <= bal
    val settings = vm.settings.collectAsStateWithLifecycle().value

    fun doSend() {
        busy = true; status = "Signing and broadcasting…"
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { vm.app.wallet.ethPrivateKey() }.let { vm.app.chains.sendEth(it, to.trim(), wei!!) } }
                .onSuccess { txHash = it; status = "Sent. It can take a minute to confirm."; vm.refreshBalances() }
                .onFailure { status = "Send failed: ${it.message ?: "unknown error"}" }
            busy = false; confirming = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = S.c.text) }
            Text("Send ETH", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = S.c.text)
        }
        Text("Sepolia testnet", fontFamily = Mono, fontSize = 12.sp, color = S.c.up, modifier = Modifier.padding(start = 12.dp))
        VSpace(16.dp)
        OutlinedTextField(
            value = to, onValueChange = { to = it }, label = { Text("Recipient address (0x…)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
            isError = to.isNotBlank() && !addrOk,
            trailingIcon = {
                val clip = LocalClipboardManager.current
                IconButton(onClick = { clip.getText()?.text?.let { to = it.trim() } }) { Icon(Icons.Filled.ContentPaste, "Paste") }
            },
        )
        VSpace(10.dp)
        OutlinedTextField(
            value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } }, label = { Text("Amount (ETH)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Balance: ${bal?.let { ChainClients.fromUnits(it, 18).toPlainString() } ?: "…"} ETH", fontSize = 12.sp, color = S.c.muted)
            Text("Fee ≈ ${fee?.let { ChainClients.fromUnits(it, 18).setScale(6, java.math.RoundingMode.UP).toPlainString() } ?: "…"} ETH", fontSize = 12.sp, color = S.c.muted)
        }
        if (amt != null && bal != null && fee != null && !enough && amt > BigDecimal.ZERO) {
            Text("Not enough ETH to cover amount + fee.", color = S.c.down, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        VSpace(20.dp)
        if (!confirming) {
            PrimaryButton("Review", Modifier.fillMaxWidth(), enabled = addrOk && enough && !busy) { confirming = true }
        } else {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Confirm send", fontWeight = FontWeight.SemiBold, color = S.c.text)
                    VSpace(8.dp)
                    Text("$amount ETH  →  ${short(to.trim())}", fontFamily = Mono, color = S.c.text)
                    Text("Network fee ≈ ${fee?.let { ChainClients.fromUnits(it, 18).toPlainString() }} ETH", fontSize = 12.sp, color = S.c.muted)
                    VSpace(12.dp)
                    PrimaryButton(if (busy) "Sending…" else "Confirm & send", Modifier.fillMaxWidth(), enabled = !busy) {
                        val act = ctx as? FragmentActivity
                        if (settings?.biometricUnlock == true && act != null && Biometrics.available(act)) {
                            Biometrics.prompt(act, "Confirm send", onSuccess = { doSend() })
                        } else doSend()
                    }
                    TextButton(onClick = { confirming = false }, enabled = !busy) { Text("Edit", color = S.c.up) }
                }
            }
        }
        status?.let { Text(it, color = S.c.text, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
        txHash?.let { h ->
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(chain.explorerTx + h))) }) {
                Text("View on block explorer", color = S.c.up)
            }
        }
        Spacer(Modifier.size(24.dp))
    }
}
