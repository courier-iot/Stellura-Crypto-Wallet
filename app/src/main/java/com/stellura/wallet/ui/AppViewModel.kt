package com.stellura.wallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stellura.wallet.StelluraApp
import com.stellura.wallet.data.AppSettings
import com.stellura.wallet.data.CoinMarket
import com.stellura.wallet.wallet.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

class AppViewModel(application: Application) : AndroidViewModel(application) {

    val app = application as StelluraApp

    val settings: StateFlow<AppSettings?> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _markets = MutableStateFlow<List<CoinMarket>>(emptyList())
    val markets = _markets.asStateFlow()

    private val _marketError = MutableStateFlow<String?>(null)
    val marketError = _marketError.asStateFlow()

    private val _addresses = MutableStateFlow<Map<Chain, String>>(emptyMap())
    val addresses = _addresses.asStateFlow()

    private val _balances = MutableStateFlow<Map<Chain, BigInteger>>(emptyMap())
    val balances = _balances.asStateFlow()

    private val _fxTick = MutableStateFlow(0)
    val fxTick = _fxTick.asStateFlow()

    /** Recovery phrase being set up during onboarding; held in memory only until saved. */
    var pendingMnemonic: String? = null

    fun refreshMarkets(force: Boolean = false) = viewModelScope.launch {
        runCatching {
            app.prices.fx.refresh(); _fxTick.value++
            _markets.value = app.prices.topMarkets(force)
            _marketError.value = null
        }.onFailure { _marketError.value = "Prices unavailable right now. Pull to retry." }
    }

    fun loadWallet() = viewModelScope.launch {
        if (!app.wallet.hasWallet()) return@launch
        val addrs = withContext(Dispatchers.Default) { Chain.entries.associateWith { app.wallet.address(it) } }
        _addresses.value = addrs
        refreshBalances()
    }

    fun refreshBalances() = viewModelScope.launch {
        val out = HashMap<Chain, BigInteger>()
        _addresses.value.forEach { (chain, addr) ->
            runCatching { app.chains.balance(chain, addr) }.onSuccess { out[chain] = it }
        }
        _balances.value = out
    }

    /** Falls back to USD if exchange rates haven't loaded, so we never mislabel a value. */
    fun displayCurrency(s: AppSettings): String = if (app.prices.fx.hasRate(s.currency)) s.currency else "USD"

    fun toFiat(usd: Double, s: AppSettings): Double = app.prices.fx.convert(usd, displayCurrency(s))

    fun coin(id: String): CoinMarket? = _markets.value.firstOrNull { it.id == id }

    fun update(block: suspend StelluraApp.() -> Unit) = viewModelScope.launch { app.block() }

    /** Removes the wallet and every setting from this phone. Irreversible without a backup. */
    fun eraseEverything(onDone: () -> Unit) = viewModelScope.launch {
        app.wallet.deleteWallet(); app.pin.clear(); app.totp.disable()
        app.secureStore.wipeAll(); app.settings.clearAll()
        _addresses.value = emptyMap(); _balances.value = emptyMap()
        app.lock.lockNow()
        onDone()
    }
}
