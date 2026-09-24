package com.tandemmoto.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tandemmoto.ui.playlist.PlaylistScreen
import com.tandemmoto.ui.ride.RideRoute
import com.tandemmoto.ui.settings.SettingsScreen
import com.tandemmoto.ui.setup.PairScreen
import com.tandemmoto.ui.setup.PermissionsRoute
import com.tandemmoto.ui.setup.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val PERMISSIONS = "permissions"
    const val PAIR = "pair"
    const val RIDE = "ride"
    const val PLAYLIST = "playlist"
    const val SETTINGS = "settings"
}

// TODO(Phase 1): start at RIDE once a paired partner is remembered.
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onGetStarted = { navController.navigate(Routes.PERMISSIONS) })
        }
        composable(Routes.PERMISSIONS) {
            PermissionsRoute(
                onContinue = { navController.navigate(Routes.PAIR) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PAIR) {
            PairScreen(
                onSkip = {
                    // Leaving setup: Back from Ride should exit the app, not return to Welcome.
                    navController.navigate(Routes.RIDE) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.RIDE) {
            RideRoute(
                onOpenPlaylist = { navController.navigate(Routes.PLAYLIST) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.PLAYLIST) {
            PlaylistScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
