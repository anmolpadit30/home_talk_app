package com.hometalk.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hometalk.app.domain.model.TalkButtonState
import com.hometalk.app.presentation.theme.BusyOrange
import com.hometalk.app.presentation.theme.DisconnectedGray
import com.hometalk.app.presentation.theme.GreenDark
import com.hometalk.app.presentation.theme.GreenLight
import com.hometalk.app.presentation.theme.GreenPrimary
import com.hometalk.app.presentation.theme.OnTalk

@Composable
fun TalkButton(
    state: TalkButtonState,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state == TalkButtonState.Idle || state == TalkButtonState.Transmitting
    val background by animateColorAsState(
        targetValue = when (state) {
            TalkButtonState.Idle -> GreenPrimary
            TalkButtonState.Transmitting -> GreenDark
            TalkButtonState.Busy -> BusyOrange
            TalkButtonState.Disconnected -> DisconnectedGray
        },
        animationSpec = tween(durationMillis = 180),
        label = "talkButtonColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (state == TalkButtonState.Transmitting) 1.06f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "talkButtonScale",
    )
    val label = when (state) {
        TalkButtonState.Idle -> "TALK"
        TalkButtonState.Transmitting -> "TRANSMITTING"
        TalkButtonState.Busy -> "CHANNEL BUSY"
        TalkButtonState.Disconnected -> "NOT CONNECTED"
    }
    val ringColor = if (state == TalkButtonState.Transmitting) GreenLight else Color.Transparent

    Box(
        modifier = modifier
            .size(220.dp)
            .scale(scale)
            .shadow(elevation = if (enabled) 10.dp else 2.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(background)
            .border(width = 6.dp, color = ringColor, shape = CircleShape)
            .semantics {
                this.contentDescription = label
                this.role = Role.Button
                if (!enabled) disabled()
            }
            // Key on Unit so a mid-press state change (Idle → Transmitting) does not
            // cancel the gesture and accidentally fire onRelease.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    waitForUpOrCancellation()
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = OnTalk,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
