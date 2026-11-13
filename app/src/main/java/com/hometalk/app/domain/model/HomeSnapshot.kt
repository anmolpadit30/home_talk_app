package com.hometalk.app.domain.model

data class HomeSnapshot(
    val settings: UserSettings,
    val wifiConnected: Boolean,
    val devices: List<Device>,
    val channel: ChannelState,
    val isLocallyTransmitting: Boolean,
    val microphonePermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val nearbyWifiPermissionGranted: Boolean,
) {
    val onlinePairedDevices: List<Device>
        get() = devices.filter { it.isOnline && it.isPaired }

    val talkButtonState: TalkButtonState
        get() = ResolveTalkButtonState(
            wifiConnected = wifiConnected,
            hasOnlinePairedPeer = onlinePairedDevices.isNotEmpty(),
            isTransmitting = isLocallyTransmitting,
            // Full duplex: never lock the Talk button because someone else is speaking.
            isChannelBusy = false,
            pttUiPreview = PhaseConfig.PTT_UI_PREVIEW,
        )
}
