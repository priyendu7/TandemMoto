package com.tandemmoto.ui.ride

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tandemmoto.R
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.AskedOnce
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.service.nextTry
import com.tandemmoto.ui.components.AppCard
import com.tandemmoto.ui.components.BarTone
import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.ui.components.ConnectionStatusBar
import com.tandemmoto.ui.components.PermissionPrompt
import com.tandemmoto.ui.components.SongSeekBar
import com.tandemmoto.ui.components.StatusStripMinHeight
import com.tandemmoto.ui.components.connectWave
import com.tandemmoto.ui.components.glow
import com.tandemmoto.ui.components.horizontalSwipe
import com.tandemmoto.ui.components.openWifiSettings
import com.tandemmoto.ui.components.rememberConnectWave
import com.tandemmoto.ui.components.rememberPermissionRequester
import com.tandemmoto.ui.theme.Radius
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
        onMute = viewModel::onMute,
        onVolume = context::showVolume
    )
}

/** Shows the system volume panel for music, the same as pressing a volume key. */
private fun Context.showVolume() {
    getSystemService(AudioManager::class.java)?.adjustStreamVolume(
        AudioManager.STREAM_MUSIC,
        AudioManager.ADJUST_SAME,
        AudioManager.FLAG_SHOW_UI
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
    onMute: (Boolean) -> Unit = {},
    onVolume: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    // Swiping right to left opens the playlist, as if pulling it in from the right.
    Scaffold(
        modifier = Modifier.horizontalSwipe(onSwipeLeft = onOpenPlaylist),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                // A little shorter than the default 64 dp, so the status card sits close under it.
                expandedHeight = 60.dp,
                // The page colour, so the bar and the page read as one surface.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    // The playlist opens from the player's fourth button.
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(44.dp)
                            .background(colors.surfaceContainer, TileShape)
                            .border(1.dp, colors.outlineVariant, TileShape)
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.ride_open_settings),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        // One green ring spreads from the connection bar when the partner connects.
        val wave = rememberConnectWave(state.connection.tone == BarTone.Connected)
        val waveColor = TandemMotoTheme.appColors.connected
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .connectWave({ wave.value }, waveColor, StatusCardTop + StatusStripMinHeight / 2)
        ) {
            ConnectionSection(
                state.connection,
                state.partnerName,
                state.nextTryAtMs,
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
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
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
                PlaybackControls(state, onPlayPause, onNext, onPrevious, onOpenPlaylist, onVolume)
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

/** Space beside the connection card and the player on Home. */
private val StatusCardMargin = 20.dp

/** The settings button's rounded square. */
private val TileShape = RoundedCornerShape(Radius.Medium)

/** Space above it: small, since the top bar already leaves room under the title. */
private val StatusCardTop = 4.dp

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
    nextTryAtMs: Long?,
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
    val cardModifier = Modifier.padding(
        start = StatusCardMargin,
        end = StatusCardMargin,
        top = StatusCardTop,
        bottom = 8.dp
    )
    if (permissions.isGranted(AppPermission.NEARBY)) {
        ConnectionStatusBar(
            modifier = cardModifier,
            status = connection,
            partnerName = partnerName,
            detail = nextTryAtMs?.let { nextTry(LocalContext.current, it) },
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
            onRequest = { onRequestPermission(AppPermission.NEARBY) },
            modifier = cardModifier
        )
    }
}

/** A decoded picture and its average colour, for the glow behind it. */
private class DecodedArt(val image: ImageBitmap, val average: Color)

private fun decodeArt(bytes: ByteArray): DecodedArt? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    // Scaling to one pixel blends the picture into one colour, with no palette library.
    val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
    val average = Color(pixel.getPixel(0, 0))
    if (pixel !== bitmap) pixel.recycle()
    return DecodedArt(bitmap.asImageBitmap(), average)
}

private val ArtworkShape = RoundedCornerShape(Radius.ExtraLarge)

/**
 * The song's own picture (album art), or a music note when the file has none, glowing in the
 * picture's average colour (in `primary`, faintly, without one).
 */
@Composable
private fun Artwork(bytes: ByteArray?, modifier: Modifier = Modifier) {
    val art by produceState<DecodedArt?>(null, bytes) {
        value = bytes?.let { withContext(Dispatchers.Default) { decodeArt(it) } }
    }
    val glowColor = art?.average ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .glow(glowColor, radius = 32.dp, shape = ArtworkShape)
            .clip(ArtworkShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ArtworkShape)
    ) {
        val picture = art?.image
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
                tint = TandemMotoTheme.appColors.accent,
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
    }
}

@Composable
private fun SongInfo(state: RideUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                style = MaterialTheme.typography.bodyLarge,
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
    onPrevious: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onVolume: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Playlist and volume, plain, either side of three big buttons for gloves: previous and
        // next on cards, play in the accent colour.
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Always enabled: with no songs yet, this is where they're added.
            PlayerButton(
                icon = R.drawable.ic_queue_music,
                contentDescription = stringResource(R.string.ride_open_playlist),
                onClick = onOpenPlaylist,
                style = PlayerButtonStyle.Plain
            )
            PlayerButton(
                icon = R.drawable.ic_skip_previous,
                contentDescription = stringResource(R.string.ride_previous),
                onClick = onPrevious,
                enabled = state.controlsEnabled
            )
            PlayerButton(
                icon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.ride_pause else R.string.ride_play
                ),
                onClick = onPlayPause,
                enabled = state.controlsEnabled,
                style = PlayerButtonStyle.Main
            )
            PlayerButton(
                icon = R.drawable.ic_skip_next,
                contentDescription = stringResource(R.string.ride_next),
                onClick = onNext,
                enabled = state.controlsEnabled
            )
            PlayerButton(
                icon = R.drawable.ic_volume,
                contentDescription = stringResource(R.string.ride_volume),
                onClick = onVolume,
                style = PlayerButtonStyle.Plain
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

private enum class PlayerButtonStyle(val size: Dp, val radius: Dp, val icon: Dp) {
    /** Just the icon, in the secondary text colour. */
    Plain(48.dp, 14.dp, 24.dp),

    /** On a card: previous and next. */
    Card(60.dp, 18.dp, 28.dp),

    /** Filled with the accent: play and pause. */
    Main(84.dp, Radius.ExtraLarge, 36.dp)
}

@Composable
private fun PlayerButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    style: PlayerButtonStyle = PlayerButtonStyle.Card
) {
    val colors = MaterialTheme.colorScheme
    val accent = TandemMotoTheme.appColors
    val shape = RoundedCornerShape(style.radius)
    val (container, content) = when (style) {
        PlayerButtonStyle.Plain -> Color.Transparent to colors.onSurfaceVariant
        PlayerButtonStyle.Card -> colors.surfaceContainer to colors.onSurface
        PlayerButtonStyle.Main -> accent.accent to accent.onAccent
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(style.size)
            .clip(shape)
            .background(
                if (enabled || style != PlayerButtonStyle.Main) {
                    container
                } else {
                    colors.surfaceContainerHighest
                }
            )
            .then(
                if (style == PlayerButtonStyle.Card) {
                    Modifier.border(1.dp, colors.outlineVariant, shape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) content else colors.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(style.icon)
        )
    }
}

/**
 * The intercom (#72) on its own card: what it's doing in two lines (read as one), a level meter
 * while it's live, then this phone's mute and Start or Stop intercom (both phones), 60 dp tall
 * for gloves.
 */
@Composable
private fun IntercomSection(
    state: RideUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMute: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val appColors = TandemMotoTheme.appColors
    val partner = state.partnerName ?: stringResource(R.string.ride_your_partner)
    val on = when (state.intercom) {
        IntercomLine.NotLinked, IntercomLine.Ready, IntercomLine.WaitingForPause -> false
        IntercomLine.Connecting, IntercomLine.On, IntercomLine.OnPhone -> true
    }
    val detail = when (state.intercom) {
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
    val title = stringResource(
        if (on) R.string.ride_intercom_title_on else R.string.ride_intercom_title_off
    )
    AppCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        // Read as one line, and a change is read out, like the connection card.
                        .clearAndSetSemantics {
                            text = AnnotatedString("$title · $detail")
                            liveRegion = LiveRegionMode.Polite
                        }
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.intercomOn && !state.muted) LevelBars(appColors.connected)
            }
            if (state.intercom == IntercomLine.NotLinked) return@Column
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val buttonModifier = Modifier.weight(1f)
                val muted = state.muted
                IntercomButton(
                    container = if (muted) colors.onSurface else colors.surfaceContainerHighest,
                    content = if (muted) colors.surface else colors.onSurface,
                    border = colors.outline,
                    onClick = { onMute(!state.muted) },
                    modifier = buttonModifier
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.muted) R.drawable.ic_mic else R.drawable.ic_mic_off
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(if (muted) R.string.ride_unmute else R.string.ride_mute))
                }
                when (state.intercom) {
                    IntercomLine.Ready -> IntercomButton(
                        container = appColors.connectedTint,
                        content = appColors.connected,
                        border = appColors.connected.copy(alpha = 0.35f),
                        onClick = onStart,
                        modifier = buttonModifier
                    ) {
                        Text(stringResource(R.string.ride_intercom_start))
                    }
                    IntercomLine.Connecting, IntercomLine.On, IntercomLine.OnPhone ->
                        IntercomButton(
                            container = appColors.notConnectedTint,
                            content = appColors.notConnected,
                            border = appColors.notConnected.copy(alpha = 0.35f),
                            onClick = onStop,
                            modifier = buttonModifier
                        ) {
                            Text(stringResource(R.string.ride_intercom_stop))
                        }
                    else -> Unit
                }
            }
        }
    }
}

/** Five bars like a level meter, showing the intercom is live; decorative. */
@Composable
private fun LevelBars(color: Color) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(24.dp)
    ) {
        listOf(8, 16, 24, 12, 18).forEach {
            Box(
                Modifier
                    .size(width = 4.dp, height = it.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** An intercom button: 60 dp tall, filled with [container], with a hairline [border]. */
@Composable
private fun IntercomButton(
    container: Color,
    content: Color,
    border: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, border),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier.height(60.dp)
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) { body() }
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
    TandemMotoTheme(darkTheme = false) {
        RidePreview(
            RideUiState(),
            PermissionsState(34, mapOf(AppPermission.NEARBY to PermissionStatus.Denied))
        )
    }
}

@Preview(name = "Not paired · light", showBackground = true)
@Composable
private fun RideNotPairedPreview() {
    TandemMotoTheme(darkTheme = false) {
        RidePreview(RideUiState())
    }
}

@Preview(name = "Connected, paused · dark", showBackground = true)
@Composable
private fun RideConnectedPreview() {
    TandemMotoTheme(darkTheme = true) {
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
