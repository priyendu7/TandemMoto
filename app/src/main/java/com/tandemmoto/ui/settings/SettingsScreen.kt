package com.tandemmoto.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tandemmoto.BuildConfig
import com.tandemmoto.R
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.transfer.WindowSettings
import com.tandemmoto.ui.components.AppCard
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.components.CardDivider
import com.tandemmoto.ui.components.IconTile
import com.tandemmoto.ui.components.SectionHeader
import com.tandemmoto.ui.theme.AppText
import com.tandemmoto.ui.theme.Radius
import com.tandemmoto.ui.theme.TandemMotoTheme
import com.tandemmoto.voice.MicTestResult
import com.tandemmoto.voice.MicTestState
import com.tandemmoto.voice.TalkTestState
import com.tandemmoto.voice.VoiceEffect
import kotlin.math.roundToInt

private const val SOURCE_URL = "https://github.com/priyendu7/TandemMoto"
private const val PRIVACY_URL = "$SOURCE_URL/blob/main/docs/PRIVACY.md"

// Links open in the browser: the app itself never talks to the internet.
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onExportLogs: () -> Unit,
    versionName: String = BuildConfig.VERSION_NAME,
    /** The paired partner's name, or null when not paired (the Forget row is hidden). */
    partnerName: String? = null,
    onForgetPartner: () -> Unit = {},
    /** Android 13+ notifications permission; null where it doesn't apply (row hidden). */
    notifications: PermissionStatus? = null,
    onNotificationsClick: () -> Unit = {},
    /** Shown when paired (#50); null hides the section. */
    partnerSongs: PartnerSongsUi? = null,
    onWindowChange: (WindowSettings) -> Unit = {},
    onRemovePartnerSongs: () -> Unit = {},
    /** Diagnostics → Mic test (#70); null hides the row. */
    micTest: MicTestState? = null,
    onMicTest: (delaySeconds: Int) -> Unit = {},
    onCancelMicTest: () -> Unit = {},
    /** Diagnostics → Talk test (#71); null hides the row (e.g. not linked). */
    talkTest: TalkTestState? = null,
    onTalkTest: (Boolean) -> Unit = {},
    /** Sound cues in the earbuds (#86). */
    soundCues: Boolean = true,
    onSoundCues: (Boolean) -> Unit = {},
    /** Advanced → Use earbud mic for the intercom (#72). */
    earbudMic: Boolean = false,
    onEarbudMic: (Boolean) -> Unit = {},
    /** The mic's level while the Talk test sends, in dBFS (#73); null hides the meter. */
    talkLevelDb: Double? = null,
    /** Advanced → Voice processing (#73), and the effects this phone has. */
    voiceProcessing: Boolean = true,
    onVoiceProcessing: (Boolean) -> Unit = {},
    voiceEffects: Set<VoiceEffect> = emptySet()
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    if (confirmForget && partnerName != null) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.settings_forget_title, partnerName)) },
            text = { Text(stringResource(R.string.settings_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    onForgetPartner()
                }) { Text(stringResource(R.string.settings_forget_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) {
                    Text(stringResource(R.string.pair_cancel))
                }
            }
        )
    }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Longer than a phone screen since Songs from partner (#50 phone test).
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            if (partnerName != null) {
                SectionHeader(stringResource(R.string.settings_section_partner))
                PartnerCard(partnerName, onForget = { confirmForget = true })
            }
            if (partnerSongs != null) {
                SectionHeader(stringResource(R.string.settings_partner_songs))
                PartnerSongsSection(partnerSongs, onWindowChange, onRemovePartnerSongs)
            }
            SectionHeader(stringResource(R.string.settings_section_alerts))
            AppCard {
                CardListItem(
                    headlineContent = { Text(stringResource(R.string.settings_sound_cues)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_sound_cues_summary))
                    },
                    trailingContent = { Switch(checked = soundCues, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = soundCues,
                        role = Role.Switch,
                        onValueChange = onSoundCues
                    )
                )
                if (notifications != null) {
                    CardDivider()
                    CardListItem(
                        headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (notifications == PermissionStatus.Granted) {
                                        R.string.settings_notifications_on
                                    } else {
                                        R.string.settings_notifications_off
                                    }
                                )
                            )
                        },
                        trailingContent = { Chevron() },
                        modifier = Modifier.clickable(onClick = onNotificationsClick)
                    )
                }
            }
            SectionHeader(stringResource(R.string.settings_section_troubleshooting))
            AppCard {
                CardListItem(
                    headlineContent = { Text(stringResource(R.string.settings_advanced)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_advanced_summary))
                    },
                    trailingContent = {
                        val arrow = if (advanced) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        }
                        Icon(arrow, contentDescription = null)
                    },
                    modifier = Modifier.clickable { advanced = !advanced }
                )
                if (advanced) {
                    CardListItem(
                        headlineContent = { Text(stringResource(R.string.settings_earbud_mic)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (earbudMic) {
                                        R.string.settings_earbud_mic_on
                                    } else {
                                        R.string.settings_earbud_mic_off
                                    }
                                )
                            )
                        },
                        trailingContent = { Switch(checked = earbudMic, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = earbudMic,
                            role = Role.Switch,
                            onValueChange = onEarbudMic
                        )
                    )
                    VoiceProcessingRow(voiceProcessing, onVoiceProcessing, voiceEffects)
                    if (micTest != null) MicTestRow(micTest, onMicTest, onCancelMicTest)
                    if (talkTest != null) {
                        CardListItem(
                            headlineContent = { Text(stringResource(R.string.settings_talk_test)) },
                            supportingContent = {
                                Column {
                                    Text(
                                        if (talkTest.on) {
                                            stringResource(
                                                R.string.settings_talk_test_on,
                                                talkTest.secondsLeft
                                            )
                                        } else {
                                            stringResource(R.string.settings_talk_test_summary)
                                        }
                                    )
                                    if (talkTest.on && talkLevelDb != null) LevelMeter(talkLevelDb)
                                }
                            },
                            trailingContent = {
                                Switch(checked = talkTest.on, onCheckedChange = null)
                            },
                            modifier = Modifier.toggleable(
                                value = talkTest.on,
                                role = Role.Switch,
                                onValueChange = onTalkTest
                            )
                        )
                    }
                }
                CardDivider()
                CardListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export_logs)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_export_logs_summary))
                    },
                    trailingContent = {
                        Icon(
                            painterResource(R.drawable.ic_share),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.clickable(onClick = onExportLogs)
                )
            }
            SectionHeader(stringResource(R.string.settings_section_about))
            AppCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.settings_version, versionName),
                        style = AppText.number.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        stringResource(R.string.settings_license),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CardDivider()
                CardListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                    supportingContent = { Text(stringResource(R.string.settings_privacy_summary)) },
                    trailingContent = { Chevron() },
                    modifier = Modifier.clickable { uriHandler.openUri(PRIVACY_URL) }
                )
                CardListItem(
                    headlineContent = { Text(stringResource(R.string.settings_source_code)) },
                    supportingContent = { Text(stringResource(R.string.settings_source_summary)) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.clickable { uriHandler.openUri(SOURCE_URL) }
                )
            }
        }
    }
}

/** The paired phone on its tile, with Forget. */
@Composable
private fun PartnerCard(name: String, onForget: () -> Unit) {
    val appColors = TandemMotoTheme.appColors
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            IconTile(
                ImageVector.vectorResource(R.drawable.ic_phone_android),
                appColors.connected,
                appColors.connectedTint
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.settings_paired_partner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DangerButton(stringResource(R.string.settings_forget_partner), onForget)
        }
    }
}

/** A small button in the problem colour, for actions that undo something (Forget). */
@Composable
internal fun DangerButton(text: String, onClick: () -> Unit) {
    val appColors = TandemMotoTheme.appColors
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.Medium),
        border = BorderStroke(1.dp, appColors.notConnected.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = appColors.notConnectedTint,
            contentColor = appColors.notConnected
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.height(40.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A row's "opens something" mark. */
@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A [ListItem] that shows the card behind it, for rows inside an [AppCard]. */
@Composable
internal fun CardListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    headlineColor: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = headlineColor
        ),
        modifier = modifier
    )
}

/** Compares the processed voice with the raw mic (#73); applies when the mic next opens. */
@Composable
private fun VoiceProcessingRow(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    effects: Set<VoiceEffect>
) {
    CardListItem(
        headlineContent = { Text(stringResource(R.string.settings_voice_processing)) },
        supportingContent = {
            val summary = stringResource(
                if (on) {
                    R.string.settings_voice_processing_on
                } else {
                    R.string.settings_voice_processing_off
                }
            )
            val has = if (effects.isEmpty()) {
                stringResource(R.string.settings_voice_processing_no_effects)
            } else {
                stringResource(
                    R.string.settings_voice_processing_effects,
                    effects.sorted().joinToString(", ") { it.label }
                )
            }
            Text("$summary\n$has")
        },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onChange)
    )
}

/** The mic's loudness while sending, to compare gain between phones (#73). */
@Composable
private fun LevelMeter(levelDb: Double) {
    Column(Modifier.padding(top = 4.dp)) {
        LinearProgressIndicator(
            progress = {
                ((levelDb - METER_FLOOR_DB) / -METER_FLOOR_DB).toFloat().coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.settings_talk_test_level, levelDb.roundToInt()))
    }
}

/** The meter's empty end: quieter than a quiet room. */
private const val METER_FLOOR_DB = -60.0

/**
 * Checks the mic works with the screen locked or the app in the background (#70): it waits, so
 * there's time to lock the screen or leave the app, then records 5 s and says what it heard. The
 * details go to the logs.
 */
@Composable
private fun MicTestRow(
    state: MicTestState,
    onStart: (delaySeconds: Int) -> Unit,
    onCancel: () -> Unit
) {
    CardListItem(
        headlineContent = { Text(stringResource(R.string.settings_mic_test)) },
        supportingContent = {
            Column {
                val result = state.result
                Text(
                    when {
                        state.startsIn != null ->
                            stringResource(R.string.settings_mic_test_starts_in, state.startsIn)
                        state.recording -> stringResource(R.string.settings_mic_test_recording)
                        result is MicTestResult.Heard -> stringResource(
                            R.string.settings_mic_test_heard,
                            result.levelsDb.max().roundToInt()
                        )
                        result == MicTestResult.Silent ->
                            stringResource(R.string.settings_mic_test_silent)
                        result == MicTestResult.CouldNotOpen ->
                            stringResource(R.string.settings_mic_test_could_not_open)
                        else -> stringResource(R.string.settings_mic_test_summary)
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.busy) {
                        OutlinedButton(onClick = onCancel) {
                            Text(stringResource(R.string.pair_cancel))
                        }
                    } else {
                        OutlinedButton(onClick = { onStart(SHORT_DELAY_S) }) {
                            Text(
                                stringResource(R.string.settings_mic_test_in_seconds, SHORT_DELAY_S)
                            )
                        }
                        OutlinedButton(onClick = { onStart(LONG_DELAY_S) }) {
                            Text(
                                stringResource(
                                    R.string.settings_mic_test_in_minutes,
                                    LONG_DELAY_S / 60
                                )
                            )
                        }
                    }
                }
            }
        }
    )
}

private const val SHORT_DELAY_S = 10

/** Long enough to lock both phones, or leave the app and make it reconnect. */
private const val LONG_DELAY_S = 5 * 60

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    TandemMotoTheme(darkTheme = true) {
        SettingsScreen(onBack = {}, onExportLogs = {}, versionName = "0.1.0")
    }
}
