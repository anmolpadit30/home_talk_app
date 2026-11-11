package com.hometalk.app.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hometalk.app.domain.model.ConnectionQuality
import com.hometalk.app.domain.model.Device
import java.text.DateFormat
import java.util.Date

@Composable
fun DeviceRow(
    device: Device,
    modifier: Modifier = Modifier,
    onPair: ((Device) -> Unit)? = null,
    onUnpair: ((Device) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.PhoneAndroid,
            contentDescription = null,
            tint = if (device.isOnline) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = statusLine(device),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!device.hostAddress.isNullOrBlank()) {
                Text(
                    text = "${device.hostAddress}:${device.controlPort ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            !device.isPaired && device.isOnline && onPair != null -> {
                TextButton(onClick = { onPair(device) }) {
                    Text("Pair")
                }
            }
            device.isPaired && onUnpair != null -> {
                TextButton(onClick = { onUnpair(device) }) {
                    Text("Remove")
                }
            }
        }
    }
}

private fun statusLine(device: Device): String {
    val presence = when {
        device.isOnline -> "Online"
        device.isPaired -> "Unreachable"
        else -> "Offline"
    }
    val pairing = if (device.isPaired) "Paired" else "Not paired"
    val quality = when (device.signalQuality) {
        ConnectionQuality.Unknown -> null
        ConnectionQuality.Poor -> "Poor"
        ConnectionQuality.Fair -> "Fair"
        ConnectionQuality.Good -> "Good"
        ConnectionQuality.Excellent -> "Excellent"
    }
    val lastSeen = device.lastSeenEpochMs?.let { epoch ->
        "Last seen ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epoch))}"
    }
    return listOfNotNull(presence, pairing, quality, lastSeen).joinToString(" · ")
}
