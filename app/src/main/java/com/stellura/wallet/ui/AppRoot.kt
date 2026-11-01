package com.stellura.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stellura.wallet.ui.screens.CoinDetailScreen
import com.stellura.wallet.ui.screens.CreatePhraseScreen
import com.stellura.wallet.ui.screens.HomeScreen
import com.stellura.wallet.ui.screens.ImportPhraseScreen
import com.stellura.wallet.ui.screens.LockScreen
import com.stellura.wallet.ui.screens.MarketsScreen
import com.stellura.wallet.ui.screens.MineScreen
import com.stellura.wallet.ui.screens.StelCoinScreen
import com.stellura.wallet.ui.screens.StelSendScreen
import com.stellura.wallet.ui.screens.ReceiveScreen
import com.stellura.wallet.ui.screens.RestoreBackupScreen
import com.stellura.wallet.ui.screens.SendScreen
import com.stellura.wallet.ui.screens.SetPinScreen
import com.stellura.wallet.ui.screens.SettingsScreen
import com.stellura.wallet.ui.screens.WalletsScreen
import com.stellura.wallet.ui.screens.WelcomeScreen
import com.stellura.wallet.ui.theme.S
import com.stellura.wallet.ui.theme.StelluraTheme
import kotlinx.coroutines.delay

object Routes {
    const val WELCOME = "welcome"; const val CREATE = "create"; const val IMPORT = "import"
    const val RESTORE = "restore"; const val SET_PIN = "setpin"
    const val HOME = "home"; const val MARKETS = "markets"; const val WALLETS = "wallets"; const val SETTINGS = "settings"
    const val COIN = "coin/{id}"; const val RECEIVE = "receive/{chain}"; const val SEND = "send"
    const val MINE = "mine"; const val STEL = "stel/{coin}"; const val STEL_SEND = "stelsend/{coin}"
    fun coin(id: String) = "coin/$id"
    fun receive(chain: String) = "receive/$chain"
    fun stel(coin: String) = "stel/$coin"
    fun stelSend(coin: String) = "stelsend/$coin"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.MARKETS, "Markets", Icons.AutoMirrored.Filled.ShowChart),
    Tab(Routes.WALLETS, "Wallets", Icons.Filled.AccountBalanceWallet),
    Tab(Routes.MINE, "Mine", Icons.Filled.Memory),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppRoot(vm: AppViewModel, activity: FragmentActivity) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return // DataStore still loading (a few ms)
    val locked by vm.app.lock.locked.collectAsStateWithLifecycle()

    StelluraTheme(s.theme) {
        Box(Modifier.fillMaxSize().background(S.c.bg)) {
            if (!s.onboarded) {
                OnboardingNav(vm)
            } else {
                // Idle auto-lock ticker while visible.
                LaunchedEffect(s.autoLockSeconds) {
                    while (true) { delay(1000); vm.app.lock.checkIdle(s.autoLockSeconds) }
                }
                LaunchedEffect(Unit) { vm.loadWallet(); vm.refreshMarkets() }
                MainNav(vm, activity)
                if (locked) LockScreen(vm, activity)
            }
        }
    }
}

@Composable
private fun OnboardingNav(vm: AppViewModel) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.WELCOME, modifier = Modifier.statusBarsPadding().navigationBarsPadding()) {
        composable(Routes.WELCOME) { WelcomeScreen(vm, nav) }
        composable(Routes.CREATE) { CreatePhraseScreen(vm, nav) }
        composable(Routes.IMPORT) { ImportPhraseScreen(vm, nav) }
        composable(Routes.RESTORE) { RestoreBackupScreen(vm, nav) }
        composable(Routes.SET_PIN) { SetPinScreen(vm, nav) }
    }
}

@Composable
private fun MainNav(vm: AppViewModel, activity: FragmentActivity) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(vm, nav) }
                composable(Routes.MARKETS) { MarketsScreen(vm, nav) }
                composable(Routes.WALLETS) { WalletsScreen(vm, nav) }
                composable(Routes.SETTINGS) { SettingsScreen(vm, activity) }
                composable(Routes.COIN, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    CoinDetailScreen(vm, nav, it.arguments?.getString("id").orEmpty())
                }
                composable(Routes.RECEIVE, arguments = listOf(navArgument("chain") { type = NavType.StringType })) {
                    ReceiveScreen(vm, nav, it.arguments?.getString("chain").orEmpty())
                }
                composable(Routes.SEND) { SendScreen(vm, nav) }
                composable(Routes.MINE) { MineScreen(vm, nav) }
                composable(Routes.STEL, arguments = listOf(navArgument("coin") { type = NavType.StringType })) {
                    StelCoinScreen(vm, nav, it.arguments?.getString("coin").orEmpty())
                }
                composable(Routes.STEL_SEND, arguments = listOf(navArgument("coin") { type = NavType.StringType })) {
                    StelSendScreen(vm, nav, it.arguments?.getString("coin").orEmpty())
                }
            }
        }
        if (route in tabs.map { it.route }) BottomBar(nav, route)
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar(containerColor = S.c.bg) {
        tabs.forEach { t ->
            NavigationBarItem(
                selected = route == t.route,
                onClick = {
                    nav.navigate(t.route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }
                },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = S.c.accent, selectedTextColor = S.c.accent,
                    unselectedIconColor = S.c.muted, unselectedTextColor = S.c.muted,
                    indicatorColor = S.c.surface2,
                ),
            )
        }
    }
}
