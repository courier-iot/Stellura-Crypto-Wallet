package com.stellura.wallet

import android.app.Application
import com.stellura.wallet.data.FxRepository
import com.stellura.wallet.data.PriceRepository
import com.stellura.wallet.data.SettingsRepository
import com.stellura.wallet.net.Http
import com.stellura.wallet.security.LockManager
import com.stellura.wallet.security.PinManager
import com.stellura.wallet.security.SecureStore
import com.stellura.wallet.security.TotpManager
import com.stellura.wallet.wallet.BackupManager
import com.stellura.wallet.wallet.ChainClients
import com.stellura.wallet.wallet.WalletManager
import com.stellura.wallet.stellarite.StelClient
import com.stellura.wallet.stellarite.StelSettings

/** Simple manual dependency container; everything lives on-device, no backend. */
class StelluraApp : Application() {

    lateinit var settings: SettingsRepository; private set
    lateinit var secureStore: SecureStore; private set
    lateinit var pin: PinManager; private set
    lateinit var totp: TotpManager; private set
    lateinit var lock: LockManager; private set
    lateinit var wallet: WalletManager; private set
    lateinit var backup: BackupManager; private set
    lateinit var prices: PriceRepository; private set
    lateinit var chains: ChainClients; private set
    lateinit var stelSettings: StelSettings; private set
    lateinit var stel: StelClient; private set

    override fun onCreate() {
        super.onCreate()
        // Native signing/derivation library from Trust Wallet Core.
        System.loadLibrary("TrustWalletCore")

        val http = Http.client
        settings = SettingsRepository(this)
        secureStore = SecureStore(this)
        pin = PinManager(this, secureStore)
        totp = TotpManager(secureStore)
        lock = LockManager()
        wallet = WalletManager(secureStore)
        backup = BackupManager()
        prices = PriceRepository(http, FxRepository(http))
        chains = ChainClients(http)
        stelSettings = StelSettings(this)
        stel = StelClient(stelSettings)
    }
}
