package com.stellura.wallet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.stellura.wallet.ui.AppRoot
import com.stellura.wallet.ui.AppViewModel
import kotlinx.coroutines.launch

/** FragmentActivity (not ComponentActivity) because BiometricPrompt requires it. */
class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by viewModels()
    private val app get() = application as StelluraApp

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (vm.settings.value?.lockOnScreenOff != false) app.lock.lockNow()
        }
    }

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = app.lock.onBackground()
        override fun onStart(owner: LifecycleOwner) =
            app.lock.onForeground(vm.settings.value?.autoLockSeconds ?: 60)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Secure by default until settings load (hides content from screenshots/recents).
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)

        lifecycleScope.launch {
            vm.settings.collect { s ->
                if (s == null || s.blockScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContent { AppRoot(vm, this) }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        app.lock.touch()
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }
}
