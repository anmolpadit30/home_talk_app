package com.hometalk.app.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hometalk.app.domain.model.TalkButtonState
import com.hometalk.app.presentation.components.PermissionBanner
import com.hometalk.app.presentation.components.SectionCard
import com.hometalk.app.presentation.components.StatusChip
import com.hometalk.app.presentation.components.TalkButton
import com.hometalk.app.presentation.permissions.AppPermissions
import com.hometalk.app.presentation.permissions.permissionSnapshot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onTalkPressed: () -> Unit,
    onTalkReleased: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onPermissionsResult: (com.hometalk.app.presentation.permissions.PermissionSnapshot) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onPermissionsResult(context.permissionSnapshot())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onPermissionsResult(context.permissionSnapshot())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HomeTalk") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!state.microphonePermissionGranted) {
                PermissionBanner(
                    title = "Microphone access needed",
                    body = "HomeTalk needs the microphone so you can talk to family members. Permission is requested while the app is open.",
                    actionLabel = "Allow microphone",
                    onAction = {
                        permissionLauncher.launch(AppPermissions.requiredRuntimePermissions())
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else if (!state.notificationPermissionGranted) {
                PermissionBanner(
                    title = "Notifications needed",
                    body = "Allow notifications so HomeTalk can keep listening in the background and show Talk / Stop controls.",
                    actionLabel = "Allow notifications",
                    onAction = {
                        permissionLauncher.launch(AppPermissions.requiredRuntimePermissions())
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(
                    label = if (state.wifiConnected) "Wi-Fi connected" else "Wi-Fi off",
                    icon = if (state.wifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    active = state.wifiConnected,
                )
                StatusChip(
                    label = if (state.wifiConnected) "Home network ready" else "No home network",
                    icon = if (state.wifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    active = state.wifiConnected,
                )
                StatusChip(
                    label = "${state.connectedDeviceCount} connected",
                    icon = Icons.Filled.People,
                    active = state.connectedDeviceCount > 0,
                )
                StatusChip(
                    label = if (state.speakerEnabled) "Speaker on" else "Speaker off",
                    icon = if (state.speakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    active = state.speakerEnabled,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SectionCard(title = state.roomName.ifBlank { "Family" }) {
                Text(
                    text = if (state.deviceName.isBlank()) "This phone" else state.deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transmitterLabel(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.connectedDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.connectedDevices.joinToString { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            TalkButton(
                state = state.talkButtonState,
                onPress = onTalkPressed,
                onRelease = onTalkReleased,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(onClick = onToggleSpeaker) {
                Text(if (state.speakerEnabled) "Mute speaker" else "Unmute speaker")
            }
        }
    }
}

private fun transmitterLabel(state: HomeUiState): String = when {
    state.talkButtonState == TalkButtonState.Transmitting -> "You are transmitting"
    state.currentTransmitterName != null -> "${state.currentTransmitterName} is talking"
    else -> "No one is talking"
}
