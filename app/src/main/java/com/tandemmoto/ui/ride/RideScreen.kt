package com.tandemmoto.ui.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tandemmoto.R
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.AskedOnce
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.ui.components.ConnectionStatusBar
import com.tandemmoto.ui.components.ControlButton
import com.tandemmoto.ui.components.PermissionPrompt
import com.tandemmoto.ui.components.openWifiSettings
import com.tandemmoto.ui.components.rememberPermissionRequester
import com.tandemmoto.ui.theme.TandemMotoTheme
import java.util.Locale

/** The Home screen: the app opens here, and every feature is reached from it. */
@Composable
fun RideRoute(
    onOpenPair: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: RideViewModel = viewModel(factory = RideViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberPermissionRequester()
    val context = LocalContext.current
    // Notifications (Android 13+, optional) are offered once, the first time the link connects:
    // that's when the connection notification appears, so the reason is obvious.
    LaunchedEffect(state.connection) {
        val askedOnce = AskedOnce(context)
        if (state.connection == ConnectionStatus.Connected &&
            permissions.state.status(AppPermission.NOTIFICATIONS) == PermissionStatus.Denied &&
            !askedOnce.wasAsked(AppPermission.NOTIFICATIONS)
        ) {
            askedOnce.markAsked(AppPermission.NOTIFICATIONS)
            permissions.request(AppPermission.NOTIFICATIONS)
        }
    }
    RideScreen(
        state = state,
        permissions = permissions.state,
        onRequestPermission = permissions.request,
        onOpenPair = onOpenPair,
        onConnect = viewModel::onConnect,
        onDisconnect = viewModel::onDisconnect,
        onOpenWifiSettings = context::openWifiSettings,
        onPlayPause = viewModel::onPlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onOpenPlaylist = onOpenPlaylist,
        onOpenSettings = onOpenSettings,
        onSeek = viewModel::onSeek
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideScreen(
    state: RideUiState,
    permissions: PermissionsState,
    onRequestPermission: (AppPermission) -> Unit,
    onOpenPair: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeek: (Long) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenPlaylist) {
                        Icon(
                            painter = painterResource(R.drawable.ic_queue_music),
                            contentDescription = stringResource(R.string.ride_open_playlist)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.ride_open_settings)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ConnectionSection(
                state.connection,
                state.partnerName,
                permissions,
                onRequestPermission,
                onOpenPair,
                onConnect,
                onDisconnect,
                onOpenWifiSettings
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                NowPlayingCard(state, onSeek)
                Spacer(Modifier.weight(1f))
                PlaybackControls(state, onPlayPause, onNext, onPrevious)
                IntercomIndicator(state.intercomOn)
            }
        }
    }
}

/**
 * Each Home section asks for its own permission in place, so the rest of the app keeps working
 * without it. Connection needs Nearby devices; music (Phase 2) and intercom (Phase 4) add theirs.
 */
@Composable
private fun ConnectionSection(
    connection: ConnectionStatus,
    partnerName: String?,
    permissions: PermissionsState,
    onRequestPermission: (AppPermission) -> Unit,
    onOpenPair: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenWifiSettings: () -> Unit
) {
    // Disconnect asks first: a stray tap while riding shouldn't drop the link.
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    // While still searching it's "Stop", not "Disconnect": nothing is connected yet.
    val searching = connection == ConnectionStatus.Searching ||
        connection == ConnectionStatus.Reconnecting
    if (confirmDisconnect) {
        val name = partnerName ?: stringResource(R.string.ride_your_partner)
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = {
                Text(
                    stringResource(
                        if (searching) R.string.ride_stop_title else R.string.ride_disconnect_title,
                        name
                    )
                )
            },
            text = {
                Text(
                    if (searching) {
                        stringResource(R.string.ride_stop_body)
                    } else {
                        stringResource(R.string.ride_disconnect_body, name)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    onDisconnect()
                }) {
                    val action = if (searching) {
                        R.string.ride_stop_action
                    } else {
                        R.string.notification_disconnect
                    }
                    Text(stringResource(action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) {
                    Text(stringResource(R.string.pair_cancel))
                }
            }
        )
    }
    if (permissions.isGranted(AppPermission.NEARBY)) {
        ConnectionStatusBar(
            status = connection,
            partnerName = partnerName,
            onClickLabel = stringResource(
                when (connection) {
                    ConnectionStatus.NotConnected -> R.string.ride_connect_action
                    ConnectionStatus.Unreachable -> R.string.ride_try_again_action
                    ConnectionStatus.WifiOff -> R.string.ride_wifi_action
                    ConnectionStatus.Searching,
                    ConnectionStatus.Reconnecting -> R.string.ride_stop_action
                    ConnectionStatus.Connected,
                    ConnectionStatus.PartnerAppClosed -> R.string.ride_disconnect_action
                    else -> R.string.ride_pair_action
                }
            ),
            onClick = when (connection) {
                ConnectionStatus.NotPaired,
                ConnectionStatus.PairedElsewhere,
                ConnectionStatus.NoLongerPaired -> onOpenPair
                ConnectionStatus.NotConnected, ConnectionStatus.Unreachable -> onConnect
                ConnectionStatus.WifiOff -> onOpenWifiSettings
                ConnectionStatus.Connected,
                ConnectionStatus.Searching,
                ConnectionStatus.Reconnecting,
                ConnectionStatus.PartnerAppClosed -> {
                    { confirmDisconnect = true }
                }
                else -> null
            }
        )
    } else {
        PermissionPrompt(
            permission = AppPermission.NEARBY,
            state = permissions,
            onRequest = { onRequestPermission(AppPermission.NEARBY) }
        )
    }
}

@Composable
private fun NowPlayingCard(state: RideUiState, onSeek: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.ride_now_playing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.nowPlaying ?: stringResource(R.string.ride_no_song),
                style = MaterialTheme.typography.headlineMedium
            )
            val below = when {
                state.gettingSong ->
                    state.partnerName
                        ?.let { stringResource(R.string.ride_getting_song_named, it) }
                        ?: stringResource(R.string.ride_getting_song)
                else -> state.artist
            }
            if (below != null && state.nowPlaying != null) {
                Text(
                    text = below,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.nowPlaying != null) SeekBar(state, onSeek)
        }
    }
}

/**
 * Where the song is. Dragging only moves the thumb; letting go seeks, which the partner's phone
 * follows (#60), so a drag doesn't send a seek for every step.
 */
@Composable
private fun SeekBar(state: RideUiState, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(0)
    val shown = dragging?.toLong() ?: state.positionMs.coerceIn(0, duration)
    val description = stringResource(R.string.ride_seek)
    val position = stringResource(
        R.string.ride_seek_position,
        formatTime(shown),
        formatTime(duration)
    )
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.toLong()) }
                dragging = null
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = state.controlsEnabled && duration > 0,
            modifier = Modifier.semantics {
                contentDescription = description
                stateDescription = position
            }
        )
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            Text(
                text = formatTime(shown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "4:05", or "1:02:03" past an hour. */
internal fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1_000
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

@Composable
private fun PlaybackControls(
    state: RideUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                icon = R.drawable.ic_skip_previous,
                contentDescription = stringResource(R.string.ride_previous),
                onClick = onPrevious,
                enabled = state.controlsEnabled
            )
            ControlButton(
                icon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.ride_pause else R.string.ride_play
                ),
                onClick = onPlayPause,
                enabled = state.controlsEnabled,
                primary = true
            )
            ControlButton(
                icon = R.drawable.ic_skip_next,
                contentDescription = stringResource(R.string.ride_next),
                onClick = onNext,
                enabled = state.controlsEnabled
            )
        }
        if (!state.controlsEnabled) {
            Text(
                text = stringResource(R.string.ride_controls_disabled_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun IntercomIndicator(intercomOn: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = null,
            tint = if (intercomOn) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(
                if (intercomOn) R.string.ride_intercom_on else R.string.ride_intercom_off
            ),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private val nearbyGranted =
    PermissionsState(34, mapOf(AppPermission.NEARBY to PermissionStatus.Granted))

@Composable
private fun RidePreview(state: RideUiState, permissions: PermissionsState = nearbyGranted) {
    RideScreen(
        state = state,
        permissions = permissions,
        onRequestPermission = {},
        onOpenPair = {},
        onConnect = {},
        onDisconnect = {},
        onOpenWifiSettings = {},
        onPlayPause = {},
        onNext = {},
        onPrevious = {},
        onOpenPlaylist = {},
        onOpenSettings = {}
    )
}

@Preview(name = "Nearby permission missing · light", showBackground = true)
@Composable
private fun RideNeedsNearbyPreview() {
    TandemMotoTheme(darkTheme = false, dynamicColor = false) {
        RidePreview(
            RideUiState(),
            PermissionsState(34, mapOf(AppPermission.NEARBY to PermissionStatus.Denied))
        )
    }
}

@Preview(name = "Not paired · light", showBackground = true)
@Composable
private fun RideNotPairedPreview() {
    TandemMotoTheme(darkTheme = false, dynamicColor = false) {
        RidePreview(RideUiState())
    }
}

@Preview(name = "Connected, paused · dark", showBackground = true)
@Composable
private fun RideConnectedPreview() {
    TandemMotoTheme(darkTheme = true, dynamicColor = false) {
        RidePreview(
            RideUiState(
                connection = ConnectionStatus.Connected,
                nowPlaying = "Highway Song",
                hasSongs = true,
                positionMs = 83_000,
                durationMs = 245_000
            )
        )
    }
}
