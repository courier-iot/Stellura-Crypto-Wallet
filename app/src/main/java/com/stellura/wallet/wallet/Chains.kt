package com.stellura.wallet.wallet

/**
 * Networks the wallet talks to. This pre-release is TESTNET ONLY: every coin here has no
 * real value. Mainnet is a one-line switch per chain once the app has been audited.
 *
 * Get free test coins from a faucet (search "<network> faucet").
 */
enum class Chain(
    val label: String,
    val network: String,
    val ticker: String,
    val decimals: Int,
    /** CoinGecko id of the mainnet asset, used to show an indicative fiat value. */
    val priceId: String,
    val explorerTx: String,
) {
    ETHEREUM("Ethereum", "Sepolia testnet", "ETH", 18, "ethereum", "https://sepolia.etherscan.io/tx/"),
    BITCOIN("Bitcoin", "Testnet4", "BTC", 8, "bitcoin", "https://mempool.space/testnet4/tx/"),
    SOLANA("Solana", "Devnet", "SOL", 9, "solana", "https://explorer.solana.com/tx/"),
}

object Endpoints {
    // Public, key-less RPC endpoints. Users will be able to set their own node in Settings later.
    const val SEPOLIA_RPC = "https://ethereum-sepolia-rpc.publicnode.com"
    const val SEPOLIA_CHAIN_ID = 11155111L
    const val SOLANA_DEVNET_RPC = "https://api.devnet.solana.com"
    const val BTC_TESTNET4_API = "https://mempool.space/testnet4/api"
}
