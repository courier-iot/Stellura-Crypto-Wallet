package com.stellura.wallet.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.stellariteminer.SoloBackend
import com.stellura.wallet.security.Biometrics
import com.stellura.wallet.stellarite.StelCoin
import com.stellura.wallet.stellarite.StelMiner
import com.stellura.wallet.stellarite.StelSettings
import com.stellura.wallet.ui.AppViewModel
import com.stellura.wallet.ui.Routes
import com.stellura.wallet.ui.components.PrimaryButton
import com.stellura.wallet.ui.components.QrCode
import com.stellura.wallet.ui.components.SectionLabel
import com.stellura.wallet.ui.components.SecondaryButton
import com.stellura.wallet.ui.components.SurfaceCard
import com.stellura.wallet.ui.components.VSpace
import com.stellura.wallet.ui.theme.Mono
import com.stellura.wallet.ui.theme.S
import com.stellura.wallet.wallet.ChainClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

// ---- Stellarite test coins (Pulsarite, Nebulite): their own section, key path and screens ----

private fun coins(units: Long): String = ChainClients.fromUnits(BigInteger.valueOf(units), StelCoin.DECIMALS).toPlainString()

private fun shortAddr(a: String) = if (a.length > 16) a.take(10) + "…" + a.takeLast(6) else a

@Composable
private fun BackHeader(nav: NavHostController, title: String, sub: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = S.c.text) }
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = S.c.text)
            Text(sub, fontFamily = Mono, fontSize = 12.sp, color = S.c.up)
        }
    }
}

/** The Wallets tab's Stellarite section: one card per coin. */
@Composable
fun StellariteSection(vm: AppViewModel, nav: NavHostController) {
    val addrs by vm.stelAddresses.collectAsStateWithLifecycle()
    val accounts by vm.stelAccounts.collectAsStateWithLifecycle()
    val solo by vm.stelSolo.collectAsStateWithLifecycle()
    val errors by vm.stelErrors.collectAsStateWithLifecycle()
    SectionLabel(StelCoin.NETWORK)
    Text("Your own learning coins, mined with useful space work. No market value; never counted in your portfolio.",
        color = S.c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    StelCoin.entries.forEach { c ->
        SurfaceCard(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { nav.navigate(Routes.stel(c.name)) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.label, fontWeight = FontWeight.SemiBold, color = S.c.text)
                        Text(accounts[c]?.name?.let { "@$it" } ?: StelCoin.NETWORK, fontFamily = Mono, fontSize = 12.sp, color = S.c.up)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(accounts[c]?.let { "${coins(it.balanceUnits)} ${c.ticker}" }
                            ?: if (vm.app.stelSettings.node(c).isBlank()) "No node" else "…",
                            fontFamily = Mono, fontSize = 15.sp, color = S.c.text)
                        solo[c]?.takeIf { it > 0 }?.let { Text("+ ${coins(it)} on solo chain", fontFamily = Mono, fontSize = 11.sp, color = S.c.muted) }
                    }
                }
                VSpace(6.dp)
                Text(addrs[c]?.let(::shortAddr) ?: "Deriving address…", fontFamily = Mono, fontSize = 12.sp, color = S.c.muted)
                errors[c]?.let { Text(it, fontSize = 12.sp, color = S.c.down, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

/** One coin: balance, username, receive, send, node and name claim. */
@Composable
fun StelCoinScreen(vm: AppViewModel, nav: NavHostController, coinName: String) {
    val c = StelCoin.byId(coinName) ?: StelCoin.PLSR
    val addrs by vm.stelAddresses.collectAsStateWithLifecycle()
    val accounts by vm.stelAccounts.collectAsStateWithLifecycle()
    val solo by vm.stelSolo.collectAsStateWithLifecycle()
    val errors by vm.stelErrors.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var editNode by remember { mutableStateOf(false) }
    var claiming by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val addr = addrs[c]
    val acc = accounts[c]
    val node = vm.app.stelSettings.node(c)

    LaunchedEffect(c) { vm.refreshStellarite() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        BackHeader(nav, c.label, "${c.ticker} · ${StelCoin.NETWORK}")
        VSpace(12.dp)
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column {
                Text(acc?.let { "${coins(it.balanceUnits)} ${c.ticker}" } ?: if (node.isBlank()) "Set a node to see your balance" else "…",
                    fontFamily = Mono, fontSize = 22.sp, color = S.c.text)
                acc?.name?.let { Text("@$it", fontFamily = Mono, color = S.c.up) }
                solo[c]?.takeIf { it > 0 }?.let {
                    Text("${coins(it)} ${c.ticker} mined on this phone's solo chain. Push the chain to a node (Mine tab) to spend it.",
                        fontSize = 12.sp, color = S.c.muted, modifier = Modifier.padding(top = 6.dp))
                }
                errors[c]?.let { Text(it, fontSize = 12.sp, color = S.c.down, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Send", Modifier.weight(1f), enabled = acc != null && acc.balanceUnits > 0) { nav.navigate(Routes.stelSend(c.name)) }
            SecondaryButton("Refresh", Modifier.weight(1f)) { vm.refreshStellarite() }
        }

        SectionLabel("Receive")
        if (addr == null) Text("Deriving address…", color = S.c.muted) else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                QrCode(addr, 200.dp)
                VSpace(10.dp)
                Text(addr, fontFamily = Mono, fontSize = 13.sp, color = S.c.text)
                VSpace(10.dp)
                SecondaryButton(if (copied) "Copied" else "Copy address", Modifier.fillMaxWidth()) { clip.setText(AnnotatedString(addr)); copied = true }
            }
            Text("This is also your mining address. People can send to your @name once you've claimed one.",
                fontSize = 12.sp, color = S.c.muted, modifier = Modifier.padding(top = 6.dp))
        }

        SectionLabel("Node")
        SurfaceCard(Modifier.fillMaxWidth().clickable { editNode = true }) {
            Column {
                Text(node.ifBlank { "Not set" }, fontFamily = Mono, fontSize = 13.sp, color = S.c.text)
                Text("Your ${c.label} node (HTTPS). Tap to change.", fontSize = 12.sp, color = S.c.muted)
            }
        }

        SectionLabel("Username")
        if (acc?.name != null) {
            Text("You're @${acc.name} on this network.", color = S.c.text, fontSize = 14.sp)
        } else {
            Text("Claim a name so people can pay @NAME instead of a long address. Free, with a few seconds of anti-spam work.",
                fontSize = 12.sp, color = S.c.muted)
            VSpace(8.dp)
            SecondaryButton(if (claiming) "Claiming…" else "Claim a username", Modifier.fillMaxWidth(), enabled = acc != null && !claiming) { claiming = true }
        }
        note?.let { Text(it, color = S.c.up, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        VSpace(16.dp)
        Warning("${c.ticker} is a learning coin on your own test network. Only send ${c.ticker} to this address; anything else sent here is lost.")
        Spacer(Modifier.size(24.dp))
    }

    if (editNode) NodeDialog(c, node, onDismiss = { editNode = false }) { url ->
        vm.app.stelSettings.setNode(c, url); editNode = false; vm.refreshStellarite()
    }
    if (claiming) ClaimDialog(onDismiss = { claiming = false }) { name ->
        scope.launch {
            note = "Working on the anti-spam proof (a few seconds)…"
            runCatching {
                val signer = withContext(Dispatchers.Default) { vm.app.wallet.stellariteSigner() }
                vm.app.stel.claimName(c, signer, name)
            }.onSuccess { note = "Claim sent. @${name.trim().removePrefix("@").uppercase()} is yours once the next block is mined." }
                .onFailure { note = "Claim failed: ${it.message}" }
            claiming = false
        }
    }
}

@Composable
private fun NodeDialog(c: StelCoin, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(current.ifBlank { "https://" }) }
    val problem = StelSettings.nodeProblem(v.trim())
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text("${c.label} node", color = S.c.text) },
        text = {
            Column {
                OutlinedTextField(v, { v = it.trim() }, singleLine = true, colors = stelluraFieldColors(),
                    placeholder = { Text("https://your-domain/${if (c == StelCoin.PLSR) "pulsarite" else "nebulite"}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
                problem?.let { Text(it, color = S.c.down, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }, enabled = problem == null) { Text("Save", color = S.c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = S.c.muted) } },
    )
}

@Composable
private fun ClaimDialog(onDismiss: () -> Unit, onClaim: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    val clean = v.trim().removePrefix("@").uppercase()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text("Claim a username", color = S.c.text) },
        text = {
            Column {
                OutlinedTextField(v, { v = it.take(17) }, singleLine = true, colors = stelluraFieldColors(), placeholder = { Text("NOVA_7") },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
                Text("3–16 letters, digits or _. One name per address; first come, first served.",
                    color = S.c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onClaim(clean) }, enabled = com.stellura.wallet.stellarite.StelClient.NAME_RE.matches(clean)) {
                Text("Claim", color = S.c.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = S.c.muted) } },
    )
}

/** Send PLSR or NBLT to an address or @name. Signs on the phone with the Stellarite key. */
@Composable
fun StelSendScreen(vm: AppViewModel, nav: NavHostController, coinName: String) {
    val c = StelCoin.byId(coinName) ?: StelCoin.PLSR
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by vm.stelAccounts.collectAsStateWithLifecycle()
    val settings = vm.settings.collectAsStateWithLifecycle().value
    var to by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf<Long?>(null) }
    var resolved by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(c) { fee = runCatching { vm.app.stel.minFee(c) }.getOrNull() }

    val bal = accounts[c]?.balanceUnits
    val units = amount.toBigDecimalOrNull()?.let { runCatching { ChainClients.toUnits(it, StelCoin.DECIMALS).longValueExact() }.getOrNull() }
    val enough = units != null && units > 0 && bal != null && fee != null && units + fee!! <= bal

    fun doSend() {
        val dest = resolved ?: return
        busy = true; status = "Signing and sending…"
        scope.launch {
            runCatching {
                val signer = withContext(Dispatchers.Default) { vm.app.wallet.stellariteSigner() }
                vm.app.stel.send(c, signer, dest, units!!, fee!!)
            }.onSuccess { status = "Sent (txid ${it.take(12)}…). It confirms when the next block is mined."; vm.refreshStellarite() }
                .onFailure { status = "Send failed: ${it.message ?: "unknown error"}" }
            busy = false; resolved = null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
        BackHeader(nav, "Send ${c.ticker}", StelCoin.NETWORK)
        VSpace(16.dp)
        OutlinedTextField(
            value = to, onValueChange = { to = it; resolved = null }, label = { Text("To: ${c.prefix}… address or @name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            trailingIcon = {
                val clip = LocalClipboardManager.current
                IconButton(onClick = { clip.getText()?.text?.let { to = it.trim(); resolved = null } }) { Icon(Icons.Filled.ContentPaste, "Paste") }
            },
        )
        VSpace(10.dp)
        OutlinedTextField(
            value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' }; resolved = null }, label = { Text("Amount (${c.ticker})") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Balance: ${bal?.let(::coins) ?: "…"} ${c.ticker}", fontSize = 12.sp, color = S.c.muted)
            Text("Fee: ${fee?.let(::coins) ?: "…"} ${c.ticker}", fontSize = 12.sp, color = S.c.muted)
        }
        if (units != null && bal != null && fee != null && !enough && amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true) {
            Text("Not enough ${c.ticker} to cover amount + fee.", color = S.c.down, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        VSpace(20.dp)
        if (resolved == null) {
            PrimaryButton("Review", Modifier.fillMaxWidth(), enabled = to.isNotBlank() && enough && !busy) {
                scope.launch {
                    runCatching { vm.app.stel.resolve(c, to) }
                        .onSuccess { resolved = it; status = null }
                        .onFailure { status = it.message ?: "Couldn't find that recipient" }
                }
            }
        } else {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Confirm send", fontWeight = FontWeight.SemiBold, color = S.c.text)
                    VSpace(8.dp)
                    Text("$amount ${c.ticker}  →  ${if (to.trim().startsWith(c.prefix)) shortAddr(resolved!!) else "@${to.trim().removePrefix("@").uppercase()}"}",
                        fontFamily = Mono, color = S.c.text)
                    if (!to.trim().startsWith(c.prefix)) Text(shortAddr(resolved!!), fontFamily = Mono, fontSize = 12.sp, color = S.c.muted)
                    Text("Fee ${fee?.let(::coins)} ${c.ticker}", fontSize = 12.sp, color = S.c.muted)
                    VSpace(12.dp)
                    PrimaryButton(if (busy) "Sending…" else "Confirm & send", Modifier.fillMaxWidth(), enabled = !busy) {
                        val act = ctx as? FragmentActivity
                        if (settings?.biometricUnlock == true && act != null && Biometrics.available(act)) {
                            Biometrics.prompt(act, "Confirm send", onSuccess = { doSend() })
                        } else doSend()
                    }
                    TextButton(onClick = { resolved = null }, enabled = !busy) { Text("Edit", color = S.c.up) }
                }
            }
        }
        status?.let { Text(it, color = S.c.text, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.size(24.dp))
    }
}

// ---- Mine tab ---------------------------------------------------------------------------

@Composable
private fun Pill(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) S.c.accent else S.c.surface2)
            .border(1.dp, if (on) S.c.accent else S.c.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = Mono, fontSize = 13.sp, color = if (on) S.c.onAccent else S.c.text) }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = S.c.text, modifier = Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = S.c.accent))
    }
}

/**
 * Mine Pulsarite or Nebulite on this phone, with the same engine as Stellarite Miner.
 * Blocks pay to the wallet's own Stellarite address. Solo mode keeps a chain on the phone;
 * node mode mines with your HTTPS node.
 */
@Composable
fun MineScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val app = vm.app
    val s = app.stelSettings
    val st by StelMiner.status.collectAsStateWithLifecycle()
    val addrs by vm.stelAddresses.collectAsStateWithLifecycle()
    val solos by vm.stelSolo.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var coin by remember { mutableStateOf(s.mineCoin) }
    var solo by remember(coin) { mutableStateOf(s.solo(coin)) }
    var threads by remember { mutableFloatStateOf(s.threads.toFloat()) }
    var charging by remember { mutableStateOf(s.chargingOnly) }
    var name by remember { mutableStateOf(s.miningName) }
    var note by remember { mutableStateOf<String?>(null) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val running = st.running
    val problem = if (addrs[coin] == null) "Deriving your address…" else StelMiner.problem(s, coin)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Mine", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text)
        Text("Earn Stellarite test coins with useful space work. Pays to your wallet.", color = S.c.muted, fontSize = 13.sp)
        VSpace(16.dp)

        SurfaceCard(Modifier.fillMaxWidth()) {
            Column {
                Text(if (running) "${st.coin.label} · ${if (st.solo) "solo chain" else "node"}" else "Idle",
                    fontWeight = FontWeight.SemiBold, color = S.c.text)
                Text(st.pausedReason?.let { "Paused: $it" } ?: if (running) StelMiner.formatRate(st.hashrate) else st.message,
                    fontFamily = Mono, fontSize = 20.sp, color = if (st.pausedReason != null) S.c.down else S.c.up)
                if (running) {
                    Text("Height ${st.height} · ${st.accepted} blocks · ${coins(st.earnedUnits)} ${st.coin.ticker} this session",
                        fontFamily = Mono, fontSize = 12.sp, color = S.c.muted)
                    if (st.work.isNotBlank()) Text(st.work, fontFamily = Mono, fontSize = 11.sp, color = S.c.text, modifier = Modifier.padding(top = 6.dp))
                    Text(st.message, fontSize = 12.sp, color = S.c.muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        VSpace(12.dp)
        if (running) {
            PrimaryButton("Stop mining", Modifier.fillMaxWidth()) { StelMiner.stop(ctx) }
        } else {
            PrimaryButton("Start mining", Modifier.fillMaxWidth(), enabled = problem == null) {
                s.mineCoin = coin; s.setSolo(coin, solo); s.threads = threads.toInt(); s.chargingOnly = charging; s.miningName = name
                if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                StelMiner.start(ctx)
            }
            problem?.let { Text(it, color = S.c.down, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        }

        SectionLabel("Coin")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StelCoin.entries.forEach { c -> Pill("${c.label} · ${c.ticker}", coin == c, Modifier.weight(1f)) { if (!running) coin = c } }
        }
        Text("Each block needs ${coin.work}; better plans make blocks easier to find.",
            fontSize = 12.sp, color = S.c.muted, modifier = Modifier.padding(top = 6.dp))

        SectionLabel("Where the blockchain runs")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Solo on this phone", solo, Modifier.weight(1f)) { if (!running) { solo = true; s.setSolo(coin, true) } }
            Pill("My node", !solo, Modifier.weight(1f)) { if (!running) { solo = false; s.setSolo(coin, false) } }
        }
        if (solo) {
            Text("A network of one, kept on this phone: for trying it out. ${solos[coin]?.let { "${coins(it)} ${coin.ticker} mined here so far." } ?: ""}",
                fontSize = 12.sp, color = S.c.muted, modifier = Modifier.padding(top = 6.dp))
            if (coin == StelCoin.PLSR) Text("Nebulite escape missions you mine solo also scout Pulsarite routes here.",
                fontSize = 12.sp, color = S.c.muted)
            VSpace(8.dp)
            SecondaryButton("Push solo chain to my node", Modifier.fillMaxWidth(), enabled = !running && s.node(coin).isNotBlank()) {
                scope.launch {
                    note = "Pushing…"
                    note = runCatching {
                        StelSettings.nodeProblem(s.node(coin))?.let { error(it) }
                        withContext(Dispatchers.IO) { SoloBackend.push(SoloBackend.chainFor(app, coin.profile), s.node(coin)) }
                    }.getOrElse { "Push failed: ${it.message}" }
                    vm.refreshStellarite()
                }
            }
        } else {
            Text("Node: ${s.node(coin).ifBlank { "not set (set it on the ${coin.label} card in Wallets)" }}",
                fontFamily = Mono, fontSize = 12.sp, color = S.c.muted, modifier = Modifier.padding(top = 6.dp))
        }
        note?.let { Text(it, color = S.c.up, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }

        SectionLabel("Phone")
        Text("CPU threads: ${threads.toInt()} of ${StelSettings.maxThreads()}", color = S.c.text, fontSize = 14.sp)
        if (StelSettings.maxThreads() > 1) {
            Slider(threads, { threads = it }, valueRange = 1f..StelSettings.maxThreads().toFloat(),
                steps = (StelSettings.maxThreads() - 2).coerceAtLeast(0), enabled = !running,
                colors = SliderDefaults.colors(thumbColor = S.c.accent, activeTrackColor = S.c.accent))
        }
        SwitchRow("Only mine while charging", charging, enabled = !running) { charging = it }
        Text("Mining always pauses when the phone gets hot or the battery drops below 20%.", fontSize = 12.sp, color = S.c.muted)

        SectionLabel("Username in mined blocks (optional)")
        OutlinedTextField(name, { v -> name = v.uppercase().filter { it.isLetterOrDigit() || it == '_' }.take(16) }, singleLine = true,
            enabled = !running, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
            placeholder = { Text("NOVA_7") }, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
        Text("A block you mine claims this name for your address on that network, if it's free.",
            fontSize = 12.sp, color = S.c.muted, modifier = Modifier.padding(top = 4.dp))

        VSpace(16.dp)
        Warning("Pulsarite and Nebulite are learning coins with no market value. Mining uses a lot of battery and makes the phone warm.")
        Spacer(Modifier.size(24.dp))
    }
}
