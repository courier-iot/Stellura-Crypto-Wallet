import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Wallet Core is hosted on GitHub Packages, which needs a token even for public packages.
// CI: uses the workflow's built-in GITHUB_TOKEN automatically.
// Local: put gpr.user / gpr.key in local.properties (see README).
val localProps = Properties().apply {
    val f = file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val gprUser: String? = localProps.getProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
val gprKey: String? = localProps.getProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/trustwallet/wallet-core")
            credentials {
                username = gprUser
                password = gprKey
            }
            content { includeGroup("com.trustwallet") }
        }
    }
}

rootProject.name = "StelluraCryptoWallet"
include(":app")
include(":stellarite-core")   // Pulsarite/Nebulite engine, synced from Stellarite Miner
