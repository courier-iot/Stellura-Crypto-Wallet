package com.stellura.wallet.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Non-secret preferences only. Secrets live in [com.stellura.wallet.security.SecureStore]. */
class SettingsRepository(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val username = stringPreferencesKey("username")
        val theme = stringPreferencesKey("theme")
        val layout = stringPreferencesKey("layout")
        val currency = stringPreferencesKey("currency")
        val timeframe = stringPreferencesKey("timeframe")
        val autoLock = intPreferencesKey("auto_lock_s")
        val lockOnScreenOff = booleanPreferencesKey("lock_screen_off")
        val biometric = booleanPreferencesKey("biometric")
        val hide = booleanPreferencesKey("hide_balances")
        val secureScreen = booleanPreferencesKey("block_screenshots")
        val favourites = stringPreferencesKey("favourites")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboarded = this[K.onboarded] ?: d.onboarded,
            username = this[K.username] ?: d.username,
            theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            layout = this[K.layout]?.let { runCatching { FavLayout.valueOf(it) }.getOrNull() } ?: d.layout,
            currency = this[K.currency] ?: d.currency,
            timeframe = this[K.timeframe]?.let { runCatching { Timeframe.valueOf(it) }.getOrNull() } ?: d.timeframe,
            autoLockSeconds = this[K.autoLock] ?: d.autoLockSeconds,
            lockOnScreenOff = this[K.lockOnScreenOff] ?: d.lockOnScreenOff,
            biometricUnlock = this[K.biometric] ?: d.biometricUnlock,
            hideBalances = this[K.hide] ?: d.hideBalances,
            blockScreenshots = this[K.secureScreen] ?: d.blockScreenshots,
            favourites = this[K.favourites]?.split(',')?.filter { it.isNotBlank() } ?: d.favourites,
        )
    }

    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[K.onboarded] = v }
    suspend fun setUsername(v: String) = context.dataStore.edit { it[K.username] = v.trim().take(24) }
    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[K.theme] = v.name }
    suspend fun setLayout(v: FavLayout) = context.dataStore.edit { it[K.layout] = v.name }
    suspend fun setCurrency(v: String) = context.dataStore.edit { it[K.currency] = v }
    suspend fun setTimeframe(v: Timeframe) = context.dataStore.edit { it[K.timeframe] = v.name }
    suspend fun setAutoLock(v: Int) = context.dataStore.edit { it[K.autoLock] = v }
    suspend fun setLockOnScreenOff(v: Boolean) = context.dataStore.edit { it[K.lockOnScreenOff] = v }
    suspend fun setBiometric(v: Boolean) = context.dataStore.edit { it[K.biometric] = v }
    suspend fun setHideBalances(v: Boolean) = context.dataStore.edit { it[K.hide] = v }
    suspend fun setBlockScreenshots(v: Boolean) = context.dataStore.edit { it[K.secureScreen] = v }
    suspend fun setFavourites(ids: List<String>) = context.dataStore.edit { it[K.favourites] = ids.distinct().joinToString(",") }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
