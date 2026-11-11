package com.hometalk.app.presentation.home

import com.hometalk.app.domain.model.Device
import com.hometalk.app.domain.model.TalkButtonState
import com.hometalk.app.domain.model.UserSettings

data class HomeUiState(
    val isLoading: Boolean = true,
    val deviceName: String = "",
    val roomName: String = UserSettings.DEFAULT_ROOM_NAME,
    val wifiConnected: Boolean = false,
    val connectedDeviceCount: Int = 0,
    val connectedDevices: List<Device> = emptyList(),
    val currentTransmitterName: String? = null,
    val talkButtonState: TalkButtonState = TalkButtonState.Disconnected,
    val speakerEnabled: Boolean = true,
    val microphonePermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
    val nearbyWifiPermissionGranted: Boolean = true,
    val statusMessage: String = "",
)
