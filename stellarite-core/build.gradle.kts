// Stellarite core, synced from the Stellarite Miner repo (android/core) by
// tools/sync-stellarite-core.sh: Pulsarite/Nebulite consensus rules, physics, the mining
// engine, node clients and wallet.py-compatible signing. Don't edit src/ here; change it
// in the miner repo and sync, so both apps always run identical consensus code.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.stellarite.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
