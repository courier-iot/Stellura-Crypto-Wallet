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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellura.wallet.BuildConfig
import com.stellura.wallet.data.Currencies
import com.stellura.wallet.data.ThemeMode
import com.stellura.wallet.security.Biometrics
import com.stellura.wallet.security.LockManager
import com.stellura.wallet.security.PinManager
import com.stellura.wallet.security.TotpManager
import com.stellura.wallet.ui.AppViewModel
import com.stellura.wallet.ui.components.PrimaryButton
import com.stellura.wallet.ui.components.QrCode
import com.stellura.wallet.ui.components.SectionLabel
import com.stellura.wallet.ui.components.VSpace
import com.stellura.wallet.ui.theme.Mono
import com.stellura.wallet.ui.theme.S
import com.stellura.wallet.wallet.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Dialog { NAME, THEME, CURRENCY, AUTOLOCK, TOTP, PIN_FOR_PHRASE, PHRASE, BACKUP, CHANGE_PIN, ERASE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, activity: FragmentActivity) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val s = st ?: return
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var totpOn by remember { mutableStateOf(vm.app.totp.isEnabled()) }
    var note by remember { mutableStateOf<String?>(null) }
    val bioAvailable = remember { Biometrics.available(activity) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Settings", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text)
        note?.let { Text(it, color = S.c.up, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }

        SectionLabel("Profile & look")
        Group {
            ValueRow("Display name", s.username.ifBlank { "USER" }) { dialog = Dialog.NAME }
            ValueRow("Theme", s.theme.name.lowercase().replaceFirstChar { it.uppercase() }) { dialog = Dialog.THEME }
            ValueRow("Currency", s.currency) { dialog = Dialog.CURRENCY }
        }

        SectionLabel("Unlock with")
        Group {
            ValueRow("PIN", "Change") { dialog = Dialog.CHANGE_PIN }
            ToggleRow(
                if (bioAvailable) "Fingerprint / face" else "Fingerprint / face (not set up on this phone)",
                s.biometricUnlock, enabled = bioAvailable,
            ) { on ->
                if (on) Biometrics.prompt(activity, "Enable biometric unlock", onSuccess = { vm.update { settings.setBiometric(true) } })
                else vm.update { settings.setBiometric(false) }
            }
            ToggleRow("Authenticator app", totpOn) { on ->
                if (on) dialog = Dialog.TOTP else { vm.app.totp.disable(); totpOn = false }
            }
        }

        SectionLabel("Protection")
        Group {
            ValueRow("Auto-lock", LockManager.label(s.autoLockSeconds)) { dialog = Dialog.AUTOLOCK }
            ToggleRow("Lock when screen turns off", s.lockOnScreenOff) { v -> vm.update { settings.setLockOnScreenOff(v) } }
            ToggleRow("Hide balances", s.hideBalances) { v -> vm.update { settings.setHideBalances(v) } }
            ToggleRow("Block screenshots", s.blockScreenshots) { v -> vm.update { settings.setBlockScreenshots(v) } }
        }

        SectionLabel("Backup & devices")
        Group {
            ValueRow("Recovery phrase", "View") { dialog = Dialog.PIN_FOR_PHRASE }
            ValueRow("Encrypted backup file", "Export") { dialog = Dialog.BACKUP }
        }
        Text(
            "To move to a new phone: install Stellura there and choose \"Restore from backup file\" " +
                "(with your backup password) or \"Import recovery phrase\". No account or server needed.",
            color = S.c.muted, fontSize = 12.sp, modifier = Modifier.padding(4.dp)
        )

        SectionLabel("Network & accounts")
        Group {
            ValueRow("Network", "Testnet only", onClick = null)
            ValueRow("Exchange accounts", "Coming soon", onClick = null)
        }

        SectionLabel("Danger zone")
        Group { ValueRow("Erase wallet from this phone", "", danger = true) { dialog = Dialog.ERASE } }

        Text("Stellura ${BuildConfig.VERSION_NAME}", fontFamily = Mono, fontSize = 11.sp, color = S.c.muted, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
    }

    when (dialog) {
        Dialog.NAME -> TextDialog("Display name", s.username, onDismiss = { dialog = null }) { v -> vm.update { settings.setUsername(v) }; dialog = null }
        Dialog.THEME -> ChoiceDialog("Theme", ThemeMode.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, s.theme,
            onDismiss = { dialog = null }) { v -> vm.update { settings.setTheme(v) }; dialog = null }
        Dialog.CURRENCY -> CurrencyDialog(s.currency, onDismiss = { dialog = null }) { v -> vm.update { settings.setCurrency(v) }; vm.refreshMarkets(); dialog = null }
        Dialog.AUTOLOCK -> ModalBottomSheet(onDismissRequest = { dialog = null }, containerColor = S.c.surface) {
            AutoLockSheet(s.autoLockSeconds) { v -> vm.update { settings.setAutoLock(v) }; dialog = null }
        }
        Dialog.TOTP -> TotpSetupDialog(vm, s.username, onDismiss = { dialog = null }) { totpOn = true; dialog = null; note = "Authenticator unlock is on." }
        Dialog.PIN_FOR_PHRASE -> PinCheckDialog(vm, "Enter PIN to view phrase", onDismiss = { dialog = null }) { dialog = Dialog.PHRASE }
        Dialog.PHRASE -> PhraseDialog(vm.app.wallet.revealMnemonic()) { dialog = null }
        Dialog.BACKUP -> BackupDialog(vm, onDismiss = { dialog = null }) { msg -> note = msg; dialog = null }
        Dialog.CHANGE_PIN -> ChangePinDialog(vm, onDismiss = { dialog = null }) { note = "PIN changed."; dialog = null }
        Dialog.ERASE -> EraseDialog(onDismiss = { dialog = null }) { vm.eraseEverything { dialog = null } }
        null -> {}
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(S.c.surface).border(1.dp, S.c.line, RoundedCornerShape(14.dp))) {
        content()
    }
}

@Composable
private fun ValueRow(label: String, value: String, danger: Boolean = false, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (danger) S.c.down else S.c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (onClick != null && value.isNotEmpty()) "$value ›" else value, fontFamily = Mono, fontSize = 13.sp, color = S.c.muted)
    }
    HorizontalDivider(color = S.c.line)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) S.c.text else S.c.muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = S.c.accent, checkedThumbColor = S.c.onAccent, uncheckedTrackColor = S.c.surface2),
        )
    }
    HorizontalDivider(color = S.c.line)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoLockSheet(current: Int, onPick: (Int) -> Unit) {
    var sel by remember { mutableStateOf(current) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text("Auto-lock", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = S.c.text)
        Text("Lock Stellura after it's been idle for:", color = S.c.muted, fontSize = 13.sp)
        VSpace(14.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 5) {
            LockManager.AUTO_LOCK_OPTIONS.forEach { secs ->
                val on = secs == sel
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) S.c.accent else S.c.surface2)
                        .border(1.dp, if (on) S.c.accent else S.c.line, RoundedCornerShape(12.dp))
                        .clickable { sel = secs },
                    contentAlignment = Alignment.Center,
                ) { Text(if (secs < 60) "${secs}s" else "${secs / 60}m", fontFamily = Mono, color = if (on) S.c.onAccent else S.c.text) }
            }
        }
        VSpace(16.dp)
        PrimaryButton("Save", Modifier.fillMaxWidth()) { onPick(sel) }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text(title, color = S.c.text) },
        text = { OutlinedTextField(v, { v = it.take(24) }, singleLine = true, colors = stelluraFieldColors()) },
        confirmButton = { TextButton(onClick = { onSave(v) }, enabled = v.isNotBlank()) { Text("Save", color = S.c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = S.c.muted) } },
    )
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, current: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text(title, color = S.c.text) },
        text = {
            Column {
                options.forEach { (v, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 14.dp)) {
                        Text(label, color = if (v == current) S.c.accent else S.c.text, modifier = Modifier.weight(1f))
                        if (v == current) Text("✓", color = S.c.accent)
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = S.c.muted) } },
    )
}

@Composable
private fun CurrencyDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = Currencies.all.filter { q.isBlank() || it.first.contains(q, true) || it.second.contains(q, true) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text("Currency", color = S.c.text) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search 75 currencies") }, colors = stelluraFieldColors())
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(list, key = { it.first }) { (code, name) ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(code) }.padding(vertical = 12.dp)) {
                            Text(code, fontFamily = Mono, color = if (code == current) S.c.accent else S.c.text, modifier = Modifier.size(56.dp, 20.dp))
                            Text(name, color = S.c.text)
                        }
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = S.c.muted) } },
    )
}

@Composable
private fun PinCheckDialog(vm: AppViewModel, title: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text(title, color = S.c.text) },
        text = {
            Column {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), colors = stelluraFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                err?.let { Text(it, color = S.c.down, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length == 6, onClick = {
                scope.launch {
                    when (val r = withContext(Dispatchers.Default) { vm.app.pin.verify(pin) }) {
                        PinManager.Result.Ok -> onOk()
                        is PinManager.Result.Wrong -> { err = "Wrong PIN"; pin = "" }
                        is PinManager.Result.LockedOut -> { err = "Locked for ${r.secondsRemaining}s"; pin = "" }
                    }
                }
            }) { Text("Continue", color = S.c.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = S.c.muted) } },
    )
}

@Composable
private fun PhraseDialog(phrase: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text("Recovery phrase", color = S.c.text) },
        text = {
            Column {
                phrase.split(' ').forEachIndexed { i, w ->
                    Text("${i + 1}. $w", fontFamily = Mono, color = S.c.text, modifier = Modifier.padding(vertical = 2.dp))
                }
                VSpace(10.dp)
                Text("Never share these words. Stellura staff will never ask for them.", color = S.c.down, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = S.c.accent) } },
    )
}

@Composable
private fun BackupDialog(vm: AppViewModel, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sealed by remember { mutableStateOf<ByteArray?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri: Uri? ->
        val data = sealed
        if (uri != null && data != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) } }.isSuccess
            onDone(if (ok) "Backup saved. Keep the password somewhere separate." else "Couldn't save the file.")
        }
    }
    val valid = p1.length >= BackupManager.MIN_PASSWORD && p1 == p2
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text("Encrypted backup", color = S.c.text) },
        text = {
            Column {
                Text("Choose a password for this file (at least ${BackupManager.MIN_PASSWORD} characters). Without it, the file can't be opened, by anyone, including you.",
                    color = S.c.muted, fontSize = 13.sp)
                VSpace(10.dp)
                OutlinedTextField(p1, { p1 = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), colors = stelluraFieldColors())
                VSpace(8.dp)
                OutlinedTextField(p2, { p2 = it }, label = { Text("Repeat password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), colors = stelluraFieldColors(),
                    isError = p2.isNotEmpty() && p1 != p2)
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = {
                busy = true
                scope.launch {
                    sealed = withContext(Dispatchers.Default) { vm.app.backup.export(vm.app.wallet.revealMnemonic(), p1) }
                    busy = false
                    save.launch(BackupManager.FILE_NAME)
                }
            }) { Text(if (busy) "Encrypting…" else "Save file", color = S.c.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = S.c.muted) } },
    )
}

@Composable
private fun TotpSetupDialog(vm: AppViewModel, username: String, onDismiss: () -> Unit, onEnabled: () -> Unit) {
    val secret = remember { vm.app.totp.newSecret() }
    val uri = remember { vm.app.totp.otpauthUri(secret, username) }
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text("Authenticator app", color = S.c.text) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Scan with your authenticator app (Aegis, 2FAS, Google Authenticator…), then enter the 6-digit code.",
                    color = S.c.muted, fontSize = 13.sp)
                VSpace(12.dp)
                QrCode(uri, 200.dp)
                VSpace(8.dp)
                Text(TotpManager.base32(secret).chunked(4).joinToString(" "), fontFamily = Mono, fontSize = 12.sp, color = S.c.text)
                VSpace(10.dp)
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6); err = false }, singleLine = true,
                    label = { Text("Code") }, isError = err, colors = stelluraFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
        },
        confirmButton = {
            TextButton(enabled = code.length == 6, onClick = {
                if (vm.app.totp.confirm(secret, code)) onEnabled() else err = true
            }) { Text("Turn on", color = S.c.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = S.c.muted) } },
    )
}

@Composable
private fun ChangePinDialog(vm: AppViewModel, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    @Composable fun pinField(v: String, label: String, set: (String) -> Unit) = OutlinedTextField(
        v, { set(it.filter(Char::isDigit).take(6)) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), colors = stelluraFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.padding(bottom = 8.dp),
    )
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text("Change PIN", color = S.c.text) },
        text = {
            Column {
                pinField(old, "Current PIN") { old = it }
                pinField(new1, "New PIN") { new1 = it }
                pinField(new2, "Repeat new PIN") { new2 = it }
                err?.let { Text(it, color = S.c.down, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = old.length == 6 && new1.length == 6 && new1 == new2, onClick = {
                scope.launch {
                    when (val r = withContext(Dispatchers.Default) { vm.app.pin.verify(old) }) {
                        PinManager.Result.Ok -> { withContext(Dispatchers.Default) { vm.app.pin.set(new1) }; onDone() }
                        is PinManager.Result.Wrong -> err = "Current PIN is wrong"
                        is PinManager.Result.LockedOut -> err = "Locked for ${r.secondsRemaining}s"
                    }
                }
            }) { Text("Save", color = S.c.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = S.c.muted) } },
    )
}

@Composable
private fun EraseDialog(onDismiss: () -> Unit, onErase: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = S.c.surface,
        title = { Text("Erase wallet?", color = S.c.text) },
        text = {
            Column {
                Text("This removes your keys and settings from this phone. Without your recovery phrase or a backup file, any coins are gone for good.",
                    color = S.c.muted, fontSize = 13.sp)
                VSpace(10.dp)
                OutlinedTextField(typed, { typed = it }, label = { Text("Type ERASE to confirm") }, singleLine = true, colors = stelluraFieldColors())
            }
        },
        confirmButton = { TextButton(enabled = typed == "ERASE", onClick = onErase) { Text("Erase", color = S.c.down) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = S.c.muted) } },
    )
}
