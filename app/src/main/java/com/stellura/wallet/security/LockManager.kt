package com.stellura.wallet.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks idle time and decides when the app must re-lock. Starts locked on every launch. */
class LockManager {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile private var lastInteraction = SystemClock.elapsedRealtime()
    @Volatile private var backgroundedAt = 0L

    fun touch() { lastInteraction = SystemClock.elapsedRealtime() }

    fun unlock() { touch(); _locked.value = false }

    fun lockNow() { _locked.value = true }

    fun onBackground() { backgroundedAt = SystemClock.elapsedRealtime() }

    fun onForeground(timeoutSeconds: Int) {
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt >= timeoutSeconds * 1000L) lockNow()
        backgroundedAt = 0L
        touch()
    }

    /** Called on a ticker while the app is visible. */
    fun checkIdle(timeoutSeconds: Int) {
        if (!_locked.value && SystemClock.elapsedRealtime() - lastInteraction >= timeoutSeconds * 1000L) lockNow()
    }

    companion object {
        /** Seconds. 5s–45s, then 1–5 minutes. */
        val AUTO_LOCK_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 120, 180, 240, 300)

        fun label(seconds: Int): String = if (seconds < 60) "$seconds sec" else "${seconds / 60} min"
    }
}
