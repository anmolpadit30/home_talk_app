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
            // Only another phone holding the channel is "busy".
            isChannelBusy = channel.isBusy &&
                channel.currentTransmitterId != null &&
                channel.currentTransmitterId != settings.deviceId,
            pttUiPreview = PhaseConfig.PTT_UI_PREVIEW,
        )
}
