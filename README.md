# Stellura Crypto Wallet

A self-custody crypto wallet for Android with a space/cyberpunk-corporate look ("Deep Orbit" theme, crimson accent, Hex Vault mark).

> **Pre-release, testnet only.** Every coin this build can hold is a test coin with no value. The code has not had a security audit. Do not import a recovery phrase that holds real funds.

## What works in this build (0.1.0-testnet)

- **Onboarding:** pick a display name, then create a new wallet (12-word phrase, written down and confirmed) or import a 12/24-word phrase or restore an encrypted `.stellura` backup file.
- **Home:** "Welcome, *name*", portfolio value, favourite coins with 7-day trend lines and 24h change, list/tile toggle, light/dark toggle, hide-balances toggle, pull to refresh.
- **Markets:** live top 100 coins by market cap with search; star to add or remove favourites.
- **Coin detail:** price chart with a timeframe dropdown (3y, 2y, 1y, 6m, 3m, 2m, 1m, 4w, 3w, 2w, 1w).
- **Wallets:** Ethereum (Sepolia), Bitcoin (Testnet4) and Solana (Devnet) addresses and balances from one phrase. Receive shows a QR code and lets you copy the address. **Send** works for Sepolia ETH.
- **Currencies:** 75 fiat currencies, CAD by default.
- **Security:**
  - Unlock with a 6-digit PIN, fingerprint/face, or an authenticator app (TOTP).
  - Auto-lock after 5/10/15/30/45 seconds or 1–5 minutes, plus lock when the screen turns off.
  - Screenshot blocking, and growing lockouts after wrong PINs.
- **Backup & device transfer:** view recovery phrase (PIN required), export an encrypted backup file, and erase the wallet.

## Build the debug APK

### On GitHub (no setup)
1. Push this repo to GitHub (branch `main`).
2. Open **Actions → Build debug APK**. It runs on every push, or press **Run workflow**.
3. When it's green, download **stellura-debug-apk** from the run's *Artifacts* section, unzip, and install the `.apk` on your phone (allow "install unknown apps" for your browser/file manager).

The build pulls [Trust Wallet Core](https://github.com/trustwallet/wallet-core) from GitHub Packages using the workflow's built-in token. If that step ever fails with a 401/403, create a classic personal access token with only `read:packages`, and add it as a repository secret named `GPR_TOKEN`.

### In Android Studio (optional)
Open the folder in Android Studio (Ladybug or newer) and add this to `local.properties` (never commit it):

```
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_READ_PACKAGES_TOKEN
```

Then run the `app` configuration.

## How it stays serverless

There is no Stellura server and no account. Your display name is cosmetic and stays on the phone.

- **Keys:** one BIP-39 recovery phrase derives every chain's keys on-device using standard paths, so the phrase also works in other wallets. It is encrypted with an AES-256-GCM key held in the Android Keystore (StrongBox chip when present). The encrypted file is excluded from Android cloud backup and device-to-device copy.
- **Moving to a new phone:** use either option.
  1. Your recovery phrase.
  2. An encrypted `.stellura` backup file. It's a standard Web3 keystore (scrypt + AES), sealed with a password you choose. Move it any way you like, then pick **Restore from backup file** on the new phone.
- **Why not "username + password = key"?** That design (a "brain wallet") has been drained by attackers many times. People pick guessable passwords, and anyone can try billions of guesses offline without the app. A random phrase plus an optional password-sealed file gives the same convenience without that risk.
- **Network calls:** the app talks directly to public services, with no API keys. These services can see your IP address; a Tor/custom-node option is on the roadmap.
  - Prices: CoinGecko, and Coinbase for 2–3-year history.
  - Exchange rates: open.er-api.com.
  - Blockchains: public testnet nodes (publicnode.com, Solana devnet, mempool.space).

## Project layout

```
app/src/main/java/com/stellura/wallet/
  security/   SecureStore (Keystore vault), PinManager, TotpManager, LockManager, Biometrics
  wallet/     WalletManager (phrase + addresses), ChainClients (balances, ETH send), BackupManager, Bech32
  data/       SettingsRepository (DataStore), PriceRepository + FxRepository, Currencies, models
  ui/         AppRoot (navigation + lock overlay), theme, components, screens/
```

Stack: Kotlin, Jetpack Compose (Material 3), Trust Wallet Core, OkHttp, DataStore, AndroidX Biometric, ZXing. Supports Android 8.0+ (minSdk 26).

## Roadmap

| Build | Scope |
|---|---|
| 0.2 | Send for BTC and SOL. EVM chains (Polygon, Base, Arbitrum, Optimism, BNB Chain). Import any token by contract address (ERC-20, SPL). |
| 0.3 | Price alerts (on-device background checks + notifications). Staking (ETH via liquid staking, SOL native). |
| 0.4 | Swaps via a DEX aggregator. Buy crypto via an on-ramp partner (needs a partner account). Read-only exchange accounts (Coinbase, Kraken, Binance API keys). |
| 0.5 | Remaining top-100 native chains (XRP, Cardano, Dogecoin, Litecoin, Tron, TON, Polkadot, Cosmos…). Custom RPC nodes / Tor. |
| 1.0 | Independent security audit, mainnet switch, release signing, store listing. |

## Known limitations

- Testnet only by design.
- The vault key is hardware-backed but not tied to biometric authentication. Anyone with an unlocked, rooted phone and the app's files could decrypt it. Binding the key to user authentication is planned before mainnet.
- Fonts use the system sans/mono for now; Space Grotesk and JetBrains Mono will be bundled.
- Public price APIs rate-limit heavy use; pull to refresh again after a minute if prices fail to load.
