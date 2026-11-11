package com.hometalk.app.domain

import com.hometalk.app.domain.model.ResolveTalkButtonState
import com.hometalk.app.domain.model.TalkButtonState
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveTalkButtonStateTest {

    @Test
    fun disconnectedWhenWifiIsOff() {
        val state = ResolveTalkButtonState(
            wifiConnected = false,
            hasOnlinePairedPeer = true,
            isTransmitting = false,
            isChannelBusy = false,
        )
        assertEquals(TalkButtonState.Disconnected, state)
    }

    @Test
    fun disconnectedWhenNoPeersAndPreviewDisabled() {
        val state = ResolveTalkButtonState(
            wifiConnected = true,
            hasOnlinePairedPeer = false,
            isTransmitting = false,
            isChannelBusy = false,
            pttUiPreview = false,
        )
        assertEquals(TalkButtonState.Disconnected, state)
    }

    @Test
    fun idleWhenWifiConnectedAndPeerOnline() {
        val state = ResolveTalkButtonState(
            wifiConnected = true,
            hasOnlinePairedPeer = true,
            isTransmitting = false,
            isChannelBusy = false,
        )
        assertEquals(TalkButtonState.Idle, state)
    }

    @Test
    fun transmittingTakesPriorityOverBusy() {
        val state = ResolveTalkButtonState(
            wifiConnected = true,
            hasOnlinePairedPeer = true,
            isTransmitting = true,
            isChannelBusy = true,
        )
        assertEquals(TalkButtonState.Transmitting, state)
    }

    @Test
    fun busyWhenAnotherTransmitterHoldsTheChannel() {
        val state = ResolveTalkButtonState(
            wifiConnected = true,
            hasOnlinePairedPeer = true,
            isTransmitting = false,
            isChannelBusy = true,
        )
        assertEquals(TalkButtonState.Busy, state)
    }

    @Test
    fun previewAllowsIdleWithoutPeers() {
        val state = ResolveTalkButtonState(
            wifiConnected = true,
            hasOnlinePairedPeer = false,
            isTransmitting = false,
            isChannelBusy = false,
            pttUiPreview = true,
        )
        assertEquals(TalkButtonState.Idle, state)
    }
}
