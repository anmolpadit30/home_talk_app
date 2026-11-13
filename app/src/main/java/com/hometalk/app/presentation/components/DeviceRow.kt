package com.hometalk.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
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
    onTalkPress: ((Device) -> Unit)? = null,
    onTalkRelease: (() -> Unit)? = null,
    isTalkingToThisDevice: Boolean = false,
) {
    val canDirectTalk = device.isPaired && device.isOnline && onTalkPress != null && onTalkRelease != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isTalkingToThisDevice) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (canDirectTalk) {
                        Modifier.pointerInput(device.id) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                onTalkPress!!(device)
                                waitForUpOrCancellation()
                                onTalkRelease!!()
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = when {
                    isTalkingToThisDevice -> MaterialTheme.colorScheme.primary
                    device.isOnline -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(end = 16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = statusLine(device, isTalkingToThisDevice, canDirectTalk),
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

private fun statusLine(
    device: Device,
    isTalkingToThisDevice: Boolean,
    canDirectTalk: Boolean,
): String {
    if (isTalkingToThisDevice) return "Talking privately — release to stop"
    val presence = when {
        device.isOnline -> "Online"
        device.isPaired -> "Unreachable"
        else -> "Offline"
    }
    val pairing = if (device.isPaired) "Paired" else "Not paired"
    val holdHint = if (canDirectTalk) "Hold to talk" else null
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
    return listOfNotNull(presence, pairing, holdHint, quality, lastSeen).joinToString(" · ")
}
