package com.stellura.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stellura.wallet.security.Biometrics
import com.stellura.wallet.security.LockManager
import com.stellura.wallet.security.PinManager
import com.stellura.wallet.ui.AppViewModel
import com.stellura.wallet.ui.components.HexMark
import com.stellura.wallet.ui.components.PinDots
import com.stellura.wallet.ui.components.PinPad
import com.stellura.wallet.ui.components.PrimaryButton
import com.stellura.wallet.ui.components.VSpace
import com.stellura.wallet.ui.theme.Mono
import com.stellura.wallet.ui.theme.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LockScreen(vm: AppViewModel, activity: FragmentActivity) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    val bioOn = settings.biometricUnlock && Biometrics.available(activity)
    val totpOn = remember { vm.app.totp.isEnabled() }
    var entry by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var totpMode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var totpFails by remember { mutableStateOf(0) }

    fun bio() = Biometrics.prompt(activity, "Unlock Stellura", onSuccess = { vm.app.lock.unlock() })

    LaunchedEffect(Unit) { if (bioOn) bio() }

    LaunchedEffect(entry) {
        if (entry.length < PinManager.PIN_LENGTH) return@LaunchedEffect
        checking = true
        val r = withContext(Dispatchers.Default) { vm.app.pin.verify(entry) }
        checking = false; entry = ""
        msg = when (r) {
            PinManager.Result.Ok -> { vm.app.lock.unlock(); null }
            is PinManager.Result.Wrong -> "Wrong PIN · ${r.attemptsLeftBeforeLockout} tries before a timeout"
            is PinManager.Result.LockedOut -> "Too many attempts. Try again in ${r.secondsRemaining}s"
        }
    }

    // Opaque, swallows all touches to the app underneath.
    Column(
        Modifier.fillMaxSize().background(S.c.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VSpace(40.dp)
        HexMark(56.dp)
        VSpace(20.dp)
        Text("Welcome back, ${settings.username.ifBlank { "USER" }}", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = S.c.text)
        if (!totpMode) {
            Text(if (checking) "Checking…" else "Enter your 6-digit PIN", color = S.c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            VSpace(24.dp)
            PinDots(entry.length)
            msg?.let { Text(it, color = S.c.down, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.weight(1f))
            PinPad(
                onDigit = { if (!checking && entry.length < PinManager.PIN_LENGTH) entry += it },
                onDelete = { entry = entry.dropLast(1) },
                onBiometric = if (bioOn) ({ bio() }) else null,
            )
            if (totpOn) TextButton(onClick = { totpMode = true }) { Text("Use authenticator code", color = S.c.up) }
        } else {
            Text("Enter the code from your authenticator app", color = S.c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            VSpace(24.dp)
            OutlinedTextField(
                value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 24.sp, letterSpacing = 6.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = stelluraFieldColors(),
            )
            msg?.let { Text(it, color = S.c.down, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            VSpace(16.dp)
            PrimaryButton("Unlock", Modifier.fillMaxWidth(), enabled = code.length == 6) {
                if (vm.app.totp.verify(code)) vm.app.lock.unlock() else {
                    code = ""; totpFails++
                    // Throttle guessing: after 5 misses, fall back to the rate-limited PIN.
                    if (totpFails >= 5) { totpMode = false; msg = "Too many codes tried. Use your PIN." } else msg = "That code didn't match."
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { totpMode = false; msg = null }) { Text("Use PIN instead", color = S.c.up) }
        }
        Text(
            "Auto-locks after ${LockManager.label(settings.autoLockSeconds)} idle",
            fontFamily = Mono, fontSize = 11.sp, color = S.c.muted, modifier = Modifier.padding(top = 8.dp)
        )
    }
}
