package com.stellura.wallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stellura.wallet.StelluraApp
import com.stellura.wallet.data.AppSettings
import com.stellura.wallet.data.CoinMarket
import com.stellura.wallet.wallet.Chain
import com.stellura.wallet.stellarite.StelAccount
import com.stellura.wallet.stellarite.StelCoin
import com.stellura.wallet.stellarite.StelMiner
import com.example.stellariteminer.SoloBackend
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

    /** Stellarite test coins: addresses, node accounts and the last error per coin. */
    private val _stelAddresses = MutableStateFlow<Map<StelCoin, String>>(emptyMap())
    val stelAddresses = _stelAddresses.asStateFlow()
    private val _stelAccounts = MutableStateFlow<Map<StelCoin, StelAccount>>(emptyMap())
    val stelAccounts = _stelAccounts.asStateFlow()
    private val _stelErrors = MutableStateFlow<Map<StelCoin, String>>(emptyMap())
    val stelErrors = _stelErrors.asStateFlow()
    /** Balances on this phone's solo chains (blocks mined in solo mode). */
    private val _stelSolo = MutableStateFlow<Map<StelCoin, Long>>(emptyMap())
    val stelSolo = _stelSolo.asStateFlow()

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
        val stel = withContext(Dispatchers.Default) { StelCoin.entries.associateWith { app.wallet.stellariteAddress(it) } }
        stel.forEach { (c, a) -> app.stelSettings.setAddress(c, a) }   // public only: lets mining run while locked
        _stelAddresses.value = stel
        refreshBalances()
    }

    fun refreshStellarite() = viewModelScope.launch {
        val acc = HashMap<StelCoin, StelAccount>(); val errs = HashMap<StelCoin, String>(); val solo = HashMap<StelCoin, Long>()
        _stelAddresses.value.forEach { (c, a) ->
            if (app.stelSettings.node(c).isNotBlank()) {
                runCatching { app.stel.account(c, a) }.onSuccess { acc[c] = it }.onFailure { errs[c] = it.message ?: "Node unreachable" }
            }
            runCatching { withContext(Dispatchers.IO) { SoloBackend.chainFor(app, c.profile).balance(a) } }.onSuccess { solo[c] = it }
        }
        _stelAccounts.value = acc; _stelErrors.value = errs; _stelSolo.value = solo
    }

    fun refreshBalances() = viewModelScope.launch {
        val out = HashMap<Chain, BigInteger>()
        _addresses.value.forEach { (chain, addr) ->
            runCatching { app.chains.balance(chain, addr) }.onSuccess { out[chain] = it }
        }
        _balances.value = out
        refreshStellarite()
    }

    /** Falls back to USD if exchange rates haven't loaded, so we never mislabel a value. */
    fun displayCurrency(s: AppSettings): String = if (app.prices.fx.hasRate(s.currency)) s.currency else "USD"

    fun toFiat(usd: Double, s: AppSettings): Double = app.prices.fx.convert(usd, displayCurrency(s))

    fun coin(id: String): CoinMarket? = _markets.value.firstOrNull { it.id == id }

    fun update(block: suspend StelluraApp.() -> Unit) = viewModelScope.launch { app.block() }

    /** Removes the wallet and every setting from this phone. Irreversible without a backup. */
    fun eraseEverything(onDone: () -> Unit) = viewModelScope.launch {
        if (StelMiner.status.value.running) { StelMiner.stop(app); kotlinx.coroutines.delay(2000) }  // let the miner finish first
        app.stelSettings.clear()
        StelCoin.entries.forEach { SoloBackend.delete(app, it.profile) }   // Stellarite solo chains
        app.wallet.deleteWallet(); app.pin.clear(); app.totp.disable()
        app.secureStore.wipeAll(); app.settings.clearAll()
        _addresses.value = emptyMap(); _balances.value = emptyMap()
        _stelAddresses.value = emptyMap(); _stelAccounts.value = emptyMap(); _stelSolo.value = emptyMap()
        app.lock.lockNow()
        onDone()
    }
}
