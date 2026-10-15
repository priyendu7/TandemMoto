package com.tandemmoto.ui.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tandemmoto.TandemMotoApp
import com.tandemmoto.diagnostics.LogExporter
import com.tandemmoto.link
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.ui.components.rememberPermissionRequester
import com.tandemmoto.ui.playlist.PlaylistRoute
import com.tandemmoto.ui.ride.RideRoute
import com.tandemmoto.ui.settings.PartnerSongsUi
import com.tandemmoto.ui.settings.SettingsScreen
import com.tandemmoto.ui.setup.PairRoute
import com.tandemmoto.voice.VoiceEffect

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
        composable(
            Routes.RIDE,
            // A small parallax under the playlist sliding over it; other screens keep the default.
            exitTransition = {
                if (targetState.destination.route == Routes.PLAYLIST) {
                    slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 }
                } else {
                    null
                }
            },
            popEnterTransition = {
                if (initialState.destination.route == Routes.PLAYLIST) {
                    slideInHorizontally(tween(SLIDE_MS)) { -it / 4 }
                } else {
                    null
                }
            }
        ) {
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
        composable(
            Routes.PLAYLIST,
            enterTransition = {
                slideInHorizontally(tween(SLIDE_MS)) { it } + fadeIn(tween(SLIDE_MS))
            },
            popExitTransition = {
                slideOutHorizontally(tween(SLIDE_MS)) { it } + fadeOut(tween(SLIDE_MS))
            }
        ) {
            PlaylistRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            val partner by context.link.partner.collectAsStateWithLifecycle()
            val permissions = rememberPermissionRequester()
            val app = context.applicationContext as TandemMotoApp
            val window by app.windowSettings.settings.collectAsStateWithLifecycle()
            val transfers by app.transfers.state.collectAsStateWithLifecycle()
            val micTest by app.micTest.state.collectAsStateWithLifecycle()
            val talkTest by app.talkTest.state.collectAsStateWithLifecycle()
            val earbudMic by app.earbudMic.on.collectAsStateWithLifecycle()
            val soundCues by app.soundCues.on.collectAsStateWithLifecycle()
            val talkLevelDb by app.voice.micLevelDb.collectAsStateWithLifecycle()
            val voiceProcessing by app.voiceProcessing.on.collectAsStateWithLifecycle()
            val voiceEffects = remember { VoiceEffect.available() }
            val partnerHost by app.link.channel.partnerHost.collectAsStateWithLifecycle()
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onExportLogs = { LogExporter.share(context) },
                partnerName = partner?.name,
                onForgetPartner = context.link::forgetPartner,
                partnerSongs = partner?.let {
                    PartnerSongsUi(
                        window = window,
                        storedCount = transfers.stored.size,
                        storedBytes = transfers.storedBytes,
                        aheadLimitedTo = transfers.aheadLimitedTo
                    )
                },
                onWindowChange = app.windowSettings::update,
                onRemovePartnerSongs = app.transfers::removeAll,
                micTest = micTest,
                onMicTest = app.micTest::start,
                onCancelMicTest = app.micTest::cancel,
                // Only while linked (or while it's on, so it can be turned off).
                talkTest = talkTest.takeIf { partnerHost != null || it.on },
                onTalkTest = app.talkTest::set,
                soundCues = soundCues,
                onSoundCues = app.soundCues::set,
                earbudMic = earbudMic,
                onEarbudMic = app.earbudMic::set,
                talkLevelDb = talkLevelDb,
                voiceProcessing = voiceProcessing,
                onVoiceProcessing = app.voiceProcessing::set,
                voiceEffects = voiceEffects,
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

/** The playlist slides in from the right and back out over Home. */
private const val SLIDE_MS = 300
