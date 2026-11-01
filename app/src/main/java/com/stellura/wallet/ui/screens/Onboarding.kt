package com.stellura.wallet.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.stellura.wallet.security.PinManager
import com.stellura.wallet.ui.AppViewModel
import com.stellura.wallet.ui.Routes
import com.stellura.wallet.ui.components.HexMark
import com.stellura.wallet.ui.components.PinDots
import com.stellura.wallet.ui.components.PinPad
import com.stellura.wallet.ui.components.PrimaryButton
import com.stellura.wallet.ui.components.SecondaryButton
import com.stellura.wallet.ui.components.VSpace
import com.stellura.wallet.ui.theme.Mono
import com.stellura.wallet.ui.theme.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun stelluraFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = S.c.accent, unfocusedBorderColor = S.c.line,
    focusedContainerColor = S.c.surface, unfocusedContainerColor = S.c.surface,
    focusedTextColor = S.c.text, unfocusedTextColor = S.c.text, cursorColor = S.c.accent,
    focusedLabelColor = S.c.muted, unfocusedLabelColor = S.c.muted,
)

@Composable
private fun TestnetBadge() {
    Text(
        "TESTNET BUILD", fontFamily = Mono, fontSize = 11.sp, color = S.c.up,
        modifier = Modifier.border(1.dp, S.c.up.copy(alpha = 0.4f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun WelcomeScreen(vm: AppViewModel, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    val ok = name.isNotBlank()
    fun go(route: String) = scope.launch { vm.app.settings.setUsername(name); nav.navigate(route) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            HexMark(40.dp); TestnetBadge()
        }
        VSpace(20.dp)
        Text("Set up your\nStellura wallet", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, color = S.c.text)
        VSpace(8.dp)
        Text("Your keys stay on this phone. Nothing is stored on a server.", color = S.c.muted, fontSize = 14.sp)
        VSpace(20.dp)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(24) }, singleLine = true,
            label = { Text("What should we call you?") }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
        )
        Text(
            if (ok) "Shown as \"Welcome, ${name.trim()}\". Only saved on this device." else "Only saved on this device.",
            color = S.c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
        VSpace(20.dp)
        ChoiceCard(Icons.Filled.Add, "Create a new wallet", "Get a fresh recovery phrase", primary = true, enabled = ok) { go(Routes.CREATE) }
        VSpace(10.dp)
        ChoiceCard(Icons.AutoMirrored.Filled.Notes, "Import recovery phrase", "12 or 24 words from another wallet", enabled = ok) { go(Routes.IMPORT) }
        VSpace(10.dp)
        ChoiceCard(Icons.Filled.FileOpen, "Restore from backup file", "Encrypted .stellura file + its password", enabled = ok) { go(Routes.RESTORE) }
        VSpace(16.dp)
        Text(
            "Exchange accounts (Coinbase, Kraken…) can be linked from Settings after setup.",
            color = S.c.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, sub: String, primary: Boolean = false, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (primary) S.c.accent else S.c.surface
    val fg = if (primary) S.c.onAccent else S.c.text
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .then(if (primary) Modifier else Modifier.border(1.dp, S.c.line, RoundedCornerShape(14.dp)))
            .clickable(enabled = enabled, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) fg else S.c.up)
        Spacer(Modifier.size(14.dp))
        Column {
            Text(title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(sub, color = if (primary) fg.copy(alpha = 0.85f) else S.c.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StepHeader(nav: NavHostController, step: Int, total: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = S.c.text) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < step) S.c.accent else S.c.line))
            }
        }
    }
    VSpace(16.dp)
    Text("STEP $step OF $total · ${label.uppercase()}", fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.4.sp, color = S.c.muted)
}

/** Steps 2–3: show the new phrase, then confirm three random words. */
@Composable
fun CreatePhraseScreen(vm: AppViewModel, nav: NavHostController) {
    val phrase = remember {
        vm.pendingMnemonic ?: vm.app.wallet.generateMnemonic(12).also { vm.pendingMnemonic = it }
    }
    val words = phrase.split(' ')
    var verifying by rememberSaveable { mutableStateOf(false) }
    val checks = remember { words.indices.shuffled().take(3).sorted() }
    val answers = remember { mutableStateOf(List(3) { "" }) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
        if (!verifying) {
            StepHeader(nav, 2, 4, "Recovery phrase")
            Text("Write these 12 words down, in order", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text, modifier = Modifier.padding(top = 6.dp))
            VSpace(16.dp)
            words.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    pair.forEachIndexed { col, w ->
                        Row(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).background(S.c.surface)
                                .border(1.dp, S.c.line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${row * 2 + col + 1}", fontFamily = Mono, fontSize = 12.sp, color = S.c.muted, modifier = Modifier.size(22.dp, 16.dp))
                            Text(w, fontFamily = Mono, fontSize = 15.sp, color = S.c.text)
                        }
                    }
                }
            }
            VSpace(8.dp)
            Warning("Anyone with these words owns your coins. Stellura can never recover them for you. Paper is safer than screenshots.")
            VSpace(24.dp)
            PrimaryButton("I've written it down", Modifier.fillMaxWidth()) { verifying = true }
        } else {
            StepHeader(nav, 3, 4, "Confirm")
            Text("Enter these words from your phrase", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text, modifier = Modifier.padding(top = 6.dp))
            VSpace(16.dp)
            checks.forEachIndexed { i, idx ->
                OutlinedTextField(
                    value = answers.value[i],
                    onValueChange = { v -> answers.value = answers.value.toMutableList().also { it[i] = v.trim().lowercase() } },
                    label = { Text("Word #${idx + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
            }
            val correct = checks.indices.all { answers.value[it] == words[checks[it]] }
            VSpace(12.dp)
            PrimaryButton("Continue", Modifier.fillMaxWidth(), enabled = correct) { nav.navigate(Routes.SET_PIN) }
            VSpace(8.dp)
            SecondaryButton("Show phrase again", Modifier.fillMaxWidth()) { verifying = false }
        }
    }
}

@Composable
fun Warning(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(S.c.down.copy(alpha = 0.10f))
            .border(1.dp, S.c.down.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(14.dp)
    ) {
        Icon(Icons.Filled.Warning, null, tint = S.c.down, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Text(text, color = S.c.text, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
fun ImportPhraseScreen(vm: AppViewModel, nav: NavHostController) {
    var text by remember { mutableStateOf("") }
    val count = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    val valid = vm.app.wallet.isValidMnemonic(text)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
        StepHeader(nav, 2, 3, "Import")
        Text("Enter your recovery phrase", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text, modifier = Modifier.padding(top = 6.dp))
        Text("Words separated by spaces, in order.", color = S.c.muted, fontSize = 14.sp)
        VSpace(16.dp)
        OutlinedTextField(
            value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().height(160.dp),
            shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        )
        Text(
            when {
                count == 0 -> "12 or 24 words"
                valid -> "Valid $count-word phrase"
                count == 12 || count == 24 -> "Those words don't form a valid phrase. Check spelling and order."
                else -> "$count words so far"
            },
            color = if (valid) S.c.up else S.c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
        )
        VSpace(24.dp)
        PrimaryButton("Continue", Modifier.fillMaxWidth(), enabled = valid) {
            vm.pendingMnemonic = vm.app.wallet.normalise(text)
            text = ""
            nav.navigate(Routes.SET_PIN)
        }
    }
}

@Composable
fun RestoreBackupScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { fileBytes = runCatching { ctx.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
        StepHeader(nav, 2, 3, "Restore")
        Text("Restore from backup file", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text, modifier = Modifier.padding(top = 6.dp))
        VSpace(16.dp)
        SecondaryButton(if (fileBytes == null) "Choose .stellura file" else "File selected ✓", Modifier.fillMaxWidth()) { pick.launch(arrayOf("*/*")) }
        VSpace(12.dp)
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Backup password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        error?.let { Text(it, color = S.c.down, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        VSpace(24.dp)
        PrimaryButton(if (busy) "Decrypting…" else "Restore", Modifier.fillMaxWidth(), enabled = fileBytes != null && password.isNotEmpty() && !busy) {
            busy = true; error = null
            scope.launch {
                // scrypt is deliberately slow; keep it off the UI thread.
                val phrase = withContext(Dispatchers.Default) { vm.app.backup.import(fileBytes!!, password) }
                busy = false
                if (phrase != null && vm.app.wallet.isValidMnemonic(phrase)) {
                    vm.pendingMnemonic = phrase; password = ""; nav.navigate(Routes.SET_PIN)
                } else error = "Wrong password, or this isn't a Stellura backup."
            }
        }
    }
}

/** Final step: choose PIN, then save the wallet and finish onboarding. */
@Composable
fun SetPinScreen(vm: AppViewModel, nav: NavHostController) {
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(entry) {
        if (entry.length < PinManager.PIN_LENGTH) return@LaunchedEffect
        if (first == null) { first = entry; entry = ""; msg = null; return@LaunchedEffect }
        if (entry != first) { msg = "PINs didn't match. Try again."; first = null; entry = ""; return@LaunchedEffect }
        saving = true
        val phrase = vm.pendingMnemonic ?: run { msg = "Setup expired, go back and start again."; saving = false; return@LaunchedEffect }
        withContext(Dispatchers.Default) {
            vm.app.wallet.save(phrase)
            vm.app.pin.set(entry)
        }
        vm.pendingMnemonic = null
        vm.app.lock.unlock()
        vm.app.settings.setOnboarded(true)
    }

    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth()) { StepHeader(nav, 4, 4, "Secure") }
        VSpace(24.dp)
        Text(if (first == null) "Choose a 6-digit PIN" else "Enter it again", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text)
        Text("You can add fingerprint, face or an authenticator app in Settings.", color = S.c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        VSpace(24.dp)
        PinDots(entry.length)
        msg?.let { Text(it, color = S.c.down, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
        if (saving) Text("Encrypting your wallet…", color = S.c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.weight(1f))
        PinPad(
            onDigit = { if (!saving && entry.length < PinManager.PIN_LENGTH) entry += it },
            onDelete = { entry = entry.dropLast(1) },
            onBiometric = null,
        )
        VSpace(16.dp)
    }
}
