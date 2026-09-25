package com.tandemmoto.spike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tandemmoto.ui.components.BackTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

/** Spike-only (#22) screen. Copy is hard-coded on purpose: this branch is never merged. */
@Composable
fun WifiDirectLabRoute(onBack: () -> Unit, lab: WifiDirectLab = viewModel()) {
    val state by lab.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(lab) {
        lab.refreshEnvironment()
        onPauseOrDispose { }
    }
    Scaffold(topBar = { BackTopBar("Wi-Fi Direct lab", onBack) }) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item { StatusCard(state) }
            item { Controls(state, lab) }
            item { ConnectionCard(state) }
            item { Text("Peers", style = MaterialTheme.typography.titleMedium) }
            if (state.peers.isEmpty()) {
                item { Text("None yet. Tap Discover on both phones.") }
            }
            items(state.peers, key = { it.address }) { peer ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(peer.name, style = MaterialTheme.typography.bodyLarge)
                        val seen = state.peerFirstSeenMs[peer.address]?.let {
                            " · seen after $it ms"
                        }
                        Text(
                            peer.status + seen.orEmpty(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = { lab.connect(peer) },
                        enabled = !state.groupFormed && !state.connecting
                    ) { Text("Connect") }
                }
            }
            item {
                HorizontalDivider()
                Text("Events (newest first)", style = MaterialTheme.typography.titleMedium)
            }
            items(state.events) { event ->
                Text(
                    "${TIME.format(Instant.ofEpochMilli(event.atMillis))}  ${event.text}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: LabState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Step 0: is Wi-Fi Direct usable?", style = MaterialTheme.typography.titleMedium)
            Line("Feature declared", state.supported.yesNo())
            Line("Wi-Fi Direct enabled", state.p2pEnabled?.yesNo() ?: "waiting…")
            Line("Location switched on", state.locationOn.yesNo())
            Line("Connected to a Wi-Fi network", state.wifiNetworkConnected.yesNo())
            Line("Nearby/location permission", state.nearbyGranted.yesNo())
            Line("This device", state.thisDevice ?: "—")
        }
    }
}

@Composable
private fun Controls(state: LabState, lab: WifiDirectLab) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = lab::discover) { Text("Discover") }
            OutlinedButton(onClick = lab::stopDiscovery) { Text("Stop") }
            OutlinedButton(onClick = lab::disconnect) { Text("Disconnect") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Group owner intent:")
            listOf(0, 15).forEach { intent ->
                FilterChip(
                    selected = state.goIntent == intent,
                    onClick = { lab.setGoIntent(intent) },
                    label = { Text("$intent") }
                )
            }
        }
        Line("Discovering", state.discovering.yesNo())
        HorizontalDivider()
        Text("Lab v2 experiments", style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ping rate:")
            listOf(1, 20).forEach { rate ->
                FilterChip(
                    selected = state.pingRate == rate,
                    onClick = { lab.setPingRate(rate) },
                    label = { Text("$rate/s") }
                )
            }
            OutlinedButton(onClick = lab::resetStats) { Text("Reset stats") }
        }
        Toggle("Low-latency Wi-Fi lock", state.wifiLockOn, lab::setWifiLock)
        Toggle("Foreground service", state.foregroundServiceOn, lab::setForegroundService)
    }
}

@Composable
private fun ConnectionCard(state: LabState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Line("Discover → first peer", state.discoveryToFirstPeerMs.ms())
            Line("Connect → group formed", state.connectToGroupMs.ms())
            Line("Group → socket", state.groupToSocketMs.ms())
            Line(
                "Role",
                when (state.isGroupOwner) {
                    true -> "GROUP OWNER"
                    false -> "client"
                    null -> "—"
                }
            )
            Line("Owner address", state.groupOwnerAddress ?: "—")
            Line("Socket", state.socketState)
            val ping = state.ping
            Line(
                "Pings sent / received / missed",
                "${ping.sent} / ${ping.received} / ${ping.missed}"
            )
            Line(
                "RTT last / median / p95",
                "${ping.lastMs.ms()} / ${ping.medianMs.ms()} / ${ping.p95Ms.ms()}"
            )
            Line("Longest gap between pongs", ping.maxGapMs.ms())
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

private fun Boolean.yesNo() = if (this) "yes" else "NO"

private fun Long?.ms() = this?.let { "$it ms" } ?: "—"

private fun Double?.ms() = this?.let { "%.1f ms".format(it) } ?: "—"
