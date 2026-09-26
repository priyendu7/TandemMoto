package com.tandemmoto.ui.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tandemmoto.diagnostics.LogExporter
import com.tandemmoto.link
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.ui.components.rememberPermissionRequester
import com.tandemmoto.ui.playlist.PlaylistRoute
import com.tandemmoto.ui.ride.RideRoute
import com.tandemmoto.ui.settings.SettingsScreen
import com.tandemmoto.ui.setup.PairRoute

object Routes {
    const val RIDE = "ride"
    const val PAIR = "pair"
    const val PLAYLIST = "playlist"
    const val SETTINGS = "settings"
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
            PairRoute(
                onBack = { navController.popBackStack() },
                onPaired = { navController.popBackStack() }
            )
        }
        composable(Routes.PLAYLIST) {
            PlaylistRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            val partner by context.link.partner.collectAsStateWithLifecycle()
            val permissions = rememberPermissionRequester()
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onExportLogs = { LogExporter.share(context) },
                partnerName = partner?.name,
                onForgetPartner = context.link::forgetPartner,
                notifications = permissions.state.statuses[AppPermission.NOTIFICATIONS],
                onNotificationsClick = {
                    if (permissions.state.isGranted(AppPermission.NOTIFICATIONS)) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    } else {
                        permissions.request(AppPermission.NOTIFICATIONS)
                    }
                }
            )
        }
    }
}
