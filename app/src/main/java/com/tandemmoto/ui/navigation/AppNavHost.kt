package com.tandemmoto.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tandemmoto.diagnostics.LogExporter
import com.tandemmoto.spike.WifiDirectLabRoute
import com.tandemmoto.ui.playlist.PlaylistScreen
import com.tandemmoto.ui.ride.RideRoute
import com.tandemmoto.ui.settings.SettingsScreen
import com.tandemmoto.ui.setup.PairScreen

object Routes {
    const val RIDE = "ride"
    const val PAIR = "pair"
    const val PLAYLIST = "playlist"
    const val SETTINGS = "settings"
    const val SPIKE_LAB = "spike_lab"
}

/**
 * Home-first: after the system splash the app opens on Ride (Home). Pairing, playlist and
 * settings open from it, and each Home section asks for its own permission in place.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.RIDE) {
        composable(Routes.RIDE) {
            RideRoute(
                onOpenPair = { navController.navigate(Routes.PAIR) },
                onOpenPlaylist = { navController.navigate(Routes.PLAYLIST) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.PAIR) {
            PairScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PLAYLIST) {
            PlaylistScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onExportLogs = { LogExporter.share(context) },
                onOpenLab = { navController.navigate(Routes.SPIKE_LAB) }
            )
        }
        composable(Routes.SPIKE_LAB) {
            WifiDirectLabRoute(onBack = { navController.popBackStack() })
        }
    }
}
