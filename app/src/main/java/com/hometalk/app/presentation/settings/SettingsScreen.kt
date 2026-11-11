package com.hometalk.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hometalk.app.domain.model.PairedDeviceRecord
import com.hometalk.app.domain.model.UserSettings
import com.hometalk.app.presentation.components.SectionCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    pairedDevices: List<PairedDeviceRecord>,
    onDeviceNameChange: (String) -> Unit,
    onRoomNameChange: (String) -> Unit,
    onSpeakerVolumeChange: (Float) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onAddPeer: (String) -> Unit,
    onRemovePeer: (String) -> Unit,
    onUnpair: (String) -> Unit,
    onBackgroundServiceChange: (Boolean) -> Unit,
) {
    var deviceName by rememberSaveable(settings.deviceId) { mutableStateOf(settings.deviceName) }
    var roomName by rememberSaveable(settings.roomId) { mutableStateOf(settings.roomName) }
    var peerDraft by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "This phone") {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = {
                        deviceName = it
                        onDeviceNameChange(it)
                    },
                    label = { Text("Device name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = roomName,
                    onValueChange = {
                        roomName = it
                        onRoomNameChange(it)
                    },
                    label = { Text("Room name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (settings.deviceId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Device ID ${settings.deviceId.take(8)}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = "Speaker") {
                Text("Volume ${(settings.speakerVolume * 100).roundToInt()}%")
                Slider(
                    value = settings.speakerVolume,
                    onValueChange = onSpeakerVolumeChange,
                    valueRange = 0f..1f,
                )
            }

            SectionCard(title = "Notifications") {
                SettingSwitchRow(
                    title = "HomeTalk notification",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = onNotificationsChange,
                )
            }

            SectionCard(title = "Audio") {
                Text(
                    text = "Target bitrate for later Opus encoding.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(24, 28, 32).forEach { bitrate ->
                        FilterChip(
                            selected = settings.audioBitrateKbps == bitrate,
                            onClick = { onBitrateChange(bitrate) },
                            label = { Text("$bitrate kbps") },
                        )
                    }
                }
            }

            SectionCard(title = "Manual peer addresses") {
                Text(
                    text = "For homes with separate router subnets. Discovery will not assume multicast crosses routers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = peerDraft,
                    onValueChange = { peerDraft = it },
                    label = { Text("IP address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onAddPeer(peerDraft)
                        peerDraft = ""
                    },
                    enabled = peerDraft.isNotBlank(),
                ) {
                    Text("Add peer IP")
                }
                settings.manualPeerAddresses.forEach { address ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = address, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemovePeer(address) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove $address")
                        }
                    }
                }
            }

            SectionCard(title = "Paired devices") {
                if (pairedDevices.isEmpty()) {
                    Text(
                        text = "No paired phones yet. Open Family, show a pairing PIN on one phone, then tap Pair on the other.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    pairedDevices.forEach { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = device.hostAddress ?: device.id.take(8),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onUnpair(device.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove ${device.name}")
                            }
                        }
                    }
                }
            }

            SectionCard(title = "Background") {
                SettingSwitchRow(
                    title = "Keep listening in the background",
                    checked = settings.keepServiceInBackground,
                    onCheckedChange = onBackgroundServiceChange,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Shows a HomeTalk notification with Talk / Stop so you can listen with the screen locked or the app minimized.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
