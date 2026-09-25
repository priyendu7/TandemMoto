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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tandemmoto.R
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.ui.components.ConnectionStatus
import com.tandemmoto.ui.components.ConnectionStatusBar
import com.tandemmoto.ui.components.ControlButton
import com.tandemmoto.ui.components.PermissionPrompt
import com.tandemmoto.ui.components.rememberPermissionRequester
import com.tandemmoto.ui.theme.TandemMotoTheme

/** The Home screen: the app opens here, and every feature is reached from it. */
@Composable
fun RideRoute(
    onOpenPair: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: RideViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberPermissionRequester()
    RideScreen(
        state = state,
        permissions = permissions.state,
        onRequestPermission = permissions.request,
        onOpenPair = onOpenPair,
        onConnect = viewModel::onConnect,
        onPlayPause = viewModel::onPlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onOpenPlaylist = onOpenPlaylist,
        onOpenSettings = onOpenSettings
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
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenSettings: () -> Unit
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
                permissions,
                onRequestPermission,
                onOpenPair,
                onConnect
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                NowPlayingCard(state.nowPlaying)
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
    permissions: PermissionsState,
    onRequestPermission: (AppPermission) -> Unit,
    onOpenPair: () -> Unit,
    onConnect: () -> Unit
) {
    if (permissions.isGranted(AppPermission.NEARBY)) {
        ConnectionStatusBar(
            status = connection,
            onClickLabel = stringResource(
                if (connection == ConnectionStatus.NotConnected) {
                    R.string.ride_connect_action
                } else {
                    R.string.ride_pair_action
                }
            ),
            onClick = when (connection) {
                ConnectionStatus.NotPaired, ConnectionStatus.PairedElsewhere -> onOpenPair
                ConnectionStatus.NotConnected -> onConnect
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
private fun NowPlayingCard(nowPlaying: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.ride_now_playing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = nowPlaying ?: stringResource(R.string.ride_no_song),
                style = MaterialTheme.typography.headlineMedium
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
            RideUiState(connection = ConnectionStatus.Connected, nowPlaying = "Highway Song")
        )
    }
}
