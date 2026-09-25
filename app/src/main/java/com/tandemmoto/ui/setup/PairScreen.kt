package com.tandemmoto.ui.setup

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tandemmoto.R
import com.tandemmoto.link.DiscoveryState
import com.tandemmoto.link.NearbyDevice
import com.tandemmoto.link.PairingState
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.components.PermissionPrompt
import com.tandemmoto.ui.components.openWifiSettings
import com.tandemmoto.ui.components.rememberPermissionRequester
import com.tandemmoto.ui.theme.TandemMotoTheme

// Opened from Home's connection bar. Pairing succeeds → [onPaired] (back to Home).
@Composable
fun PairRoute(onBack: () -> Unit, onPaired: () -> Unit, viewModel: PairViewModel = viewModel()) {
    val state by viewModel.discovery.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()
    val permissions = rememberPermissionRequester()
    val context = LocalContext.current
    // Searching only while the screen is visible; coming back from settings starts a new search.
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenVisible()
        onPauseOrDispose { viewModel.onScreenHidden() }
    }
    LaunchedEffect(pairing) {
        if (pairing is PairingState.Paired) {
            viewModel.dismissPairing()
            onPaired()
        }
    }
    PairScreen(
        state = state,
        pairing = pairing,
        permissions = permissions.state,
        onRequestPermission = { permissions.request(AppPermission.NEARBY) },
        onSearchAgain = viewModel::searchAgain,
        onDeviceClick = viewModel::invite,
        onConfirmReplace = viewModel::confirmReplace,
        onCancelInvite = viewModel::cancelInvite,
        onDismissPairing = viewModel::dismissPairing,
        onOpenWifiSettings = context::openWifiSettings,
        onOpenLocationSettings = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        },
        onBack = onBack
    )
}

@Composable
fun PairScreen(
    state: DiscoveryState,
    pairing: PairingState,
    permissions: PermissionsState,
    onRequestPermission: () -> Unit,
    onSearchAgain: () -> Unit,
    onDeviceClick: (NearbyDevice) -> Unit,
    onConfirmReplace: () -> Unit,
    onCancelInvite: () -> Unit,
    onDismissPairing: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.pair_title), onBack) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.pair_instructions),
                style = MaterialTheme.typography.bodyLarge
            )
            if (pairing is PairingState.Inviting) {
                InvitingCard(pairing.device, onCancelInvite)
                return@Column
            }
            if (pairing is PairingState.Failed) {
                FailedCard(pairing.reason, onDismissPairing)
            }
            if (pairing is PairingState.ConfirmReplace) {
                ReplaceDialog(pairing, onConfirmReplace, onDismissPairing)
            }
            val onDevice = if (pairing == PairingState.Idle) onDeviceClick else null
            when (state) {
                DiscoveryState.Idle, is DiscoveryState.Scanning -> {
                    SearchingIndicator()
                    DeviceList((state as? DiscoveryState.Scanning)?.devices.orEmpty(), onDevice)
                }
                is DiscoveryState.Finished -> {
                    if (state.devices.isEmpty()) {
                        Message(stringResource(R.string.pair_none_found))
                    } else {
                        DeviceList(state.devices, onDevice)
                    }
                    ActionButton(stringResource(R.string.pair_search_again), onSearchAgain)
                    if (state.suggestLocation) {
                        Message(stringResource(R.string.pair_location_hint))
                        OutlinedButton(
                            onClick = onOpenLocationSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                        ) {
                            Text(stringResource(R.string.pair_open_location_settings))
                        }
                    }
                }
                DiscoveryState.WifiOff -> {
                    Message(stringResource(R.string.pair_wifi_off))
                    ActionButton(
                        stringResource(R.string.pair_open_wifi_settings),
                        onOpenWifiSettings
                    )
                }
                DiscoveryState.Stuck -> {
                    Message(stringResource(R.string.pair_stuck))
                    ActionButton(
                        stringResource(R.string.pair_open_wifi_settings),
                        onOpenWifiSettings
                    )
                    OutlinedButton(
                        onClick = onSearchAgain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    ) {
                        Text(stringResource(R.string.pair_search_again))
                    }
                }
                DiscoveryState.PermissionMissing -> PermissionPrompt(
                    permission = AppPermission.NEARBY,
                    state = permissions,
                    onRequest = onRequestPermission
                )
                DiscoveryState.Unsupported -> Message(stringResource(R.string.pair_unsupported))
            }
        }
    }
}

@Composable
private fun SearchingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(stringResource(R.string.pair_searching), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DeviceList(devices: List<NearbyDevice>, onDeviceClick: ((NearbyDevice) -> Unit)?) {
    val (phones, others) = devices.partition { it.isPhone }
    if (phones.isNotEmpty()) {
        DeviceSection(stringResource(R.string.pair_phones), phones, onDeviceClick)
    }
    if (others.isNotEmpty()) {
        DeviceSection(stringResource(R.string.pair_other_devices), others, onDeviceClick)
    }
}

@Composable
private fun DeviceSection(
    title: String,
    devices: List<NearbyDevice>,
    onDeviceClick: ((NearbyDevice) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        devices.forEach { device ->
            val clickLabel = stringResource(R.string.pair_device_action)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(
                        if (onDeviceClick != null) {
                            Modifier.clickable(onClickLabel = clickLabel) { onDeviceClick(device) }
                        } else {
                            Modifier
                        }
                    )
                    .semantics(mergeDescendants = true) {}
            ) {
                Text(
                    device.name.ifBlank { stringResource(R.string.pair_unnamed_device) },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(device.status.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun InvitingCard(device: NearbyDevice, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(
                    stringResource(
                        R.string.pair_inviting,
                        device.name.ifBlank { stringResource(R.string.pair_unnamed_device) }
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                stringResource(R.string.pair_inviting_hint),
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.pair_cancel))
            }
        }
    }
}

@Composable
private fun FailedCard(reason: PairingState.Failed.Reason, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Text(
                stringResource(
                    when (reason) {
                        PairingState.Failed.Reason.NoAnswer -> R.string.pair_failed_no_answer
                        PairingState.Failed.Reason.Busy -> R.string.pair_failed_busy
                        PairingState.Failed.Reason.Error -> R.string.pair_failed_error
                    }
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pair_ok)) }
        }
    }
}

@Composable
private fun ReplaceDialog(
    state: PairingState.ConfirmReplace,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pair_replace_title)) },
        text = {
            Text(stringResource(R.string.pair_replace_body, state.current.name, state.device.name))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.pair_replace_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pair_cancel)) }
        }
    )
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Text(text)
    }
}

private fun NearbyDevice.Status.label() = when (this) {
    NearbyDevice.Status.Available -> R.string.pair_status_available
    NearbyDevice.Status.Invited -> R.string.pair_status_invited
    NearbyDevice.Status.Connected -> R.string.pair_status_connected
    NearbyDevice.Status.Failed, NearbyDevice.Status.Unavailable -> R.string.pair_status_unavailable
}

private val previewPermissions =
    PermissionsState(34, mapOf(AppPermission.NEARBY to PermissionStatus.Granted))

@Preview(name = "Searching", showBackground = true)
@Composable
private fun PairSearchingPreview() {
    TandemMotoTheme(dynamicColor = false) {
        PairScreen(
            state = DiscoveryState.Scanning(
                listOf(
                    NearbyDevice("Galaxy S25", "a", NearbyDevice.Status.Available, isPhone = true),
                    NearbyDevice(
                        "Living room TV",
                        "b",
                        NearbyDevice.Status.Available,
                        isPhone = false
                    )
                )
            ),
            pairing = PairingState.Idle,
            permissions = previewPermissions,
            onRequestPermission = {},
            onSearchAgain = {},
            onDeviceClick = {},
            onConfirmReplace = {},
            onCancelInvite = {},
            onDismissPairing = {},
            onOpenWifiSettings = {},
            onOpenLocationSettings = {},
            onBack = {}
        )
    }
}

@Preview(name = "Nothing found · dark", showBackground = true)
@Composable
private fun PairNothingFoundPreview() {
    TandemMotoTheme(darkTheme = true, dynamicColor = false) {
        PairScreen(
            state = DiscoveryState.Finished(emptyList()),
            pairing = PairingState.Idle,
            permissions = previewPermissions,
            onRequestPermission = {},
            onSearchAgain = {},
            onDeviceClick = {},
            onConfirmReplace = {},
            onCancelInvite = {},
            onDismissPairing = {},
            onOpenWifiSettings = {},
            onOpenLocationSettings = {},
            onBack = {}
        )
    }
}
