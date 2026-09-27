package com.tandemmoto.ui.ride

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.tandemmoto.ui.components.SongSeekBar
import com.tandemmoto.ui.components.openWifiSettings
import com.tandemmoto.ui.components.rememberPermissionRequester
import com.tandemmoto.ui.theme.TandemMotoTheme
import com.tandemmoto.voice.IntercomLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    // Offered once, the first time the link connects, one dialog after the other:
    // notifications (Android 13+), when the connection notification appears; and the microphone
    // for the intercom, while the app is on screen, so the link service can take the microphone
    // type that lets it talk with the screen locked (#70). Both are optional.
    LaunchedEffect(state.connection) {
        if (state.connection != ConnectionStatus.Connected) return@LaunchedEffect
        val askedOnce = AskedOnce(context)
        val offer = connectOffers.filter {
            permissions.state.status(it) == PermissionStatus.Denied && !askedOnce.wasAsked(it)
        }
        if (offer.isEmpty()) return@LaunchedEffect
        offer.forEach(askedOnce::markAsked)
        permissions.requestAll(offer)
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
        onSeek = viewModel::onSeek,
        onStartIntercom = viewModel::onStartIntercom,
        onStopIntercom = viewModel::onStopIntercom,
        onMute = viewModel::onMute
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
    onSeek: (Long) -> Unit = {},
    onStartIntercom: () -> Unit = {},
    onStopIntercom: () -> Unit = {},
    onMute: (Boolean) -> Unit = {}
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // The picture takes the room that's left, as a square: large on a tall phone,
                // smaller on a short one, and never pushing the controls off the screen.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Artwork(
                        state.artwork,
                        Modifier
                            .widthIn(max = 320.dp)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    )
                }
                SongInfo(state)
                if (state.nowPlaying != null) {
                    SongSeekBar(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        enabled = state.controlsEnabled,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PlaybackControls(state, onPlayPause, onNext, onPrevious)
                if (permissions.isGranted(AppPermission.MICROPHONE)) {
                    IntercomSection(state, onStartIntercom, onStopIntercom, onMute)
                } else {
                    PermissionPrompt(
                        permission = AppPermission.MICROPHONE,
                        state = permissions,
                        onRequest = { onRequestPermission(AppPermission.MICROPHONE) }
                    )
                }
            }
        }
    }
}

/** Offered once on the first connection, in this order. */
private val connectOffers = listOf(AppPermission.NOTIFICATIONS, AppPermission.MICROPHONE)

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

/** The song's own picture (album art), or a music note when the file has none. */
@Composable
private fun Artwork(bytes: ByteArray?, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, bytes) {
        value = bytes?.let {
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
            }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val picture = image
        if (picture != null) {
            // Decorative: the title and artist below say what's playing.
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
    }
}

@Composable
private fun SongInfo(state: RideUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Song names are often file names: two lines at most, so a long one can't push the
        // controls off a small screen.
        Text(
            text = state.nowPlaying ?: stringResource(R.string.ride_no_song),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val partner = state.partnerName ?: stringResource(R.string.ride_your_partner)
        val below = when {
            state.waitingForPartner -> stringResource(R.string.ride_getting_song_on, partner)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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

/**
 * The intercom (#72): what it's doing, Start or Stop intercom (both phones), and this phone's
 * mute. Buttons are 56 dp tall, for gloves.
 */
@Composable
private fun IntercomSection(
    state: RideUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMute: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val partner = state.partnerName ?: stringResource(R.string.ride_your_partner)
    val line = when (state.intercom) {
        IntercomLine.NotLinked -> stringResource(R.string.ride_intercom_not_linked)
        IntercomLine.Ready -> stringResource(R.string.ride_intercom_ready)
        IntercomLine.WaitingForPause -> stringResource(R.string.ride_intercom_off)
        IntercomLine.Connecting -> stringResource(R.string.ride_intercom_connecting)
        IntercomLine.OnPhone -> stringResource(R.string.ride_intercom_on_phone)
        IntercomLine.On -> when {
            state.muted && state.partnerMuted ->
                stringResource(R.string.ride_intercom_on_both_muted)
            state.muted -> stringResource(R.string.ride_intercom_on_you_muted)
            state.partnerMuted -> stringResource(R.string.ride_intercom_on_partner_muted, partner)
            state.talkIntoPhone -> stringResource(R.string.ride_intercom_on_talk_into_phone)
            else -> stringResource(R.string.ride_intercom_on)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // A change is read out, like the connection bar.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Icon(
                painter = painterResource(
                    if (state.muted) R.drawable.ic_mic_off else R.drawable.ic_mic
                ),
                contentDescription = null,
                tint = if (state.intercomOn) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(text = line, style = MaterialTheme.typography.bodyLarge)
        }
        if (state.intercom == IntercomLine.NotLinked) return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val buttonModifier = Modifier
                .weight(1f)
                .height(56.dp)
            when (state.intercom) {
                IntercomLine.Ready -> FilledTonalButton(
                    onClick = onStart,
                    modifier = buttonModifier
                ) {
                    Text(stringResource(R.string.ride_intercom_start))
                }
                IntercomLine.Connecting, IntercomLine.On, IntercomLine.OnPhone ->
                    OutlinedButton(onClick = onStop, modifier = buttonModifier) {
                        Text(stringResource(R.string.ride_intercom_stop))
                    }
                else -> Unit
            }
            FilledTonalButton(onClick = { onMute(!state.muted) }, modifier = buttonModifier) {
                Icon(
                    painter = painterResource(
                        if (state.muted) R.drawable.ic_mic else R.drawable.ic_mic_off
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (state.muted) R.string.ride_unmute else R.string.ride_mute))
            }
        }
    }
}

private val nearbyGranted = PermissionsState(
    34,
    mapOf(
        AppPermission.NEARBY to PermissionStatus.Granted,
        AppPermission.MICROPHONE to PermissionStatus.Granted
    )
)

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
